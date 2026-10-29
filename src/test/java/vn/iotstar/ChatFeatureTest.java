package vn.iotstar;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.iotstar.dto.ChatDto;
import vn.iotstar.entity.*;
import vn.iotstar.enums.ChatSessionStatus;
import vn.iotstar.exception.BadRequestException;
import vn.iotstar.repository.*;
import vn.iotstar.service.ChatService;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class ChatFeatureTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired BranchRepository branches;
    @Autowired ChatSessionRepository sessions;
    @Autowired ChatMessageRepository messages;
    @Autowired ChatService chat;
    @Autowired AuthorizationManager<Message<?>> messageAuthorizationManager;
    User customer, stranger, staff, colleague, otherStaff, admin;
    Branch a, b;
    Long sessionId;

    @BeforeEach void setup() {
        a = branches.save(Branch.builder().branchName("Chat A").address("A").district("A").phone("100").build());
        b = branches.save(Branch.builder().branchName("Chat B").address("B").district("B").phone("101").build());
        var cr = roles.save(Role.builder().roleName("CUSTOMER").build());
        var sr = roles.save(Role.builder().roleName("STAFF").build());
        var ar = roles.save(Role.builder().roleName("ADMIN").build());
        customer = createUser("chat_customer", cr, null, "0910000001");
        stranger = createUser("chat_stranger", cr, null, "0910000002");
        staff = createUser("chat_staff", sr, a, "0910000003");
        colleague = createUser("chat_colleague", sr, a, "0910000004");
        otherStaff = createUser("chat_other_staff", sr, b, "0910000005");
        admin = createUser("chat_admin", ar, null, "0910000006");
        sessionId = chat.openSession(customer.getUsername(), a.getBranchId()).sessionId();
    }
    User createUser(String name, Role role, Branch branch, String phone) {
        return users.save(User.builder().username(name).email(name+"@test.com").password("test-password")
                .fullName(name).role(role).branch(branch).phone(phone).isVerified(true).build());
    }
    ChatDto.MessageRequest request(String id, String content) {
        return ChatDto.MessageRequest.builder().sessionId(sessionId).clientMessageId(id).content(content).build();
    }
    @Test void createsRealSessionAndReusesOpenSession() {
        assertEquals(sessionId, chat.openSession(customer.getUsername(), a.getBranchId()).sessionId());
        assertNotEquals(sessionId, chat.openSession(customer.getUsername(), b.getBranchId()).sessionId());
        assertThrows(AccessDeniedException.class, () -> chat.openSession(staff.getUsername(), a.getBranchId()));
    }
    @Test void storesRealSenderAndRejectsIdSpoofing() throws Exception {
        mvc.perform(post("/chat/api/messages").with(user(customer)).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(Map.of("sessionId",sessionId,"clientMessageId","spoof", "content","Xin chào", "senderId",admin.getUserId(),"branchId",b.getBranchId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("senderId").value(customer.getUserId()))
                .andExpect(jsonPath("senderRole").value("CUSTOMER")).andExpect(jsonPath("password").doesNotExist())
                .andExpect(jsonPath("sender").doesNotExist());
        assertEquals(1,messages.count());
    }
    @Test void customersCannotReadSendCloseOrMarkAnotherCustomersSession() throws Exception {
        for (String path : List.of("/sessions/"+sessionId,"/sessions/"+sessionId+"/messages"))
            mvc.perform(get("/chat/api"+path).with(user(stranger))).andExpect(status().isForbidden());
        assertThrows(AccessDeniedException.class, () -> chat.sendMessage(stranger.getUsername(), request("x","leak")));
        assertThrows(AccessDeniedException.class, () -> chat.closeSession(stranger.getUsername(), sessionId));
        assertThrows(AccessDeniedException.class, () -> chat.markRead(stranger.getUsername(), sessionId,0L));
    }
    @Test void staffCannotCrossBranchEvenWithForgedParameters() throws Exception {
        assertThrows(AccessDeniedException.class, () -> chat.getMessages(otherStaff.getUsername(),sessionId,null));
        assertThrows(AccessDeniedException.class, () -> chat.sendMessage(otherStaff.getUsername(),request("x","leak")));
        assertThrows(AccessDeniedException.class, () -> chat.claimSession(otherStaff.getUsername(),sessionId));
        assertThrows(AccessDeniedException.class, () -> chat.closeSession(otherStaff.getUsername(),sessionId));
        mvc.perform(get("/chat/api/sessions").with(user(staff)).param("branchId",b.getBranchId().toString()))
                .andExpect(status().isForbidden());
    }
    @Test void returnsOnlyOwnSessionsAndAssignedBranchInbox() {
        chat.openSession(stranger.getUsername(),b.getBranchId());
        assertEquals(1,chat.listSessions(customer.getUsername(),null,0).sessions().size());
        assertEquals(1,chat.listSessions(staff.getUsername(),null,0).sessions().size());
        assertEquals(1,chat.listSessions(admin.getUsername(),b.getBranchId(),0).sessions().size());
    }
    @Test void claimPreventsAnotherStaffAnsweringAndAdminCanTakeOver() {
        chat.claimSession(staff.getUsername(),sessionId);
        assertThrows(BadRequestException.class, () -> chat.claimSession(colleague.getUsername(),sessionId));
        assertThrows(BadRequestException.class, () -> chat.sendMessage(colleague.getUsername(),request("colleague","Hi")));
        assertEquals(admin.getUserId(),chat.claimSession(admin.getUsername(),sessionId).staffId());
    }
    @Test void transferredOrDeactivatedStaffLosesAccessAndColleagueCanReclaim() {
        chat.claimSession(staff.getUsername(),sessionId); staff.setBranch(b); users.flush();
        assertThrows(AccessDeniedException.class, () -> chat.getSession(staff.getUsername(),sessionId));
        assertEquals(colleague.getUserId(),chat.claimSession(colleague.getUsername(),sessionId).staffId());
        colleague.setIsActive(false);
        assertThrows(AccessDeniedException.class, () -> chat.sendMessage(colleague.getUsername(),request("off","hi")));
    }
    @Test void idempotentRetryCreatesOnlyOneMessageAndRejectsDifferentContent() {
        var first = chat.sendMessage(customer.getUsername(),request("repeat","Xin chào"));
        var retry = chat.sendMessage(customer.getUsername(),request("repeat","Xin chào"));
        assertEquals(first.getMessageId(),retry.getMessageId()); assertEquals(1,messages.count());
        assertThrows(BadRequestException.class, () -> chat.sendMessage(customer.getUsername(),request("repeat","Other")));
    }
    @Test void readMarkersAndUnreadCountsNeverMoveBackwards() {
        var message = chat.sendMessage(customer.getUsername(),request("one","Cần tư vấn"));
        assertEquals(1,chat.getSession(staff.getUsername(),sessionId).unreadCount());
        chat.markRead(staff.getUsername(),sessionId,message.getMessageId());
        chat.markRead(staff.getUsername(),sessionId,0L);
        assertEquals(0,chat.getSession(staff.getUsername(),sessionId).unreadCount());
        var reply = chat.sendMessage(staff.getUsername(),request("two","Em hỗ trợ anh/chị"));
        assertEquals(1,chat.getSession(customer.getUsername(),sessionId).unreadCount());
        chat.markRead(customer.getUsername(),sessionId,reply.getMessageId());
        assertEquals(0,chat.getSession(customer.getUsername(),sessionId).unreadCount());
        assertThrows(BadRequestException.class, () -> chat.markRead(customer.getUsername(),sessionId,99999L));
    }
    @Test void closePreservesHistoryAndCreatesNewConversation() {
        chat.sendMessage(customer.getUsername(),request("one","Lịch sử"));
        chat.closeSession(customer.getUsername(),sessionId);
        assertEquals(ChatSessionStatus.CLOSED,sessions.findById(sessionId).orElseThrow().getStatus());
        assertThrows(BadRequestException.class, () -> chat.sendMessage(customer.getUsername(),request("two","Late")));
        assertEquals(1,chat.getMessages(customer.getUsername(),sessionId,null).messages().size());
        assertNotEquals(sessionId,chat.openSession(customer.getUsername(),a.getBranchId()).sessionId());
    }
    @Test void historyPaginatesInStableAscendingOrderAndReturnsNoSecrets() throws Exception {
        for (int i=0;i<55;i++) chat.sendMessage(customer.getUsername(),request("msg-"+i,"Nội dung "+i));
        var latest = chat.getMessages(customer.getUsername(),sessionId,null);
        assertEquals(50,latest.messages().size()); assertTrue(latest.hasMore());
        var older = chat.getMessages(customer.getUsername(),sessionId,latest.nextBeforeId());
        assertEquals(5,older.messages().size()); assertFalse(older.hasMore());
        mvc.perform(get("/chat/api/sessions/"+sessionId+"/messages").with(user(customer)))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString("test-password"))));
    }
    @Test void recoversMoreThanOnePageOfMissedMessagesWithAfterCursor() {
        var first=chat.sendMessage(customer.getUsername(),request("anchor","First"));
        for (int i=0;i<55;i++) chat.sendMessage(customer.getUsername(),request("missed-"+i,"Missed "+i));
        var batch=chat.getMessages(customer.getUsername(),sessionId,null,first.getMessageId());
        assertEquals(50,batch.messages().size()); assertTrue(batch.hasMore());
        var rest=chat.getMessages(customer.getUsername(),sessionId,null,batch.nextAfterId());
        assertEquals(5,rest.messages().size()); assertFalse(rest.hasMore());
        assertEquals(56,messages.count());
    }
    @Test void continuousConversationIncludesClosedSessionsAndIsolatesCustomerAndBranch() throws Exception {
        Long oldSession = sessionId;
        var first = chat.sendMessage(customer.getUsername(), request("old-thread", "Tư vấn trước đây"));
        chat.closeSession(customer.getUsername(), oldSession);
        sessionId = chat.openSession(customer.getUsername(), a.getBranchId()).sessionId();
        var second = chat.sendMessage(customer.getUsername(), request("new-thread", "Tư vấn hôm nay"));
        Long strangerSession = chat.openSession(stranger.getUsername(), a.getBranchId()).sessionId();
        chat.sendMessage(stranger.getUsername(), ChatDto.MessageRequest.builder().sessionId(strangerSession)
                .clientMessageId("private-stranger").content("Tin riêng khách khác").build());
        Long branchBSession = chat.openSession(customer.getUsername(), b.getBranchId()).sessionId();
        chat.sendMessage(customer.getUsername(), ChatDto.MessageRequest.builder().sessionId(branchBSession)
                .clientMessageId("private-branch-b").content("Tin riêng nhánh B").build());
        var timeline = chat.getConversationMessages(customer.getUsername(), sessionId, null, null);
        assertEquals(List.of(first.getMessageId(), second.getMessageId()), timeline.messages().stream().map(ChatDto.MessageResponse::getMessageId).toList());
        assertEquals(List.of(oldSession, sessionId), timeline.messages().stream().map(ChatDto.MessageResponse::getSessionId).toList());
        assertTrue(timeline.messages().stream().allMatch(m -> m.getSentAt() != null));
        assertEquals(2, chat.getConversationMessages(staff.getUsername(), oldSession, null, null).messages().size());
        assertEquals(2, chat.getConversationMessages(admin.getUsername(), sessionId, null, null).messages().size());
        mvc.perform(get("/chat/api/sessions/" + sessionId + "/conversation/messages").with(user(customer))
                        .param("customerId", stranger.getUserId().toString()).param("branchId", b.getBranchId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.messages", hasSize(2)))
                .andExpect(jsonPath("$.messages[0].content").value("Tư vấn trước đây"))
                .andExpect(jsonPath("$.messages[1].sentAt").isNotEmpty())
                .andExpect(content().string(not(containsString("test-password"))));
    }
    @Test void continuousConversationRejectsOtherCustomersAndBranches() throws Exception {
        for (User forbidden : List.of(stranger, otherStaff)) {
            mvc.perform(get("/chat/api/sessions/" + sessionId + "/conversation/messages").with(user(forbidden)))
                    .andExpect(status().isForbidden());
            assertThrows(AccessDeniedException.class, () -> chat.getConversationMessages(forbidden.getUsername(), sessionId, null, null));
        }
        mvc.perform(get("/chat/api/sessions/" + sessionId + "/conversation/messages")).andExpect(status().isUnauthorized());
        assertThrows(BadRequestException.class, () -> chat.getConversationMessages(customer.getUsername(), sessionId, 0L, null));
        assertThrows(BadRequestException.class, () -> chat.getConversationMessages(customer.getUsername(), sessionId, null, -1L));
        assertThrows(BadRequestException.class, () -> chat.getConversationMessages(customer.getUsername(), sessionId, 1L, 1L));
    }
    @Test void continuousConversationPaginatesAndCatchesUpAcrossSessionBoundaries() {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 30; i++) ids.add(chat.sendMessage(customer.getUsername(), request("first-" + i, "Cũ " + i)).getMessageId());
        chat.closeSession(customer.getUsername(), sessionId);
        sessionId = chat.openSession(customer.getUsername(), a.getBranchId()).sessionId();
        for (int i = 0; i < 30; i++) ids.add(chat.sendMessage(customer.getUsername(), request("second-" + i, "Mới " + i)).getMessageId());
        var latest = chat.getConversationMessages(customer.getUsername(), sessionId, null, null);
        assertEquals(ids.subList(10, 60), latest.messages().stream().map(ChatDto.MessageResponse::getMessageId).toList());
        assertTrue(latest.hasMore());
        var older = chat.getConversationMessages(customer.getUsername(), sessionId, latest.nextBeforeId(), null);
        assertEquals(ids.subList(0, 10), older.messages().stream().map(ChatDto.MessageResponse::getMessageId).toList());
        assertFalse(older.hasMore());
        var missed = chat.getConversationMessages(customer.getUsername(), sessionId, null, ids.get(0));
        assertEquals(50, missed.messages().size()); assertTrue(missed.hasMore());
        var remainder = chat.getConversationMessages(customer.getUsername(), sessionId, null, missed.nextAfterId());
        assertEquals(ids.subList(51, 60), remainder.messages().stream().map(ChatDto.MessageResponse::getMessageId).toList());
        assertFalse(remainder.hasMore());
    }
    @Test void staffInboxListsOneContactPerCustomerAndUsesTheOpenSession() throws Exception {
        assertTrue(chat.listConversations(staff.getUsername(), null, 0).conversations().isEmpty());
        Long oldSession = sessionId;
        chat.sendMessage(customer.getUsername(), request("old-contact", "Tư vấn cũ"));
        chat.closeSession(customer.getUsername(), oldSession);
        sessionId = chat.openSession(customer.getUsername(), a.getBranchId()).sessionId();
        chat.sendMessage(customer.getUsername(), request("new-contact", "Tư vấn mới"));
        Long otherCustomerSession = chat.openSession(stranger.getUsername(), a.getBranchId()).sessionId();
        chat.sendMessage(stranger.getUsername(), ChatDto.MessageRequest.builder().sessionId(otherCustomerSession)
                .clientMessageId("stranger-contact").content("Khách thứ hai").build());
        User silent = createUser("chat_silent", customer.getRole(), null, "0910000007");
        chat.openSession(silent.getUsername(), a.getBranchId());
        var page = chat.listConversations(staff.getUsername(), null, 0);
        assertEquals(2, page.conversations().size()); assertFalse(page.hasMore());
        var contact = page.conversations().stream().filter(c -> c.customerId().equals(customer.getUserId())).findFirst().orElseThrow();
        assertEquals(sessionId, contact.sessionId()); assertEquals("OPEN", contact.status()); assertEquals(2, contact.unreadCount());
        assertEquals(sessionId, chat.getConversation(staff.getUsername(), oldSession).sessionId());
        mvc.perform(get("/chat/api/conversations").with(user(staff)).param("branchId", a.getBranchId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.conversations", hasSize(2)))
                .andExpect(content().string(not(containsString("test-password"))));
    }
    @Test void staffContactUnreadCountsSpanSessionsAndClosedContactSwitchesToNewOpenSession() {
        Long oldSession = sessionId;
        var first = chat.sendMessage(customer.getUsername(), request("unread-old", "Tin cũ"));
        chat.closeSession(customer.getUsername(), oldSession);
        sessionId = chat.openSession(customer.getUsername(), a.getBranchId()).sessionId();
        var second = chat.sendMessage(customer.getUsername(), request("unread-new", "Tin mới"));
        assertEquals(2, chat.getConversation(staff.getUsername(), oldSession).unreadCount());
        chat.markRead(staff.getUsername(), oldSession, first.getMessageId());
        assertEquals(1, chat.getConversation(staff.getUsername(), oldSession).unreadCount());
        chat.markRead(staff.getUsername(), sessionId, second.getMessageId());
        assertEquals(0, chat.getConversation(staff.getUsername(), oldSession).unreadCount());
        chat.closeSession(customer.getUsername(), sessionId);
        assertEquals(sessionId, chat.getConversation(staff.getUsername(), oldSession).sessionId());
        assertEquals("CLOSED", chat.getConversation(staff.getUsername(), oldSession).status());
        Long reopened = chat.openSession(customer.getUsername(), a.getBranchId()).sessionId();
        var contact = chat.listConversations(staff.getUsername(), null, 0).conversations().get(0);
        assertEquals(reopened, contact.sessionId()); assertEquals("OPEN", contact.status());
        assertEquals(reopened, chat.getConversation(staff.getUsername(), oldSession).sessionId());
    }
    @Test void staffInboxPaginatesCustomersRatherThanIndividualSessions() {
        for (int i = 0; i < 31; i++) {
            User person = createUser("contact_page_" + i, customer.getRole(), null, "09200" + String.format("%05d", i));
            Long oldSession = chat.openSession(person.getUsername(), a.getBranchId()).sessionId();
            chat.sendMessage(person.getUsername(), ChatDto.MessageRequest.builder().sessionId(oldSession)
                    .clientMessageId("old").content("Cũ " + i).build());
            chat.closeSession(person.getUsername(), oldSession);
            Long newSession = chat.openSession(person.getUsername(), a.getBranchId()).sessionId();
            chat.sendMessage(person.getUsername(), ChatDto.MessageRequest.builder().sessionId(newSession)
                    .clientMessageId("new").content("Mới " + i).build());
        }
        var first = chat.listConversations(staff.getUsername(), null, 0);
        var second = chat.listConversations(staff.getUsername(), null, 1);
        assertEquals(30, first.conversations().size()); assertTrue(first.hasMore());
        assertEquals(1, second.conversations().size()); assertFalse(second.hasMore());
        Set<Long> customerIds = new HashSet<>();
        for (var contact : first.conversations()) {
            assertTrue(customerIds.add(contact.customerId())); assertEquals("OPEN", contact.status()); assertEquals(2, contact.unreadCount());
        }
        assertTrue(customerIds.add(second.conversations().get(0).customerId())); assertEquals(31, customerIds.size());
    }
    @Test void staffContactEndpointsEnforceCurrentRoleAndBranch() throws Exception {
        chat.sendMessage(customer.getUsername(), request("private-contact", "Riêng nhánh A"));
        for (User forbidden : List.of(customer, otherStaff)) {
            mvc.perform(get("/chat/api/conversations").with(user(forbidden)).param("branchId", a.getBranchId().toString()))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/chat/api/sessions/" + sessionId + "/conversation").with(user(forbidden)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/chat/api/conversations")).andExpect(status().isUnauthorized());
        assertThrows(BadRequestException.class, () -> chat.listConversations(staff.getUsername(), null, -1));
        assertThrows(BadRequestException.class, () -> chat.listConversations(admin.getUsername(), null, 0));
        assertEquals(1, chat.listConversations(admin.getUsername(), a.getBranchId(), 0).conversations().size());
        assertTrue(chat.listConversations(otherStaff.getUsername(), null, 0).conversations().isEmpty());
        staff.setBranch(b); users.flush();
        assertThrows(AccessDeniedException.class, () -> chat.getConversation(staff.getUsername(), sessionId));
        staff.setBranch(a); staff.setIsActive(false); users.flush();
        assertThrows(AccessDeniedException.class, () -> chat.listConversations(staff.getUsername(), null, 0));
    }
    @Test void validatesContentClientIdAndCsrf() throws Exception {
        for (String invalid : List.of(" ","x".repeat(2001)))
            mvc.perform(post("/chat/api/messages").with(user(customer)).with(csrf()).contentType("application/json")
                    .content(json.writeValueAsString(request("invalid",invalid)))).andExpect(status().isBadRequest());
        mvc.perform(post("/chat/api/messages").with(user(customer)).contentType("application/json")
                .content(json.writeValueAsString(request("no-csrf","hi")))).andExpect(status().isForbidden());
        mvc.perform(get("/chat/api/bootstrap")).andExpect(status().isUnauthorized());
        assertEquals(0,messages.count());
    }
    @Test void inactiveBranchCannotOpenOrReceiveNewMessages() {
        a.setIsActive(false);
        assertThrows(BadRequestException.class, () -> chat.openSession(customer.getUsername(),a.getBranchId()));
        assertThrows(BadRequestException.class, () -> chat.sendMessage(customer.getUsername(),request("off","hi")));
        assertThrows(AccessDeniedException.class, () -> chat.listSessions(staff.getUsername(),null,0));
    }
    @Test void rendersInboxAndSafeCustomerWidget() throws Exception {
        mvc.perform(get("/staff/chat").with(user(staff))).andExpect(status().isOk()).andExpect(content().string(containsString("data-techchat")));
        mvc.perform(get("/staff/chat").with(user(customer))).andExpect(status().isForbidden());
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(containsString("Đăng nhập để chat")))
                .andExpect(content().string(not(containsString("data-techchat"))));
        mvc.perform(get("/").with(user(customer))).andExpect(status().isOk()).andExpect(content().string(containsString("data-techchat")));
    }
    boolean allowed(User user, SimpMessageType type, String destination) {
        var headers = SimpMessageHeaderAccessor.create(type); headers.setDestination(destination);
        var auth = new UsernamePasswordAuthenticationToken(user,null,user.getAuthorities());
        var message = org.springframework.messaging.support.MessageBuilder.createMessage(new byte[0],headers.getMessageHeaders());
        return Objects.requireNonNull(messageAuthorizationManager.check(() -> auth,message)).isGranted();
    }
    @Test void stompCannotListenToOtherUsersOrPublishToBroker() {
        assertTrue(allowed(customer,SimpMessageType.SUBSCRIBE,"/user/queue/chat"));
        assertFalse(allowed(customer,SimpMessageType.SUBSCRIBE,"/topic/chat/"+sessionId));
        assertFalse(allowed(customer,SimpMessageType.SUBSCRIBE,"/topic/branch-chat/"+a.getBranchId()));
        assertFalse(allowed(customer,SimpMessageType.SUBSCRIBE,"/user/"+staff.getUsername()+"/queue/chat"));
        assertFalse(allowed(customer,SimpMessageType.SUBSCRIBE,"/queue/chat-user-forged"));
        assertFalse(allowed(customer,SimpMessageType.MESSAGE,"/topic/chat/"+sessionId));
        assertFalse(allowed(customer,SimpMessageType.SUBSCRIBE,"/topic/admin/orders"));
        assertTrue(allowed(admin,SimpMessageType.SUBSCRIBE,"/topic/admin/orders"));
    }
}
