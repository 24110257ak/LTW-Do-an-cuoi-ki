package vn.iotstar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.iotstar.entity.Order;
import vn.iotstar.entity.User;
import vn.iotstar.enums.OrderStatus;

import java.time.LocalDateTime;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:customer_order_filter;DB_CLOSE_DELAY=-1;MODE=MSSQLServer")
@AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class CustomerOrderFilterTest extends CommerceTestSupport {
    @Autowired MockMvc mvc;
    int sequence;
    @BeforeEach void setup() { fixtures(); }

    Order order(User owner, OrderStatus status) {
        return persist(Order.builder().user(owner).branch(source).orderCode("FILTER-" + ++sequence)
                .status(status).createdAt(LocalDateTime.of(2026, 10, 29, 10, 0)).build());
    }

    @SuppressWarnings("unchecked")
    Page<Order> page(String status, int number, int size) throws Exception {
        var request = get("/orders").with(user(customer)).param("page", "" + number).param("size", "" + size);
        if (status != null) request.param("status", status);
        var result = mvc.perform(request).andExpect(status().isOk()).andExpect(view().name("customer/order-history"))
                .andReturn();
        return (Page<Order>) result.getModelAndView().getModel().get("orderPage");
    }

    @Test void cancelledTabOnlyReturnsCancelledOrdersOfSignedInCustomer() throws Exception {
        order(customer, OrderStatus.NEW); order(customer, OrderStatus.SHIPPING); order(customer, OrderStatus.DELIVERED);
        var cancelled = order(customer, OrderStatus.CANCELLED);
        order(admin, OrderStatus.CANCELLED);
        var result = page("CANCELLED", 0, 10);
        assertEquals(1, result.getTotalElements());
        assertEquals(cancelled.getOrderId(), result.getContent().get(0).getOrderId());
    }

    @Test void countsAndPagesAreCalculatedAfterFiltering() throws Exception {
        for (int i = 0; i < 23; i++) order(customer, i < 3 ? OrderStatus.CANCELLED : OrderStatus.NEW);
        var result = page("CANCELLED", 0, 10);
        assertEquals(3, result.getTotalElements()); assertEquals(1, result.getTotalPages());
        assertEquals(3, result.getNumberOfElements());
        assertEquals(23, page(null, 0, 10).getTotalElements());
        assertEquals(3, page(null, 0, 10).getTotalPages());
    }

    @Test void stablePaginationKeepsFilterSizeAndDoesNotRepeatOrders() throws Exception {
        Order newest = null;
        for (int i = 0; i < 15; i++) newest = order(customer, OrderStatus.CANCELLED);
        order(customer, OrderStatus.NEW); order(admin, OrderStatus.CANCELLED);
        var first = page("CANCELLED", 0, 10); var second = page("CANCELLED", 1, 10);
        assertEquals(newest.getOrderId(), first.getContent().get(0).getOrderId());
        assertEquals(10, first.getNumberOfElements()); assertEquals(5, second.getNumberOfElements());
        var ids = new HashSet<Long>();
        first.forEach(o -> assertTrue(ids.add(o.getOrderId())));
        second.forEach(o -> assertTrue(ids.add(o.getOrderId())));
        assertEquals(15, ids.size());
        mvc.perform(get("/orders").with(user(customer)).param("status", "CANCELLED").param("size", "10"))
                .andExpect(content().string(containsString("/orders?status=CANCELLED&amp;page=1&amp;size=10")))
                .andExpect(content().string(containsString("/orders?status=NEW&amp;page=0&amp;size=10")));
        mvc.perform(get("/orders").with(user(customer)).param("status", "CANCELLED").param("page", "1").param("size", "10"))
                .andExpect(content().string(containsString("/orders?status=CANCELLED&amp;page=0&amp;size=10")));
    }

    @Test void emptyStatusAndOutOfRangePageRenderWithoutLeakingOtherOrders() throws Exception {
        order(customer, OrderStatus.NEW); order(admin, OrderStatus.CANCELLED);
        assertTrue(page("CANCELLED", 0, 10).isEmpty());
        assertTrue(page(null, 10, 10).isEmpty());
        mvc.perform(get("/orders").with(user(customer)).param("status", "CANCELLED"))
                .andExpect(content().string(containsString("Bạn chưa có đơn hàng nào ở trạng thái này!")));
    }

    @Test void legacyServiceMethodStillReturnsEveryStatusForOnlyThatUser() {
        order(customer, OrderStatus.NEW); order(customer, OrderStatus.CANCELLED); order(admin, OrderStatus.NEW);
        assertEquals(2, orders.getOrdersByUser(customer.getUserId(), PageRequest.of(0, 10)).getTotalElements());
    }

    @Test void invalidPaginationIsBoundedAndAnonymousAccessRequiresLogin() throws Exception {
        order(customer, OrderStatus.NEW);
        var result = page(null, -1, 0);
        assertEquals(0, result.getNumber()); assertEquals(1, result.getSize());
        assertEquals(100, page(null, 0, 1000).getSize());
        mvc.perform(get("/orders")).andExpect(resultWithoutLogin -> {
            int code = resultWithoutLogin.getResponse().getStatus();
            assertTrue(code == 401 || code == 302, "Anonymous request must require authentication");
        });
    }

    private String tabGroupTag(String html, String id) {
        var match = java.util.regex.Pattern.compile("<div\\b[^>]*\\bid=\"" + id + "\"[^>]*>").matcher(html);
        assertTrue(match.find(), "Missing customer status group " + id);
        return match.group();
    }

    @Test void originalTabsShowFirstAndExtraTabsAreBehindTheArrow() throws Exception {
        var html = mvc.perform(get("/orders").with(user(customer)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(tabGroupTag(html, "orderStatusBasic").contains("hidden"));
        assertTrue(tabGroupTag(html, "orderStatusExtra").contains("hidden"));
        assertTrue(html.contains("id=\"orderStatusTabsToggle\""));
        assertTrue(html.contains("/js/customer-order-tabs.js"));
        var basic = html.substring(html.indexOf("id=\"orderStatusBasic\""), html.indexOf("id=\"orderStatusExtra\""));
        assertEquals(7, basic.split("<a ", -1).length - 1, "Keep All and the six original status tabs");
        assertTrue(basic.contains("status=RETURNED_REFUND"));
        assertFalse(basic.contains("status=READY_FOR_PICKUP"));
        for (String status : new String[]{"DELIVERY_FAILED", "RETURNING", "RETURNED"}) {
            assertFalse(html.contains("/orders?status=" + status + "&amp;"), "No separate tab for " + status);
        }
    }

    @Test void selectingReadyForPickupKeepsTheOnlyExtraTabVisibleAfterNavigation() throws Exception {
        for (String status : new String[]{"READY_FOR_PICKUP"}) {
            var html = mvc.perform(get("/orders").with(user(customer)).param("status", status).param("size", "5"))
                    .andExpect(status().isOk()).andExpect(model().attribute("selectedStatus", OrderStatus.valueOf(status)))
                    .andReturn().getResponse().getContentAsString();
            assertTrue(tabGroupTag(html, "orderStatusBasic").contains("hidden"));
            assertFalse(tabGroupTag(html, "orderStatusExtra").contains("hidden"));
            assertTrue(html.contains("/orders?status=" + status + "&amp;page=0&amp;size=5"));
            var extra = html.substring(html.indexOf("id=\"orderStatusExtra\""), html.indexOf("id=\"orderStatusTabsToggle\""));
            assertEquals(1, extra.split("<a ", -1).length - 1);
        }
    }

    @Test void shippingTabIncludesFailedDeliveryButExcludesReturnsAndOtherCustomers() throws Exception {
        order(customer, OrderStatus.SHIPPING); order(customer, OrderStatus.DELIVERY_FAILED);
        order(customer, OrderStatus.RETURNING); order(customer, OrderStatus.NEW);
        order(admin, OrderStatus.DELIVERY_FAILED);
        var result = page("SHIPPING", 0, 10);
        assertEquals(2, result.getTotalElements());
        assertEquals(java.util.Set.of(OrderStatus.SHIPPING, OrderStatus.DELIVERY_FAILED),
                result.getContent().stream().map(Order::getStatus).collect(java.util.stream.Collectors.toSet()));
        mvc.perform(get("/orders").with(user(customer)).param("status", "SHIPPING"))
                .andExpect(content().string(containsString("Giao không thành công")));
    }

    @Test void returnsTabIncludesEveryReturnStepAndShowsTheActualProgress() throws Exception {
        order(customer, OrderStatus.RETURNING); order(customer, OrderStatus.RETURNED);
        order(customer, OrderStatus.RETURNED_REFUND); order(customer, OrderStatus.DELIVERED);
        order(admin, OrderStatus.RETURNED);
        var result = page("RETURNED_REFUND", 0, 10);
        assertEquals(3, result.getTotalElements());
        assertEquals(java.util.Set.of(OrderStatus.RETURNING, OrderStatus.RETURNED, OrderStatus.RETURNED_REFUND),
                result.getContent().stream().map(Order::getStatus).collect(java.util.stream.Collectors.toSet()));
        mvc.perform(get("/orders").with(user(customer)).param("status", "RETURNED_REFUND"))
                .andExpect(content().string(containsString("Đang chuyển hoàn về kho")))
                .andExpect(content().string(containsString("Đã hoàn về kho")));
    }

    @Test void groupedReturnsAreCountedAndPaginatedTogetherWithoutDuplicates() throws Exception {
        var statuses = new OrderStatus[]{OrderStatus.RETURNING, OrderStatus.RETURNED, OrderStatus.RETURNED_REFUND};
        for (int i = 0; i < 7; i++) order(customer, statuses[i % 3]);
        order(customer, OrderStatus.SHIPPING); order(admin, OrderStatus.RETURNED_REFUND);
        var ids = new HashSet<Long>();
        for (int number = 0; number < 3; number++) {
            var result = page("RETURNED_REFUND", number, 3);
            assertEquals(7, result.getTotalElements());
            assertEquals(3, result.getTotalPages());
            result.forEach(o -> assertTrue(ids.add(o.getOrderId())));
        }
        assertEquals(7, ids.size());
    }

    @Test void oldIntermediateStatusLinksSelectTheMatchingBasicTab() throws Exception {
        for (String[] mapping : new String[][]{{"DELIVERY_FAILED", "SHIPPING"},
                {"RETURNING", "RETURNED_REFUND"}, {"RETURNED", "RETURNED_REFUND"}}) {
            var html = mvc.perform(get("/orders").with(user(customer)).param("status", mapping[0]))
                    .andExpect(status().isOk())
                    .andExpect(model().attribute("selectedStatus", OrderStatus.valueOf(mapping[1])))
                    .andReturn().getResponse().getContentAsString();
            assertFalse(tabGroupTag(html, "orderStatusBasic").contains("hidden"));
            assertTrue(tabGroupTag(html, "orderStatusExtra").contains("hidden"));
        }
    }

    @Test void originalExactStatusServiceKeepsItsPreviousBehavior() {
        order(customer, OrderStatus.SHIPPING); order(customer, OrderStatus.DELIVERY_FAILED);
        var exact = orders.getOrdersByUser(customer.getUserId(), OrderStatus.SHIPPING, PageRequest.of(0, 10));
        assertEquals(1, exact.getTotalElements());
        assertEquals(OrderStatus.SHIPPING, exact.getContent().get(0).getStatus());
    }
}
