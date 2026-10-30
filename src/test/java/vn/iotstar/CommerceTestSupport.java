package vn.iotstar;

import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.iotstar.dto.request.CheckoutRequest;
import vn.iotstar.entity.*;
import vn.iotstar.enums.*;
import vn.iotstar.repository.*;
import vn.iotstar.service.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

abstract class CommerceTestSupport {
    @Autowired EntityManager em;
    @Autowired OrderService orders;
    @Autowired CartService carts;
    @Autowired CouponService coupons;
    @Autowired CheckoutPricingService pricing;
    @Autowired InventoryService inventory;
    @Autowired StockTransferService transfers;
    @Autowired OrderRepository orderRepo;
    @Autowired CouponRepository couponRepo;
    @Autowired BranchInventoryRepository stockRepo;
    @Autowired FinancialReportService reports;
    User customer, admin;
    Branch source, destination;
    Product product;
    ProductVariant variant;

    void fixtures() {
        var cr = persist(Role.builder().roleName("CUSTOMER").build());
        var ar = persist(Role.builder().roleName("ADMIN").build());
        customer = persist(User.builder().username("commerce_customer").email("commerce@example.test").fullName("Customer")
                .password("test").phone("0901000001").role(cr).isVerified(true).build());
        admin = persist(User.builder().username("commerce_admin").email("commerce-admin@example.test").fullName("Admin")
                .password("test").phone("0901000002").role(ar).isVerified(true).build());
        source = persist(Branch.builder().branchName("Commerce A").address("A").district("Quận 1").province("TP. Hồ Chí Minh").phone("1").build());
        destination = persist(Branch.builder().branchName("Commerce B").address("B").district("Quận 3").province("TP. Hồ Chí Minh").phone("2").build());
        var category = persist(Category.builder().categoryName("Commerce").slug("commerce").build());
        var brand = persist(Brand.builder().brandName("Commerce").build());
        product = persist(Product.builder().productName("Commerce product").slug("commerce-product").category(category).brand(brand).build());
        variant = persist(ProductVariant.builder().product(product).sku("COMMERCE-1").price(new BigDecimal("2000000")).costPrice(new BigDecimal("1000000")).build());
        inventory.adjustStock(source.getBranchId(),variant.getVariantId(),10,StockMovementType.ADJUSTMENT,admin,"INIT","Initial stock");
        carts.addToCart(customer.getUserId(),variant.getVariantId(),1);
    }

    <T> T persist(T entity) { em.persist(entity); return entity; }
    CheckoutRequest request() {
        return CheckoutRequest.builder().orderType(OrderType.PICKUP).pickupBranchId(source.getBranchId()).paymentMethod(PaymentMethod.COD).build();
    }
    Coupon coupon(String code, int limit) {
        return coupons.saveCoupon(Coupon.builder().couponCode(code).discountType(DiscountType.AMOUNT).discountValue(new BigDecimal("100000"))
                .usageLimit(limit).validFrom(LocalDateTime.now().minusDays(1)).validUntil(LocalDateTime.now().plusDays(2)).build());
    }
    void loginAdmin() { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(admin,"",admin.getAuthorities())); }
    void ship(Order order) {
        loginAdmin();
        orders.updateOrderStatus(order.getOrderId(),OrderStatus.CONFIRMED,null);
        orders.updateOrderStatus(order.getOrderId(),OrderStatus.SHIPPING,null);
    }
    int stock() { return inventory.getAvailableQuantity(source.getBranchId(),variant.getVariantId()); }
    vn.iotstar.dto.request.ReturnInspectionForm allGood(Order order) {
        var form=new vn.iotstar.dto.request.ReturnInspectionForm();
        for(var item:order.getOrderItems()) {var line=new vn.iotstar.dto.request.ReturnInspectionForm.Line();line.setOrderItemId(item.getOrderItemId());line.setGood(item.getQuantity());line.setDamaged(0);line.setMissing(0);form.getLines().add(line);}return form;
    }
}
