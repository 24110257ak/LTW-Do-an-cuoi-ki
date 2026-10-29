package vn.iotstar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.iotstar.entity.*;
import vn.iotstar.enums.OrderStatus;
import vn.iotstar.repository.*;
import vn.iotstar.service.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StaffBranchAuthorizationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired BranchRepository branches;
    @Autowired OrderRepository orders;
    @Autowired BranchService branchService;
    @Autowired OrderService orderService;
    @Autowired InventoryService inventoryService;

    private Branch a, b;
    private User staff, admin;
    private Order own, other;

    @BeforeEach
    void setup() {
        a = branches.save(Branch.builder().branchName("Scope A").address("A").district("A").phone("01").build());
        b = branches.save(Branch.builder().branchName("Scope B").address("B").district("B").phone("02").build());
        Role staffRole = roles.save(Role.builder().roleName("STAFF").build());
        Role adminRole = roles.save(Role.builder().roleName("ADMIN").build());
        staff = users.save(User.builder().username("staff_hn").email("scope-staff@test.com")
                .password("test-password").fullName("Staff").phone("0911100001").isVerified(true).role(staffRole).branch(a).build());
        admin = users.save(User.builder().username("scope_admin").email("scope-admin@test.com")
                .password("test-password").fullName("Admin").phone("0911100002").isVerified(true).role(adminRole).build());
        own = orders.save(Order.builder().orderCode("SCOPE-A").checkoutToken("scope-token-a").user(admin).branch(a).build());
        other = orders.save(Order.builder().orderCode("SCOPE-B").checkoutToken("scope-token-b").user(admin).branch(b).build());
    }

    @AfterEach
    void clearContext() { SecurityContextHolder.clearContext(); }

    private void login(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @Test
    void ignoresUsernameAndForgedSessionAndHidesSelectorForStaff() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("staffBranchId", b.getBranchId());
        mvc.perform(get("/staff/inventory").with(user(staff)).session(session))
                .andExpect(status().isOk())
                .andExpect(model().attribute("currentBranch", hasProperty("branchId", is(a.getBranchId()))))
                .andExpect(content().string(not(containsString("/staff/switch-branch"))));
        assertNull(session.getAttribute("staffBranchId"));
    }

    @Test
    void staffCannotPostSwitchBranchEvenWithCsrf() throws Exception {
        mvc.perform(post("/staff/switch-branch").with(user(staff)).with(csrf())
                .param("branchId", b.getBranchId().toString())).andExpect(status().isForbidden());
    }

    @Test
    void adminCanSwitchAndSeesSelector() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/staff/switch-branch").with(user(admin)).with(csrf()).session(session)
                .param("branchId", b.getBranchId().toString())).andExpect(status().is3xxRedirection());
        mvc.perform(get("/staff/inventory").with(user(admin)).session(session))
                .andExpect(status().isOk())
                .andExpect(model().attribute("currentBranch", hasProperty("branchId", is(b.getBranchId()))))
                .andExpect(content().string(containsString("/staff/switch-branch")));
    }

    @Test
    void crossBranchOrderWebAndApiReturn403AndCannotChangeStatus() throws Exception {
        mvc.perform(get("/staff/orders/" + other.getOrderId()).with(user(staff))).andExpect(status().isForbidden());
        mvc.perform(get("/api/orders/" + other.getOrderCode()).with(user(staff)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("status").value(403));
        mvc.perform(post("/staff/orders/" + other.getOrderId() + "/status").with(user(staff)).with(csrf())
                .param("status", "CONFIRMED")).andExpect(status().isForbidden());
        assertEquals(OrderStatus.NEW, orders.findById(other.getOrderId()).orElseThrow().getStatus());
    }

    @Test
    void sameBranchOrderCanBeReadAndUpdated() throws Exception {
        mvc.perform(get("/staff/orders/" + own.getOrderId()).with(user(staff))).andExpect(status().isOk());
        mvc.perform(post("/staff/orders/" + own.getOrderId() + "/status").with(user(staff)).with(csrf())
                .param("status", "CONFIRMED")).andExpect(status().is3xxRedirection());
        assertEquals(OrderStatus.CONFIRMED, own.getStatus());
    }

    @Test
    void servicesDenyCrossBranchOrderAndInventoryBeforeMutation() {
        login(staff);
        assertThrows(AccessDeniedException.class, () -> orderService.getOrderById(other.getOrderId()));
        assertThrows(AccessDeniedException.class, () -> orderService.updateOrderStatus(other.getOrderId(), OrderStatus.CONFIRMED, null));
        assertThrows(AccessDeniedException.class, () -> inventoryService.updateStock(b.getBranchId(), 999L, 10));
        assertThrows(AccessDeniedException.class, () -> inventoryService.getInventoryByBranch(b.getBranchId()));
        assertDoesNotThrow(() -> inventoryService.getInventoryByBranch(a.getBranchId()));
    }

    @Test
    void staffCannotUseCodOrDeliveryApiToModifyAnotherBranch() throws Exception {
        for (String action : new String[]{"reconcile-cod", "shipper-deliver"}) {
            mvc.perform(put("/api/orders/" + other.getOrderId() + "/" + action).with(user(staff)))
                    .andExpect(status().isForbidden());
        }
        assertEquals(OrderStatus.NEW, other.getStatus());
        assertFalse(other.getCodCollected());
    }

    @Test
    void codQueueOnlyContainsAssignedBranch() throws Exception {
        own.setCodCollected(true);
        other.setCodCollected(true);
        orders.flush();
        login(staff);
        assertEquals(java.util.List.of(own.getOrderId()), orderService.getPendingCodReconciliationOrders()
                .stream().map(Order::getOrderId).toList());
    }

    @Test
    void noAssignmentInactiveBranchAndLockedAccountAreDenied() throws Exception {
        staff.setBranch(null);
        mvc.perform(get("/staff/inventory").with(user(staff))).andExpect(status().isForbidden());
        staff.setBranch(a);
        a.setIsActive(false);
        mvc.perform(get("/staff/inventory").with(user(staff))).andExpect(status().isForbidden());
        a.setIsActive(true);
        staff.setIsActive(false);
        mvc.perform(get("/staff/inventory").with(user(staff))).andExpect(status().isForbidden());
    }

    @Test
    void adminAssignmentChangesScopeImmediatelyAndCanBeRevoked() throws Exception {
        mvc.perform(post("/admin/users/" + staff.getUserId() + "/branch").with(user(admin)).with(csrf())
                .param("branchId", b.getBranchId().toString())).andExpect(status().is3xxRedirection());
        // Old session points to A; database assignment to B must win.
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("staffBranchId", a.getBranchId());
        mvc.perform(get("/staff/inventory").with(user(staff)).session(session))
                .andExpect(status().isOk())
                .andExpect(model().attribute("currentBranch", hasProperty("branchId", is(b.getBranchId()))));
        mvc.perform(post("/admin/users/" + staff.getUserId() + "/branch").with(user(admin)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/staff/inventory").with(user(staff))).andExpect(status().isForbidden());
    }

    @Test
    void staffCannotAssignThemselvesAndAdminCannotSelectInactiveBranch() throws Exception {
        mvc.perform(post("/admin/users/" + staff.getUserId() + "/branch").with(user(staff)).with(csrf())
                .param("branchId", b.getBranchId().toString())).andExpect(status().isForbidden());
        b.setIsActive(false);
        mvc.perform(post("/staff/switch-branch").with(user(admin)).with(csrf())
                .param("branchId", b.getBranchId().toString())).andExpect(status().isBadRequest());
        mvc.perform(post("/admin/users/" + staff.getUserId() + "/branch").with(user(admin)).with(csrf())
                .param("branchId", b.getBranchId().toString())).andExpect(status().isBadRequest());
        assertEquals(a.getBranchId(), staff.getBranch().getBranchId());
    }

    @Test
    void adminUsersPageRendersAssignmentForm() throws Exception {
        mvc.perform(get("/admin/users").with(user(admin))).andExpect(status().isOk())
                .andExpect(content().string(containsString("/admin/users/" + staff.getUserId() + "/branch")));
    }
}
