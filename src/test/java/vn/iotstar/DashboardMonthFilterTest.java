package vn.iotstar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.iotstar.entity.Order;
import vn.iotstar.enums.OrderStatus;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:dashboard_month_filter;DB_CLOSE_DELAY=-1;MODE=MSSQLServer")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DashboardMonthFilterTest extends CommerceTestSupport {
    @Autowired MockMvc mvc;
    int sequence;

    @BeforeEach void setup() { fixtures(); }

    private void order(String createdAt, OrderStatus status) {
        persist(Order.builder().orderCode("MONTH-" + ++sequence).user(customer)
                .status(status).createdAt(LocalDateTime.parse(createdAt)).build());
    }

    @Test void includesLeapDayAndExcludesFirstInstantOfNextMonth() {
        // Orders with no branch must also count in the admin's whole-chain report.
        order("2024-01-31T23:59:59", OrderStatus.NEW);
        order("2024-02-01T00:00:00", OrderStatus.NEW);
        order("2024-02-29T23:59:59", OrderStatus.DELIVERED);
        order("2024-03-01T00:00:00", OrderStatus.CANCELLED);
        em.flush();
        var counts = orders.getOrderStatusCounts(LocalDateTime.parse("2024-02-01T00:00:00"),
                LocalDateTime.parse("2024-03-01T00:00:00"));
        assertEquals(1L, counts.get(OrderStatus.NEW));
        assertEquals(1L, counts.get(OrderStatus.DELIVERED));
        assertEquals(0L, counts.get(OrderStatus.CANCELLED));
        assertEquals(2L, counts.values().stream().mapToLong(Long::longValue).sum());
    }

    @Test void monthRangeCrossesYearAndKeepsGlobalCounters() throws Exception {
        order("2025-11-30T23:59:59", OrderStatus.NEW);
        order("2025-12-01T00:00:00", OrderStatus.NEW);
        order("2026-01-31T23:59:59", OrderStatus.DELIVERED);
        order("2026-02-01T00:00:00", OrderStatus.NEW);
        em.flush();
        var result = mvc.perform(get("/admin/dashboard").with(user(admin))
                        .param("fromMonth", "2025-12").param("toMonth", "2026-01"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("statusPeriodCaption", "12/2025 – 01/2026"))
                .andExpect(model().attribute("newOrdersCount", 3L))
                .andExpect(content().string(containsString("name=\"fromMonth\"")))
                .andReturn();
        @SuppressWarnings("unchecked")
        var counts = (Map<OrderStatus, Long>) result.getModelAndView().getModel().get("orderStatusCounts");
        assertEquals(2L, counts.values().stream().mapToLong(Long::longValue).sum());
    }

    @Test void invalidOrIncompleteRangesShowValidationAndAllTimeFallback() throws Exception {
        for (String[] range : new String[][]{{"2026-10", "2026-09"}, {"2026-10", ""},
                {"2026-13", "2026-13"}, {"0000-01", "2026-01"}}) {
            mvc.perform(get("/admin/dashboard").with(user(admin))
                            .param("fromMonth", range[0]).param("toMonth", range[1]))
                    .andExpect(status().isOk())
                    .andExpect(model().attributeExists("statusFilterError"))
                    .andExpect(model().attribute("statusPeriodCaption", "Tất cả thời gian"));
        }
        mvc.perform(get("/admin/dashboard").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(model().attributeDoesNotExist("statusFilterError"));
    }

    @Test void openingDashboardDefaultsToCurrentVietnamMonthAndAllTimeStillWorks() throws Exception {
        YearMonth currentMonth = YearMonth.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        order(currentMonth.atDay(1).atStartOfDay().toString(), OrderStatus.NEW);
        order(currentMonth.minusMonths(1).atDay(1).atStartOfDay().toString(), OrderStatus.DELIVERED);
        em.flush();
        var result = mvc.perform(get("/admin/dashboard").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("fromMonth", currentMonth.toString()))
                .andExpect(model().attribute("toMonth", currentMonth.toString()))
                .andExpect(model().attribute("statusPeriodCaption", "Tháng " + currentMonth.format(DateTimeFormatter.ofPattern("MM/yyyy"))))
                .andReturn();
        @SuppressWarnings("unchecked")
        var counts = (Map<OrderStatus, Long>) result.getModelAndView().getModel().get("orderStatusCounts");
        assertEquals(1L, counts.get(OrderStatus.NEW));
        assertEquals(0L, counts.get(OrderStatus.DELIVERED));

        var allTime = mvc.perform(get("/admin/dashboard").param("allTime", "true").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("statusPeriodCaption", "Tất cả thời gian"))
                .andExpect(model().attribute("fromMonth", ""))
                .andExpect(model().attribute("toMonth", ""))
                .andReturn();
        @SuppressWarnings("unchecked")
        var allCounts = (Map<OrderStatus, Long>) allTime.getModelAndView().getModel().get("orderStatusCounts");
        assertEquals(2L, allCounts.values().stream().mapToLong(Long::longValue).sum());
    }
}
