package vn.iotstar;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.iotstar.entity.*;
import vn.iotstar.exception.BadRequestException;
import vn.iotstar.service.InventoryService;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:commerce_concurrency;DB_CLOSE_DELAY=-1;MODE=MSSQLServer") @ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CommerceConcurrencyTest extends CommerceTestSupport {
    @Autowired PlatformTransactionManager transactions;
    @Autowired vn.iotstar.service.PaymentSimulationService simulator;
    @Autowired vn.iotstar.repository.SimulatedPaymentRepository paymentRepo;
    @SpyBean InventoryService stockSpy;
    TransactionTemplate tx;
    @BeforeEach void setup() { tx=new TransactionTemplate(transactions);tx.execute(s->{fixtures();return null;}); }

    List<Boolean> race(Callable<Boolean> action) throws Exception {
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            var a=pool.submit(()->{start.await();return action.call();});
            var b=pool.submit(()->{start.await();return action.call();});
            start.countDown();return List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }

    @Test void onlyOneTransactionCanReserveLastCouponUse() throws Exception {
        var id=tx.execute(s->coupon("LAST",1).getCouponId());
        var outcomes=race(()->{
            try { tx.execute(s->{coupons.reserveCoupon("LAST",new java.math.BigDecimal("2000000"),source.getBranchId());return null;});return true; }
            catch (BadRequestException expected) { return false; }
        });
        assertEquals(1,outcomes.stream().filter(Boolean::booleanValue).count());
        assertEquals(1,couponRepo.findById(id).orElseThrow().getUsedCount());
    }
    @Test void concurrentApprovalAndReturnReceiptChangeStockOnce() throws Exception {
        var id=tx.execute(s->transfers.createTransfer(source.getBranchId(),destination.getBranchId(),Map.of(variant.getVariantId(),4),admin,null).getTransferId());
        race(()->{transfers.approveTransfer(id,admin);return true;});assertEquals(6,stock());
        transfers.shipTransfer(id,admin);transfers.cancelTransfer(id,admin,"Return request");assertEquals(6,stock());
        race(()->{transfers.confirmReturnedTransfer(id,Map.of(variant.getVariantId(),4),admin,null);return true;});
        assertEquals(10,stock());
    }
    @Test void twoCustomersCannotCheckoutWithTheLastCouponUse() throws Exception {
        var couponId=tx.execute(s->coupon("FINAL-ORDER",1).getCouponId());
        var otherId=tx.execute(s->{
            var other=persist(User.builder().username("other_buyer").email("other@example.test").phone("0901000003")
                    .password("test").fullName("Other buyer").role(customer.getRole()).isVerified(true).build());
            carts.addToCart(other.getUserId(),variant.getVariantId(),1);return other.getUserId();
        });
        var ids=List.of(customer.getUserId(),otherId);var index=new java.util.concurrent.atomic.AtomicInteger();
        var outcomes=race(()->{
            long userId=ids.get(index.getAndIncrement());var req=request();req.setCouponCode("FINAL-ORDER");
            try { orders.placeOrder(userId,req);return true; } catch (BadRequestException expected) { return false; }
        });
        assertEquals(1,outcomes.stream().filter(Boolean::booleanValue).count());
        assertEquals(1,orderRepo.count());assertEquals(9,stock());assertEquals(1,couponRepo.findById(couponId).orElseThrow().getUsedCount());
    }
    @Test void failedStockDeductionRollsBackOrderAndCoupon() {
        var id=tx.execute(s->coupon("ROLLBACK",1).getCouponId());
        doThrow(new BadRequestException("Simulated stock failure after coupon reservation")).when(stockSpy)
                .adjustStock(anyInt(),anyLong(),intThat(n->n<0),eq(StockMovementType.SALE),any(),anyString(),anyString());
        var req=request();req.setCouponCode("ROLLBACK");
        assertThrows(BadRequestException.class,()->orders.placeOrder(customer.getUserId(),req));
        assertEquals(0,orderRepo.count());assertEquals(0,couponRepo.findById(id).orElseThrow().getUsedCount());
        assertEquals(10,stock());assertEquals(1,carts.getCartItems(customer.getUserId()).size());
    }
    @Test void failedRestockRollsBackCancellationAndCouponRelease() {
        var couponId=tx.execute(s->coupon("RESTOCK",1).getCouponId());
        var req=request();req.setCouponCode("RESTOCK");var order=orders.placeOrder(customer.getUserId(),req);
        tx.execute(s->{stockRepo.findByBranchBranchIdAndVariantVariantId(source.getBranchId(),variant.getVariantId()).orElseThrow().setQuantity(Integer.MAX_VALUE);return null;});
        assertThrows(IllegalStateException.class,()->orders.cancelOrder(customer.getUserId(),order.getOrderId(),"Return"));
        assertEquals(vn.iotstar.enums.OrderStatus.NEW,orderRepo.findById(order.getOrderId()).orElseThrow().getStatus());
        assertEquals(1,couponRepo.findById(couponId).orElseThrow().getUsedCount());assertEquals(Integer.MAX_VALUE,stock());
    }
    @Test void duplicateCheckoutTokenInParallelCreatesOneOrder() throws Exception {
        var req=request();req.setCheckoutToken("CONCURRENT-TOKEN");
        race(()->{orders.placeOrder(customer.getUserId(),req);return true;});
        assertEquals(1,orderRepo.count());assertEquals(9,stock());
    }
    @Test void checkoutAndTransferShareAConsistentLockOrder() throws Exception {
        var transferId=tx.execute(s->transfers.createTransfer(source.getBranchId(),destination.getBranchId(),Map.of(variant.getVariantId(),4),admin,null).getTransferId());
        var step=new java.util.concurrent.atomic.AtomicInteger();
        race(()->{
            if (step.getAndIncrement()==0) orders.placeOrder(customer.getUserId(),request());
            else transfers.approveTransfer(transferId,admin);
            return true;
        });
        assertEquals(5,stock());assertEquals(1,orderRepo.count());
    }
    @Test void concurrentCartAddsRespectPersonalLimit() throws Exception {
        var results=race(()->{
            try { carts.addToCart(customer.getUserId(),variant.getVariantId(),9);return true; }
            catch (BadRequestException expected) { return false; }
        });
        assertEquals(1,results.stream().filter(Boolean::booleanValue).count());
        assertEquals(10,carts.getCartItemCount(customer.getUserId()));
    }
    @Test void concurrentBuyNowRetriesPreserveExistingCart() throws Exception {
        var req=request();req.setBuyNowVariantId(variant.getVariantId());req.setBuyNowQuantity(2);req.setCheckoutToken("BUY-NOW-ONCE");
        race(()->{orders.placeOrder(customer.getUserId(),req);return true;});
        assertEquals(1,orderRepo.count());assertEquals(8,stock());assertEquals(1,carts.getCartItemCount(customer.getUserId()));
    }
    @Test void concurrentSimulatedSuccessSettlesOnce() throws Exception {
        var req=request();req.setPaymentMethod(vn.iotstar.enums.PaymentMethod.VNPAY);var order=orders.placeOrder(customer.getUserId(),req);
        race(()->{simulator.settle(customer.getUserId(),order.getOrderCode(),vn.iotstar.service.PaymentSimulationService.Outcome.SUCCESS);return true;});
        assertEquals(vn.iotstar.enums.PaymentStatus.PAID,orderRepo.findById(order.getOrderId()).orElseThrow().getPaymentStatus());
        assertEquals(vn.iotstar.enums.SimulatedPaymentStatus.SUCCESS,paymentRepo.findByOrderOrderId(order.getOrderId()).orElseThrow().getStatus());
        assertEquals(9,stock());assertEquals(1,paymentRepo.count());assertEquals(0,orderRepo.sumConfirmedPayments().signum());
    }
    @Test void expiredSimulationReleasesStockAndCouponOnceUnderRace() throws Exception {
        var couponId=tx.execute(s->coupon("SIM-EXPIRE",1).getCouponId());
        var req=request();req.setPaymentMethod(vn.iotstar.enums.PaymentMethod.VNPAY);req.setCouponCode("SIM-EXPIRE");
        var order=orders.placeOrder(customer.getUserId(),req);
        var paymentId=tx.execute(s->{
            var p=paymentRepo.findByOrderOrderId(order.getOrderId()).orElseThrow();
            p.setCreatedAt(java.time.LocalDateTime.now().minusMinutes(16));p.setExpiresAt(java.time.LocalDateTime.now().minusMinutes(1));return p.getPaymentId();
        });
        race(()->{simulator.expire(paymentId);return true;});
        assertEquals(10,stock());assertEquals(0,couponRepo.findById(couponId).orElseThrow().getUsedCount());
        assertEquals(vn.iotstar.enums.SimulatedPaymentStatus.EXPIRED,paymentRepo.findById(paymentId).orElseThrow().getStatus());
    }
    @Test void successRacingWithCancellationCannotResurrectReleasedStock() throws Exception {
        var req=request();req.setPaymentMethod(vn.iotstar.enums.PaymentMethod.VNPAY);var order=orders.placeOrder(customer.getUserId(),req);
        var step=new java.util.concurrent.atomic.AtomicInteger();
        race(()->{
            if (step.getAndIncrement()==0) simulator.settle(customer.getUserId(),order.getOrderCode(),vn.iotstar.service.PaymentSimulationService.Outcome.SUCCESS);
            else { try { orders.cancelOrder(customer.getUserId(),order.getOrderId(),"Cancel race"); } catch (BadRequestException expected) { return false; } }
            return true;
        });
        var saved=orderRepo.findById(order.getOrderId()).orElseThrow();
        if (saved.getPaymentStatus()==vn.iotstar.enums.PaymentStatus.PAID) {
            assertEquals(vn.iotstar.enums.OrderStatus.NEW,saved.getStatus());assertEquals(9,stock());assertFalse(saved.getStockReleased());
        } else { assertEquals(vn.iotstar.enums.OrderStatus.CANCELLED,saved.getStatus());assertEquals(10,stock()); }
    }
    @Test void failedRestockRollsBackSimulatedFailureRecord() {
        var req=request();req.setPaymentMethod(vn.iotstar.enums.PaymentMethod.VNPAY);var order=orders.placeOrder(customer.getUserId(),req);
        doThrow(new BadRequestException("Simulated warehouse error")).when(stockSpy)
                .adjustStock(anyInt(),anyLong(),intThat(n->n>0),eq(StockMovementType.RETURN),any(),anyString(),anyString());
        assertThrows(BadRequestException.class,()->simulator.settle(customer.getUserId(),order.getOrderCode(),vn.iotstar.service.PaymentSimulationService.Outcome.FAILED));
        assertEquals(vn.iotstar.enums.SimulatedPaymentStatus.PENDING,paymentRepo.findByOrderOrderId(order.getOrderId()).orElseThrow().getStatus());
        assertEquals(vn.iotstar.enums.OrderStatus.NEW,orderRepo.findById(order.getOrderId()).orElseThrow().getStatus());assertEquals(9,stock());
    }
}
