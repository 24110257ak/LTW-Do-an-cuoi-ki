package vn.iotstar;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.csrf.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.*;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import vn.iotstar.dto.ChatDto;
import vn.iotstar.entity.*;
import vn.iotstar.repository.*;
import vn.iotstar.service.ChatService;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:chat_stomp_only;DB_CLOSE_DELAY=-1;MODE=MSSQLServer")
@ActiveProfiles("test")
class ChatStompIntegrationTest {
    @Autowired @Qualifier("subProtocolWebSocketHandler") WebSocketHandler handler;
    @Autowired SimpUserRegistry registry;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired BranchRepository branches;
    @Autowired ChatSessionRepository sessions;
    @Autowired ChatMessageRepository messages;
    @Autowired ChatService chat;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    User customer, stranger, staff, other;
    Branch a,b;
    final List<Wire> wires=new ArrayList<>();

    @BeforeEach void setup() {
        a=branches.save(Branch.builder().branchName("Stomp A").address("A").district("A").phone("010").build());
        b=branches.save(Branch.builder().branchName("Stomp B").address("B").district("B").phone("011").build());
        var cr=roles.save(Role.builder().roleName("CUSTOMER").build());
        var sr=roles.save(Role.builder().roleName("STAFF").build());
        customer=create("stomp_customer",cr,null,"0915000001"); stranger=create("stomp_stranger",cr,null,"0915000002");
        staff=create("stomp_staff",sr,a,"0915000003"); other=create("stomp_other",sr,b,"0915000004");
    }
    User create(String name,Role role,Branch branch,String phone) {
        return users.save(User.builder().username(name).email(name+"@test.com").fullName(name).role(role).branch(branch)
                .password("test-password").phone(phone).isVerified(true).build());
    }
    @AfterEach void cleanup() throws Exception {
        for(Wire wire:wires) handler.afterConnectionClosed(wire.session,CloseStatus.NORMAL);
        messages.deleteAll(); sessions.deleteAll(); users.deleteAll(); branches.deleteAll(); roles.deleteAll();
    }
    class Wire {
        final WebSocketSession session=mock(WebSocketSession.class);
        final BlockingQueue<String> frames=new LinkedBlockingQueue<>();
        final AtomicBoolean open=new AtomicBoolean(true);
        Wire(User user, boolean csrf) throws Exception {
            String id=UUID.randomUUID().toString();
            CsrfToken raw=new DefaultCsrfToken("X-CSRF-TOKEN","_csrf",UUID.randomUUID().toString());
            var req=new MockHttpServletRequest();
            new XorCsrfTokenRequestAttributeHandler().handle(req,new MockHttpServletResponse(),() -> raw);
            String masked=((CsrfToken)req.getAttribute(CsrfToken.class.getName())).getToken();
            when(session.getId()).thenReturn(id); when(session.getUri()).thenReturn(URI.create("ws://localhost/ws"));
            when(session.getAcceptedProtocol()).thenReturn("v12.stomp");
            when(session.getAttributes()).thenReturn(new ConcurrentHashMap<>(Map.of(CsrfToken.class.getName(),raw)));
            when(session.getTextMessageSizeLimit()).thenReturn(65536); when(session.getBinaryMessageSizeLimit()).thenReturn(65536);
            when(session.getPrincipal()).thenReturn(new UsernamePasswordAuthenticationToken(user,null,user.getAuthorities()));
            when(session.isOpen()).thenAnswer(call -> open.get());
            doAnswer(call -> { frames.add(((TextMessage)call.getArgument(0)).getPayload()); return null; }).when(session).sendMessage(any());
            doAnswer(call -> { open.set(false); frames.add("CLOSED"); return null; }).when(session).close(any(CloseStatus.class));
            wires.add(this); handler.afterConnectionEstablished(session);
            send("CONNECT\naccept-version:1.2\nheart-beat:0,0\n"+(csrf?"X-CSRF-TOKEN:"+masked+"\n":"")+"\n");
            if(csrf) await("CONNECTED");
        }
        void send(String text) throws Exception { handler.handleMessage(session,new TextMessage(text+"\0")); }
        String await(String prefix) throws InterruptedException {
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(System.nanoTime()<deadline) {
                String frame=frames.poll(500,TimeUnit.MILLISECONDS);
                if(frame!=null && frame.stripLeading().startsWith(prefix)) return frame;
                if(frame!=null && (frame.startsWith("ERROR")||frame.equals("CLOSED"))) fail(frame);
            }
            throw new AssertionError("No "+prefix+" frame");
        }
        void subscribe(User user) throws Exception {
            send("SUBSCRIBE\nid:chat\ndestination:/user/queue/chat\n\n");
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
            while(System.nanoTime()<deadline) {
                var registered=registry.getUser(user.getUsername());
                if(registered!=null && registered.getSessions().stream().anyMatch(s -> s.getSubscriptions().stream()
                        .anyMatch(sub -> "/user/queue/chat".equals(sub.getDestination())))) return;
                Thread.sleep(10);
            }
            fail("Subscription not registered");
        }
    }
    @Test void realStompPipelineAndAfterCommitDeliverOnlyToAllowedUsers() throws Exception {
        Wire c=new Wire(customer,true), s=new Wire(staff,true), x=new Wire(stranger,true), o=new Wire(other,true);
        c.subscribe(customer); s.subscribe(staff); x.subscribe(stranger); o.subscribe(other);
        Long id=chat.openSession(customer.getUsername(),a.getBranchId()).sessionId();
        c.await("MESSAGE"); s.await("MESSAGE");
        var request=ChatDto.MessageRequest.builder().sessionId(id).clientMessageId("pipeline").content("Xin chào chi nhánh A").build();
        chat.sendMessage(customer.getUsername(),request);
        assertTrue(c.await("MESSAGE").contains("Xin chào chi nhánh A"));
        assertTrue(s.await("MESSAGE").contains("Xin chào chi nhánh A"));
        assertNull(x.frames.poll(200,TimeUnit.MILLISECONDS)); assertNull(o.frames.poll(200,TimeUnit.MILLISECONDS));
        chat.sendMessage(customer.getUsername(),request);
        assertEquals(1,messages.count()); assertNull(s.frames.poll(200,TimeUnit.MILLISECONDS));
        // A STOMP SEND passes through authentication, JSON validation, controller and the same service.
        s.send("SEND\ndestination:/app/chat.sendMessage\ncontent-type:application/json\n\n"+
                "{\"sessionId\":"+id+",\"clientMessageId\":\"reply\",\"content\":\"Chào bạn\",\"senderId\":"+stranger.getUserId()+"}");
        String reply=c.await("MESSAGE"); assertTrue(reply.contains("Chào bạn"));
        assertTrue(reply.contains("\"senderId\":"+staff.getUserId()));
        assertEquals(2,messages.count());
        s.frames.clear();
        s.send("SUBSCRIBE\nid:forbidden\ndestination:/topic/chat/"+id+"\n\n");
        s.await("ERROR");
    }
    @Test void rollbackDoesNotSaveOrBroadcastUncommittedMessage() throws Exception {
        Wire client=new Wire(customer,true), operator=new Wire(staff,true);
        client.subscribe(customer); operator.subscribe(staff);
        Long id=chat.openSession(customer.getUsername(),a.getBranchId()).sessionId();
        client.await("MESSAGE"); operator.await("MESSAGE");
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status -> {
            chat.sendMessage(customer.getUsername(),ChatDto.MessageRequest.builder()
                    .sessionId(id).clientMessageId("rollback").content("Must never be delivered").build());
            status.setRollbackOnly();
        });
        assertEquals(0,messages.count());
        assertNull(client.frames.poll(200,TimeUnit.MILLISECONDS)); assertNull(operator.frames.poll(200,TimeUnit.MILLISECONDS));
    }
    @Test void onlyOneOfTwoStaffCanClaimTheSameConversation() throws Exception {
        User colleague=create("stomp_colleague",staff.getRole(),a,"0915000005");
        Long id=chat.openSession(customer.getUsername(),a.getBranchId()).sessionId();
        java.util.function.Function<User,Boolean> claim=user -> {
            try { chat.claimSession(user.getUsername(),id); return true; }
            catch (vn.iotstar.exception.BadRequestException exception) { return false; }
        };
        var first=CompletableFuture.supplyAsync(() -> claim.apply(staff));
        var second=CompletableFuture.supplyAsync(() -> claim.apply(colleague));
        assertNotEquals(first.get(5,TimeUnit.SECONDS),second.get(5,TimeUnit.SECONDS));
        assertNotNull(chat.getSession(customer.getUsername(),id).staffId());
    }
    @Test void missingCsrfRejectsConnect() throws Exception {
        Wire wire=new Wire(customer,false); wire.await("ERROR");
    }
    @Test void concurrentOpenAndDuplicateSendProduceOneSessionAndOneMessage() throws Exception {
        java.util.function.Supplier<Long> open=() -> chat.openSession(customer.getUsername(),a.getBranchId()).sessionId();
        var first=CompletableFuture.supplyAsync(open); var second=CompletableFuture.supplyAsync(open);
        Long id=first.get(5,TimeUnit.SECONDS); assertEquals(id,second.get(5,TimeUnit.SECONDS)); assertEquals(1,sessions.count());
        var request=ChatDto.MessageRequest.builder().sessionId(id).content("One committed message").clientMessageId("concurrent").build();
        var sendA=CompletableFuture.supplyAsync(() -> chat.sendMessage(customer.getUsername(),request));
        var sendB=CompletableFuture.supplyAsync(() -> chat.sendMessage(customer.getUsername(),request));
        assertEquals(sendA.get(5,TimeUnit.SECONDS).getMessageId(),sendB.get(5,TimeUnit.SECONDS).getMessageId());
        assertEquals(1,messages.count());
    }
}
