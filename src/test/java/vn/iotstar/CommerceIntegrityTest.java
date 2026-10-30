package vn.iotstar;

import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.iotstar.entity.*;
import vn.iotstar.entity.Order;
import vn.iotstar.enums.*;
import vn.iotstar.exception.BadRequestException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:commerce_integrity;DB_CLOSE_DELAY=-1;MODE=MSSQLServer") @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class CommerceIntegrityTest extends CommerceTestSupport {
    @Autowired MockMvc mvc;
    @BeforeEach void setup() { fixtures(); }
    @AfterEach void logout() { SecurityContextHolder.clearContext(); }

    @Test void reportsOnlyCompletedMerchandiseAndUsesCompletionDay() {
        var date=LocalDate.now().minusDays(2);
        for (var status : List.of(OrderStatus.NEW,OrderStatus.SHIPPING,OrderStatus.DELIVERED,OrderStatus.RETURNED)) {
            persist(Order.builder().orderCode("REPORT-"+status).user(customer).branch(source).status(status)
                    .subtotal(new BigDecimal("10000000")).discountAmount(new BigDecimal("2000000")).shippingFee(new BigDecimal("40000"))
                    .totalAmount(new BigDecimal("8040000")).totalCost(new BigDecimal("5000000")).grossProfit(new BigDecimal("3000000"))
                    .createdAt(date.minusMonths(1).atStartOfDay()).completedAt(status==OrderStatus.DELIVERED?date.atTime(10,0):null).build());
        }
        em.flush();
        assertEquals(0,new BigDecimal("8000000").compareTo(orderRepo.sumTotalRevenue()));
        assertEquals(0,new BigDecimal("8000000").compareTo(orderRepo.sumRevenueByBranch(source.getBranchId())));
        assertEquals(0,new BigDecimal("5000000").compareTo(orderRepo.sumTotalCost()));
        assertEquals(0,new BigDecimal("3000000").compareTo(orderRepo.sumTotalGrossProfit()));
        assertEquals(0,new BigDecimal("8000000").compareTo(reports.getCompletedDays(date,date).get(0).revenue()));
        assertEquals(0,reports.getCompletedDays(date.minusMonths(1),date.minusMonths(1)).get(0).revenue().signum());
    }
    @Test void inactiveProductIsRejectedEvenAfterAddingToCart() {
        product.setIsActive(false);
        assertThrows(BadRequestException.class,()->orders.placeOrder(customer.getUserId(),request()));
        assertEquals(0,orderRepo.count()); assertEquals(10,stock());
    }
    @Test void inactiveVariantIsRejectedAtCheckout() {
        variant.setIsActive(false);
        assertThrows(BadRequestException.class,()->orders.placeOrder(customer.getUserId(),request()));
        assertEquals(0,orderRepo.count());
    }
    @Test void inactivePickupBranchCannotConsumeCouponOrStock() {
        var c=coupon("BRANCH",2); var req=request();req.setCouponCode(c.getCouponCode());source.setIsActive(false);
        assertThrows(BadRequestException.class,()->orders.placeOrder(customer.getUserId(),req));
        assertEquals(0,c.getUsedCount()); assertEquals(0,orderRepo.count()); assertEquals(10,stock());
    }
    @Test void noActiveBranchRejectsDeliveryWithoutCreatingAnOrder() {
        var c=coupon("NO-BRANCH",1); var req=request();req.setOrderType(OrderType.DELIVERY);req.setCouponCode(c.getCouponCode());
        source.setIsActive(false);destination.setIsActive(false);
        assertThrows(BadRequestException.class,()->orders.placeOrder(customer.getUserId(),req));
        assertEquals(0,c.getUsedCount()); assertEquals(0,orderRepo.count()); assertEquals(10,stock());
    }
    @Test void noBranchWithEntireCartRejectsDelivery() {
        stockRepo.findByBranchBranchIdAndVariantVariantId(source.getBranchId(),variant.getVariantId()).orElseThrow().setQuantity(1);
        carts.updateQuantity(customer.getUserId(),carts.getCartItems(customer.getUserId()).get(0).getCartItemId(),2);
        var req=request();req.setOrderType(OrderType.DELIVERY);
        assertThrows(BadRequestException.class,()->orders.placeOrder(customer.getUserId(),req));
        assertEquals(0,orderRepo.count()); assertEquals(1,stock());
    }
    @Test void nonPositivePriceIsRejected() {
        variant.setPrice(BigDecimal.ZERO);
        assertThrows(BadRequestException.class,()->orders.placeOrder(customer.getUserId(),request()));
    }
    @Test void quoteMatchesOrderAndPreviewDoesNotSpendCoupon() {
        var c=coupon("QUOTE",2);var req=request();req.setOrderType(OrderType.DELIVERY);req.setProvince("Hà Nội");req.setDistrict("Ba Đình");req.setCouponCode(c.getCouponCode());
        var quote=pricing.preview(customer.getUserId(),req);
        assertEquals(0,new BigDecimal("1940000").compareTo(quote.totalAmount())); assertEquals(0,c.getUsedCount());
        var order=orders.placeOrder(customer.getUserId(),req);
        assertEquals(0,quote.subtotal().compareTo(order.getSubtotal()));assertEquals(0,quote.shippingFee().compareTo(order.getShippingFee()));
        assertEquals(0,quote.discountAmount().compareTo(order.getDiscountAmount()));assertEquals(0,quote.totalAmount().compareTo(order.getTotalAmount()));assertEquals(1,c.getUsedCount());
    }
    @Test void quoteChangesWithCarrierAndPickupAndFreeShippingThreshold() {
        var carrier=persist(DeliveryProvider.builder().providerName("Quote carrier").baseFee(new BigDecimal("45000")).build());
        var req=request();req.setOrderType(OrderType.DELIVERY);req.setProviderId(carrier.getProviderId());
        assertEquals(0,new BigDecimal("45000").compareTo(pricing.preview(customer.getUserId(),req).shippingFee()));
        req.setOrderType(OrderType.PICKUP);assertEquals(0,pricing.preview(customer.getUserId(),req).shippingFee().signum());
        req.setOrderType(OrderType.DELIVERY);carts.updateQuantity(customer.getUserId(),carts.getCartItems(customer.getUserId()).get(0).getCartItemId(),3);
        assertEquals(0,pricing.preview(customer.getUserId(),req).shippingFee().signum());
    }
    @Test void inactiveCarrierIsRejectedEvenForFreeShipping() {
        var carrier=persist(DeliveryProvider.builder().providerName("Disabled carrier").baseFee(BigDecimal.ZERO).isActive(false).build());
        var req=request();req.setOrderType(OrderType.DELIVERY);req.setProviderId(carrier.getProviderId());
        assertThrows(BadRequestException.class,()->pricing.preview(customer.getUserId(),req));
    }
    @Test void scopedCouponNeedsItsBranchAndInvalidConfigurationsAreRejected() {
        var c=coupon("SCOPED",2);c.setBranch(source);em.flush();
        assertThrows(BadRequestException.class,()->coupons.validateCoupon("SCOPED",new BigDecimal("2000000"),null));
        var bad=Coupon.builder().couponCode("BAD").discountType(DiscountType.PERCENT).discountValue(new BigDecimal("150"))
                .validUntil(LocalDateTime.now().plusDays(1)).build();
        assertThrows(BadRequestException.class,()->coupons.saveCoupon(bad));
    }
    @Test void couponRulesRejectNegativeAmountsAndInvalidRanges() {
        var c=Coupon.builder().couponCode("RULES").discountType(DiscountType.AMOUNT).discountValue(BigDecimal.ONE).validUntil(LocalDateTime.now().plusDays(1)).build();
        c.setDiscountValue(BigDecimal.ONE.negate()); assertThrows(BadRequestException.class,()->vn.iotstar.service.CouponRules.validate(c));
        c.setDiscountValue(BigDecimal.ONE);c.setMinOrderAmt(BigDecimal.ONE.negate());assertThrows(BadRequestException.class,()->vn.iotstar.service.CouponRules.validate(c));
        c.setMinOrderAmt(BigDecimal.ZERO);c.setMaxDiscount(BigDecimal.ZERO);assertThrows(BadRequestException.class,()->vn.iotstar.service.CouponRules.validate(c));
        c.setMaxDiscount(null);c.setValidUntil(c.getValidFrom());assertThrows(BadRequestException.class,()->vn.iotstar.service.CouponRules.validate(c));
        c.setValidUntil(LocalDateTime.now().plusDays(1));c.setUsageLimit(0);assertThrows(BadRequestException.class,()->vn.iotstar.service.CouponRules.validate(c));
    }
    @Test void cancelReleasesCouponAndStockOnce() {
        var c=coupon("CANCEL",1);var req=request();req.setCouponCode(c.getCouponCode());
        var order=orders.placeOrder(customer.getUserId(),req);assertEquals(9,stock());assertEquals(1,c.getUsedCount());
        orders.cancelOrder(customer.getUserId(),order.getOrderId(),"Cancel");orders.cancelOrder(customer.getUserId(),order.getOrderId(),"Retry");
        assertEquals(10,stock());assertEquals(0,c.getUsedCount());
    }
    @Test void deliveryCannotSkipStatesAndCompletionTimeIsImmutable() {
        var req=request();req.setOrderType(OrderType.DELIVERY);var order=orders.placeOrder(customer.getUserId(),req);loginAdmin();
        assertThrows(BadRequestException.class,()->orders.updateOrderStatus(order.getOrderId(),OrderStatus.DELIVERED,null));
        ship(order);orders.confirmDelivery(order.getOrderId(),order.getTotalAmount(),"Người nhận",null);var completed=order.getCompletedAt();
        assertNotNull(completed);orders.confirmDelivery(order.getOrderId(),order.getTotalAmount(),"Người nhận",null);assertEquals(completed,order.getCompletedAt());
        assertThrows(BadRequestException.class,()->orders.updateOrderStatus(order.getOrderId(),OrderStatus.CANCELLED,null));
        assertThrows(BadRequestException.class,()->orders.updateOrderStatus(order.getOrderId(),OrderStatus.RETURNED_REFUND,null));
    }
    @Test void deliveryFailureMustReturnPhysicallyBeforeRestocking() {
        var req=request();req.setOrderType(OrderType.DELIVERY);var order=orders.placeOrder(customer.getUserId(),req);ship(order);
        orders.reportDeliveryFailed(order.getOrderId(),"Failed");assertEquals(9,stock());
        orders.updateOrderStatus(order.getOrderId(),OrderStatus.RETURNING,null);assertEquals(9,stock());
        orders.inspectReturn(order.getOrderId(),allGood(order));
        orders.inspectReturn(order.getOrderId(),allGood(order));assertEquals(10,stock());
    }
    @Test void transferOnTruckIsNotRestockedUntilReceiptAndRetryIsSafe() {
        var t=transfers.createTransfer(source.getBranchId(),destination.getBranchId(),Map.of(variant.getVariantId(),4),admin,null);
        transfers.approveTransfer(t.getTransferId(),admin);transfers.shipTransfer(t.getTransferId(),admin);transfers.cancelTransfer(t.getTransferId(),admin,"Cancel");
        assertEquals(TransferStatus.RETURNING,t.getStatus());assertEquals(6,stock());
        transfers.confirmReturnedTransfer(t.getTransferId(),Map.of(variant.getVariantId(),3),admin,"One unit missing");
        transfers.confirmReturnedTransfer(t.getTransferId(),Map.of(variant.getVariantId(),3),admin,"Retry");
        assertEquals(9,stock());assertEquals(TransferStatus.RETURNED,t.getStatus());assertNotNull(t.getReturnedAt());
        assertEquals(3,t.getItems().get(0).getReturnedQuantity());
    }
    @Test void vnPayCreatesPendingSimulationButCannotBeMarkedPaidManually() {
        var req=request();req.setPaymentMethod(PaymentMethod.VNPAY);
        var order=orders.placeOrder(customer.getUserId(),req);
        assertTrue(order.getPaymentSimulated());assertEquals(PaymentStatus.PENDING,order.getPaymentStatus());
        assertThrows(BadRequestException.class,()->orders.markAsPaid(order.getOrderId(),"fake"));
    }
    @Test void checkoutPageAndPricingEndpointUseBackendMoney() throws Exception {
        mvc.perform(get("/checkout").with(user(customer))).andExpect(status().isOk()).andExpect(content().string(containsString("payVNPAY")))
                .andExpect(content().string(containsString("displaySubtotal")));
        mvc.perform(get("/checkout/pricing").with(user(customer)).param("orderType","DELIVERY").param("paymentMethod","COD").param("province","Hà Nội"))
                .andExpect(status().isOk()).andExpect(jsonPath("shippingFee").value(40000)).andExpect(jsonPath("totalAmount").value(2040000));
    }
    @Test void adminFormCannotSetUsedCount() throws Exception {
        mvc.perform(post("/admin/coupons/save").with(user(admin)).with(csrf()).param("couponCode","FORGED").param("discountType","AMOUNT")
                .param("discountValue","1000").param("usageLimit","5").param("usedCount","4"))
                .andExpect(status().is3xxRedirection());
        assertEquals(0,couponRepo.findByCouponCode("FORGED").orElseThrow().getUsedCount());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"discount_value = -100", "discount_type = 'PERCENT', discount_value = 150", "min_order_amt = -1", "max_discount = 0", "used_count = 3", "usage_limit = 0", "coupon_code = '   '", "valid_until = valid_from"})
    void databaseRejectsInvalidCouponEvenWhenServiceIsBypassed(String assignment) {
        var c=coupon("DB-GUARD",2);em.flush();
        assertThrows(jakarta.persistence.PersistenceException.class,()->em.createNativeQuery("update coupons set " + assignment + " where coupon_id = :id")
                .setParameter("id",c.getCouponId()).executeUpdate());
    }
    @Test void completedTransferCannotBeCancelledOrReceivedTwice() {
        var t=transfers.createTransfer(source.getBranchId(),destination.getBranchId(),Map.of(variant.getVariantId(),4),admin,null);
        transfers.approveTransfer(t.getTransferId(),admin);transfers.shipTransfer(t.getTransferId(),admin);
        transfers.completeTransfer(t.getTransferId(),admin);transfers.completeTransfer(t.getTransferId(),admin);
        assertThrows(IllegalStateException.class,()->transfers.cancelTransfer(t.getTransferId(),admin,"Invalid"));
        assertEquals(6,stock());
    }
    @Test void dashboardAndOrderDetailShowBackendMetricsAndTransitions() throws Exception {
        var order=orders.placeOrder(customer.getUserId(),request());
        mvc.perform(get("/admin/dashboard").with(user(admin))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Doanh thu hàng hóa hoàn tất")))
                .andExpect(content().string(containsString("Ngày hoàn tất")));
        mvc.perform(get("/admin/orders/"+order.getOrderId()).with(user(admin))).andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"CONFIRMED\"")))
                .andExpect(content().string(not(containsString("value=\"DELIVERED\""))));
    }
    @Test void vnPayWebAndApiOrdersUseBackendSimulationAmounts() throws Exception {
        mvc.perform(post("/checkout/place-order").with(user(customer)).with(csrf())
                .param("orderType","PICKUP").param("pickupBranchId",source.getBranchId().toString()).param("paymentMethod","VNPAY"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("/checkout/vnpay-sim/*"));
        mvc.perform(post("/api/orders/checkout").with(user(customer)).contentType("application/json")
                .content("{\"orderType\":\"PICKUP\",\"pickupBranchId\":"+source.getBranchId()+",\"paymentMethod\":\"VNPAY\",\"buyNowVariantId\":"+variant.getVariantId()+",\"buyNowQuantity\":1,\"totalAmount\":1}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("data.totalAmount").value(2000000))
                .andExpect(jsonPath("data.paymentStatus").value("PENDING")).andExpect(jsonPath("data.paymentSimulated").value(true));
        assertEquals(2,orderRepo.count());assertEquals(8,stock());
    }
    @Test void returnFormRequiresActualReceiptAndRejectsExcessQuantity() throws Exception {
        var t=transfers.createTransfer(source.getBranchId(),destination.getBranchId(),Map.of(variant.getVariantId(),4),admin,null);
        transfers.approveTransfer(t.getTransferId(),admin);transfers.shipTransfer(t.getTransferId(),admin);transfers.cancelTransfer(t.getTransferId(),admin,null);
        mvc.perform(get("/admin/transfers").with(user(admin))).andExpect(status().isOk())
                .andExpect(content().string(containsString("qty_"+variant.getVariantId())))
                .andExpect(content().string(containsString("Xác nhận nhận hoàn")));
        assertThrows(IllegalArgumentException.class,()->transfers.confirmReturnedTransfer(t.getTransferId(),Map.of(variant.getVariantId(),5),admin,"Invalid"));
        assertEquals(6,stock());assertEquals(TransferStatus.RETURNING,t.getStatus());assertNull(t.getReturnedAt());
    }
}
