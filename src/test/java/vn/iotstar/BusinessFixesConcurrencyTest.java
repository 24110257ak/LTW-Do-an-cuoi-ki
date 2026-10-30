package vn.iotstar;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.iotstar.entity.*;
import vn.iotstar.entity.Order;
import vn.iotstar.enums.*;
import vn.iotstar.exception.BadRequestException;
import vn.iotstar.service.*;
import vn.iotstar.repository.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:business_fixes_race;DB_CLOSE_DELAY=-1;MODE=MSSQLServer")
@ActiveProfiles("test") @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BusinessFixesConcurrencyTest extends CommerceTestSupport {
    @Autowired PlatformTransactionManager manager;
    @Autowired WishlistService wishlist;
    @Autowired WishlistRepository wishes;
    @Autowired OtpService otp;
    @Autowired OtpTokenRepository tokens;
    @Autowired OrderReturnReceiptRepository receipts;
    @Autowired PaymentSimulationService simulator;
    @Autowired PaymentRefundRepository refunds;
    TransactionTemplate tx;
    @BeforeEach void setup(){tx=new TransactionTemplate(manager);tx.execute(s->{fixtures();return null;});}
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    void login(User actor){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,"",actor.getAuthorities()));}
    List<Boolean> race(Callable<Boolean> first,Callable<Boolean> second) throws Exception {
        var pool=Executors.newFixedThreadPool(2);var latch=new CountDownLatch(1);
        try {var a=pool.submit(()->{latch.await();try{return first.call();}finally{SecurityContextHolder.clearContext();}});var b=pool.submit(()->{latch.await();try{return second.call();}finally{SecurityContextHolder.clearContext();}});latch.countDown();return List.of(a.get(25,TimeUnit.SECONDS),b.get(25,TimeUnit.SECONDS));}
        finally{pool.shutdownNow();}
    }
    User driver(String name,Role role){return persist(User.builder().username(name).phone(name.endsWith("a")?"0980000001":"0980000002").email(name+"@example.test").fullName(name).password("test").role(role).branch(source).build());}
    @Test void twoDriversCannotBothClaimTheSameOrder() throws Exception {
        var actors=tx.execute(s->{var role=persist(Role.builder().roleName("SHIPPER").build());return List.of(driver("driver_a",role),driver("driver_b",role));});
        var id=tx.execute(s->{var req=request();req.setOrderType(OrderType.DELIVERY);var order=orders.placeOrder(customer.getUserId(),req);ship(order);return order.getOrderId();});
        var result=race(()->{login(actors.get(0));try{orders.claimOrder(id);return true;}catch(BadRequestException expected){return false;}},()->{login(actors.get(1));try{orders.claimOrder(id);return true;}catch(BadRequestException expected){return false;}});
        assertEquals(1,result.stream().filter(Boolean::booleanValue).count());assertNotNull(orderRepo.findById(id).orElseThrow().getShipper());
    }
    @Test void concurrentLikeRequestsCreateOnlyOneWishlistRow() throws Exception {
        Callable<Boolean> action=()->{login(customer);return wishlist.setLiked(product.getProductId(),true);};
        assertEquals(List.of(true,true),race(action,action));assertEquals(1,wishes.count());
    }
    @Test void simultaneousFirstOtpRequestsCannotBypassCooldown() throws Exception {
        String email="race-"+UUID.randomUUID()+"@example.test";
        Callable<Boolean> action=()->{try{otp.generateAndSendOtp(email,OtpPurpose.REGISTER);return true;}catch(BadRequestException expected){return false;}};
        var result=race(action,action);assertEquals(1,result.stream().filter(Boolean::booleanValue).count());assertEquals(1,tokens.findAll().stream().filter(t->email.equals(t.getEmail())).count());
    }
    @Test void concurrentReturnReceiptsOnlyRestockGoodUnitsOnce() throws Exception {
        var form=tx.execute(s->{carts.updateQuantity(customer.getUserId(),carts.getCartItems(customer.getUserId()).get(0).getCartItemId(),3);var req=request();req.setOrderType(OrderType.DELIVERY);var order=orders.placeOrder(customer.getUserId(),req);ship(order);orders.reportDeliveryFailed(order.getOrderId(),"Failed");orders.updateOrderStatus(order.getOrderId(),OrderStatus.RETURNING,null);return new AbstractMap.SimpleEntry<>(order.getOrderId(),allGood(order));});
        form.getValue().getLines().get(0).setGood(2);form.getValue().getLines().get(0).setDamaged(1);form.getValue().getLines().get(0).setReason("Một máy hỏng");
        Callable<Boolean> action=()->{login(admin);orders.inspectReturn(form.getKey(),form.getValue());return true;};
        assertEquals(List.of(true,true),race(action,action));assertEquals(1,receipts.count());assertEquals(9,stock());
    }
    @Test void simultaneousRefundsCreateOneReceiptAndReleaseStockOnce() throws Exception {
        var id=tx.execute(s->{var req=request();req.setPaymentMethod(PaymentMethod.VNPAY);var order=orders.placeOrder(customer.getUserId(),req);simulator.settle(customer.getUserId(),order.getOrderCode(),PaymentSimulationService.Outcome.SUCCESS);loginAdmin();orders.updateOrderStatus(order.getOrderId(),OrderStatus.CONFIRMED,null);orders.updateOrderStatus(order.getOrderId(),OrderStatus.READY_FOR_PICKUP,null);return order.getOrderId();});
        Callable<Boolean> action=()->{login(admin);orders.refundSimulatedOrder(id,"Khách không nhận");return true;};assertEquals(List.of(true,true),race(action,action));assertEquals(1,refunds.count());assertEquals(10,stock());
    }
}
