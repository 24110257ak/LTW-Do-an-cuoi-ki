package vn.iotstar;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.iotstar.entity.*;
import vn.iotstar.entity.Order;
import vn.iotstar.enums.*;
import vn.iotstar.exception.BadRequestException;
import vn.iotstar.repository.SimulatedPaymentRepository;
import vn.iotstar.service.PaymentSimulationService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:simulation_purchase;DB_CLOSE_DELAY=-1;MODE=MSSQLServer")
@AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class SimulationAndPurchaseTest extends CommerceTestSupport {
    @Autowired PaymentSimulationService simulator;
    @Autowired SimulatedPaymentRepository paymentRepo;
    @Autowired MockMvc mvc;
    @BeforeEach void setup() { fixtures(); }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    Order simulatedOrder() {
        var req=request();req.setPaymentMethod(PaymentMethod.VNPAY);
        return orders.placeOrder(customer.getUserId(),req);
    }

    @Test void cartBadgeTracksAddUpdateAndRemoveInsteadOfStaleSessionCount() throws Exception {
        mvc.perform(get("/products").with(user(customer)).sessionAttr("cartCount",99))
                .andExpect(status().isOk()).andExpect(model().attribute("cartCount",1))
                .andExpect(content().string(matchesPattern("(?s).*id=\"headerCartBadge\"[^>]*>1</span>.*")));
        mvc.perform(post("/cart/add").with(user(customer)).with(csrf())
                .param("variantId",variant.getVariantId().toString()).param("quantity","3"))
                .andExpect(redirectedUrl("/cart"));
        mvc.perform(get("/cart").with(user(customer))).andExpect(status().isOk())
                .andExpect(model().attribute("cartCount",4)).andExpect(model().attribute("itemCount",4))
                .andExpect(content().string(matchesPattern("(?s).*id=\"headerCartBadge\"[^>]*>4</span>.*")));
        var item=carts.getCartItems(customer.getUserId()).get(0);
        mvc.perform(post("/cart/update").with(user(customer)).with(csrf())
                .param("cartItemId",item.getCartItemId().toString()).param("quantity","2"))
                .andExpect(redirectedUrl("/cart"));
        mvc.perform(get("/products").with(user(customer))).andExpect(model().attribute("cartCount",2));
        mvc.perform(post("/cart/remove").with(user(customer)).with(csrf())
                .param("cartItemId",item.getCartItemId().toString())).andExpect(redirectedUrl("/cart"));
        mvc.perform(get("/cart").with(user(customer))).andExpect(model().attribute("cartCount",0))
                .andExpect(content().string(matchesPattern("(?s).*id=\"headerCartBadge\"[^>]*>0</span>.*")));
    }

    @Test void cartBadgeSumsQuantitiesAndDoesNotLeakBetweenAccountsOrGuests() throws Exception {
        var other=persist(ProductVariant.builder().product(product).sku("BADGE-VARIANT").price(new BigDecimal("2000000")).build());
        carts.addToCart(customer.getUserId(),other.getVariantId(),2);
        mvc.perform(get("/products").with(user(customer))).andExpect(model().attribute("cartCount",3));
        mvc.perform(get("/products").with(user(admin)).sessionAttr("cartCount",3))
                .andExpect(model().attribute("cartCount",0));
        mvc.perform(get("/products").with(anonymous()).sessionAttr("cartCount",3))
                .andExpect(model().attribute("cartCount",0));
        mvc.perform(post("/cart/clear").with(user(customer)).with(csrf())).andExpect(redirectedUrl("/cart"));
        mvc.perform(get("/products").with(user(customer))).andExpect(model().attribute("cartCount",0));
    }

    @Test void cartBadgeStaysAfterBuyNowAndClearsAfterCartCheckout() throws Exception {
        var buyNow=request();buyNow.setBuyNowVariantId(variant.getVariantId());buyNow.setBuyNowQuantity(2);
        orders.placeOrder(customer.getUserId(),buyNow);
        mvc.perform(get("/products").with(user(customer))).andExpect(model().attribute("cartCount",1));
        orders.placeOrder(customer.getUserId(),request());
        mvc.perform(get("/products").with(user(customer))).andExpect(model().attribute("cartCount",0));
    }

    @Test void cartWithOneOnlyAcceptsNineMore() {
        var limits=carts.getLimits(customer.getUserId(),variant.getVariantId());
        assertEquals(1,limits.inCartQuantity());assertEquals(9,limits.maxAdditionalQuantity());
        var ex=assertThrows(BadRequestException.class,()->carts.addToCart(customer.getUserId(),variant.getVariantId(),10));
        assertTrue(ex.getMessage().contains("đã có 1"));assertTrue(ex.getMessage().contains("tối đa 9"));
        assertEquals(1,carts.getCartItemCount(customer.getUserId()));
    }
    @Test void addingRemainingQuantityReachesExactlyTen() {
        carts.addToCart(customer.getUserId(),variant.getVariantId(),9);
        assertEquals(10,carts.getCartItemCount(customer.getUserId()));
        assertEquals(0,carts.getLimits(customer.getUserId(),variant.getVariantId()).maxAdditionalQuantity());
        assertThrows(BadRequestException.class,()->carts.addToCart(customer.getUserId(),variant.getVariantId(),1));
    }
    @Test void limitCombinesVariantsOfTheSameProduct() {
        var other=persist(ProductVariant.builder().product(product).sku("OTHER-VARIANT").price(new BigDecimal("2000000")).build());
        carts.addToCart(customer.getUserId(),other.getVariantId(),8);
        assertEquals(1,carts.getLimits(customer.getUserId(),variant.getVariantId()).maxAdditionalQuantity());
        assertThrows(BadRequestException.class,()->carts.addToCart(customer.getUserId(),other.getVariantId(),2));
        assertThrows(BadRequestException.class,()->carts.updateQuantity(customer.getUserId(),carts.getCartItems(customer.getUserId()).stream().filter(i->i.getVariant().getVariantId().equals(variant.getVariantId())).findFirst().orElseThrow().getCartItemId(),3));
    }
    @Test void forgedQuantityUpdateCannotBypassLimit() {
        var id=carts.getCartItems(customer.getUserId()).get(0).getCartItemId();
        assertThrows(BadRequestException.class,()->carts.updateQuantity(customer.getUserId(),id,Integer.MAX_VALUE));
        assertEquals(1,carts.getCartItemCount(customer.getUserId()));
    }
    @Test void productPageAndCartShowRemainingQuantityAndBuyNowButton() throws Exception {
        mvc.perform(get("/products/"+product.getSlug()).with(user(customer))).andExpect(status().isOk())
                .andExpect(content().string(containsString("btnBuyNow"))).andExpect(content().string(containsString("/checkout/buy-now")))
                .andExpect(content().string(containsString("Giỏ đã có 1 sản phẩm này; có thể thêm tối đa 9")));
        mvc.perform(get("/cart").with(user(customer))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Giỏ có 1/10 sản phẩm này; thêm tối đa 9")));
        mvc.perform(get("/cart/limits").with(user(customer)).param("variantId",variant.getVariantId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("maxAdditionalQuantity").value(9));
    }
    @Test void buyNowOrdersOnlySelectedQuantityWithoutChangingCart() {
        var req=request();req.setBuyNowVariantId(variant.getVariantId());req.setBuyNowQuantity(2);
        var order=orders.placeOrder(customer.getUserId(),req);
        assertEquals(1,order.getOrderItems().size());assertEquals(2,order.getOrderItems().get(0).getQuantity());
        assertEquals(0,new BigDecimal("4000000").compareTo(order.getTotalAmount()));
        assertEquals(1,carts.getCartItemCount(customer.getUserId()));assertEquals(8,stock());
    }
    @Test void buyNowWorksWithAnEmptyCart() {
        carts.clearCart(customer.getUserId());
        var req=request();req.setBuyNowVariantId(variant.getVariantId());req.setBuyNowQuantity(1);
        assertNotNull(orders.placeOrder(customer.getUserId(),req));assertEquals(0,carts.getCartItemCount(customer.getUserId()));
    }
    @Test void buyNowRevalidatesLimitAndProductActive() {
        var req=request();req.setBuyNowVariantId(variant.getVariantId());req.setBuyNowQuantity(11);
        assertThrows(BadRequestException.class,()->pricing.preview(customer.getUserId(),req));
        req.setBuyNowQuantity(1);product.setIsActive(false);
        assertThrows(BadRequestException.class,()->pricing.preview(customer.getUserId(),req));
    }
    @Test void buyNowWebFlowKeepsSelectedVariantAndQuantityForPricing() throws Exception {
        mvc.perform(post("/checkout/buy-now").with(user(customer)).with(csrf()).param("variantId",variant.getVariantId().toString()).param("quantity","2"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/checkout?buyNowVariantId="+variant.getVariantId()+"&buyNowQuantity=2"));
        mvc.perform(get("/checkout").with(user(customer)).param("buyNowVariantId",variant.getVariantId().toString()).param("buyNowQuantity","2"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Giỏ hàng hiện có được giữ nguyên")))
                .andExpect(content().string(containsString("name=\"buyNowQuantity\" value=\"2\"")));
        mvc.perform(get("/checkout/pricing").with(user(customer)).param("buyNowVariantId",variant.getVariantId().toString()).param("buyNowQuantity","2")
                .param("orderType","PICKUP").param("pickupBranchId",source.getBranchId().toString()).param("paymentMethod","VNPAY"))
                .andExpect(status().isOk()).andExpect(jsonPath("totalAmount").value(4000000));
    }
    @Test void simulationStartsPendingAndMustPayBeforeConfirmation() {
        var order=simulatedOrder();
        assertTrue(order.getPaymentSimulated());assertEquals(PaymentStatus.PENDING,order.getPaymentStatus());
        var payment=paymentRepo.findByOrderOrderId(order.getOrderId()).orElseThrow();
        assertEquals(0,payment.getAmount().compareTo(order.getTotalAmount()));assertTrue(payment.getReference().startsWith("SIM-VNPAY-"));
        assertEquals(java.util.List.of(OrderStatus.CANCELLED),orders.allowedNextStatuses(order));
        loginAdmin();assertThrows(BadRequestException.class,()->orders.updateOrderStatus(order.getOrderId(),OrderStatus.CONFIRMED,null));
    }
    @Test void simulatedSuccessIsPersistedOnceAndNotRealCashOrCompletedRevenue() {
        var order=simulatedOrder();
        var payment=simulator.settle(customer.getUserId(),order.getOrderCode(),PaymentSimulationService.Outcome.SUCCESS);
        var settled=payment.getSettledAt();
        simulator.settle(customer.getUserId(),order.getOrderCode(),PaymentSimulationService.Outcome.SUCCESS);
        assertEquals(SimulatedPaymentStatus.SUCCESS,payment.getStatus());assertEquals(settled,payment.getSettledAt());
        assertEquals(PaymentStatus.PAID,order.getPaymentStatus());assertEquals(OrderStatus.NEW,order.getStatus());assertEquals(9,stock());
        assertEquals(java.util.List.of(OrderStatus.CONFIRMED),orders.allowedNextStatuses(order));
        assertEquals(0,orderRepo.sumConfirmedPayments().signum());assertEquals(0,orderRepo.sumTotalRevenue().signum());
        assertEquals(0,order.getTotalAmount().compareTo(orderRepo.sumSimulatedPayments()));
    }
    @Test void simulatedFailureCancelsAndReleasesCouponAndStockOnce() {
        var coupon=coupon("SIM-FAIL",1);var req=request();req.setCouponCode(coupon.getCouponCode());req.setPaymentMethod(PaymentMethod.VNPAY);
        var order=orders.placeOrder(customer.getUserId(),req);
        simulator.settle(customer.getUserId(),order.getOrderCode(),PaymentSimulationService.Outcome.FAILED);
        simulator.settle(customer.getUserId(),order.getOrderCode(),PaymentSimulationService.Outcome.SUCCESS);
        assertEquals(OrderStatus.CANCELLED,order.getStatus());assertEquals(PaymentStatus.PENDING,order.getPaymentStatus());
        assertEquals(10,stock());assertEquals(0,coupon.getUsedCount());assertEquals(SimulatedPaymentStatus.FAILED,paymentRepo.findByOrderOrderId(order.getOrderId()).orElseThrow().getStatus());
    }
    @Test void cancellingSimulationRestoresStockWithoutCharging() {
        var order=simulatedOrder();simulator.settle(customer.getUserId(),order.getOrderCode(),PaymentSimulationService.Outcome.CANCELLED);
        assertEquals(OrderStatus.CANCELLED,order.getStatus());assertEquals(10,stock());assertEquals(PaymentStatus.PENDING,order.getPaymentStatus());
    }
    @Test void expiredPaymentCannotBeRevivedByLateSuccess() {
        var order=simulatedOrder();var p=paymentRepo.findByOrderOrderId(order.getOrderId()).orElseThrow();
        p.setCreatedAt(LocalDateTime.now().minusMinutes(16));p.setExpiresAt(LocalDateTime.now().minusMinutes(1));em.flush();
        simulator.settle(customer.getUserId(),order.getOrderCode(),PaymentSimulationService.Outcome.SUCCESS);
        simulator.expire(p.getPaymentId());assertEquals(SimulatedPaymentStatus.EXPIRED,p.getStatus());
        assertEquals(OrderStatus.CANCELLED,order.getStatus());assertEquals(PaymentStatus.PENDING,order.getPaymentStatus());assertEquals(10,stock());
    }
    @Test void anotherAccountCannotReadOrSettlePayment() {
        var order=simulatedOrder();
        assertThrows(AccessDeniedException.class,()->simulator.view(admin.getUserId(),order.getOrderCode()));
        assertThrows(AccessDeniedException.class,()->simulator.settle(admin.getUserId(),order.getOrderCode(),PaymentSimulationService.Outcome.SUCCESS));
        assertEquals(PaymentStatus.PENDING,order.getPaymentStatus());
    }
    @Test void mismatchedStoredAmountCannotBeMarkedPaid() {
        var order=simulatedOrder();var payment=paymentRepo.findByOrderOrderId(order.getOrderId()).orElseThrow();payment.setAmount(BigDecimal.ONE);em.flush();
        assertThrows(BadRequestException.class,()->simulator.settle(customer.getUserId(),order.getOrderCode(),PaymentSimulationService.Outcome.SUCCESS));
        assertEquals(PaymentStatus.PENDING,order.getPaymentStatus());
    }
    @Test void cancelledOrderCannotBePaidViaOldSession() {
        var order=simulatedOrder();orders.cancelOrder(customer.getUserId(),order.getOrderId(),"Customer cancel");
        var p=simulator.settle(customer.getUserId(),order.getOrderCode(),PaymentSimulationService.Outcome.SUCCESS);
        assertEquals(SimulatedPaymentStatus.CANCELLED,p.getStatus());assertEquals(10,stock());assertEquals(PaymentStatus.PENDING,order.getPaymentStatus());
    }
    @Test void simulationPageShowsControlsAndPostRequiresCsrf() throws Exception {
        var order=simulatedOrder();var url=order.getPaymentSimulationUrl();
        mvc.perform(get(url).with(user(customer))).andExpect(status().isOk()).andExpect(content().string(containsString("Không kết nối ngân hàng")))
                .andExpect(content().string(containsString("value=\"SUCCESS\"")));
        mvc.perform(post(url).with(user(customer)).param("outcome","SUCCESS")).andExpect(status().isForbidden());
        mvc.perform(post(url).with(user(customer)).with(csrf()).param("outcome","SUCCESS").param("amount","1"))
                .andExpect(status().is3xxRedirection());
        assertEquals(PaymentStatus.PAID,order.getPaymentStatus());
        mvc.perform(get(url).with(user(customer))).andExpect(status().isOk()).andExpect(content().string(containsString("Thanh toán mô phỏng thành công")))
                .andExpect(content().string(not(containsString("value=\"SUCCESS\""))));
    }
    @Test void apiSimulationIgnoresClientAmountAndChecksOwnership() throws Exception {
        var order=simulatedOrder();var url="/api/orders/"+order.getOrderCode()+"/payment-simulation";
        mvc.perform(post(url).with(user(admin)).param("outcome","SUCCESS")).andExpect(status().isForbidden());
        mvc.perform(post(url).with(user(customer)).param("outcome","SUCCESS").param("amount","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("data.simulated").value(true))
                .andExpect(jsonPath("data.amount").value(2000000)).andExpect(jsonPath("data.status").value("SUCCESS"));
    }
    @Test void expiryRejectsAnInconsistentPaymentRecord() {
        var order=simulatedOrder();var p=paymentRepo.findByOrderOrderId(order.getOrderId()).orElseThrow();
        p.setAmount(BigDecimal.ONE);p.setCreatedAt(LocalDateTime.now().minusMinutes(16));p.setExpiresAt(LocalDateTime.now().minusMinutes(1));em.flush();
        assertThrows(BadRequestException.class,()->simulator.expire(p.getPaymentId()));
        assertEquals(OrderStatus.NEW,order.getStatus());assertEquals(SimulatedPaymentStatus.PENDING,p.getStatus());assertEquals(9,stock());
    }
}
