package vn.iotstar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.iotstar.dto.request.CheckoutRequest;
import vn.iotstar.entity.*;
import vn.iotstar.enums.OrderType;
import vn.iotstar.enums.PaymentMethod;
import vn.iotstar.repository.*;
import vn.iotstar.service.CartService;
import vn.iotstar.service.InventoryService;
import vn.iotstar.service.OrderService;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class OrderIdempotencyTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartService cartService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository variantRepository;

    private User customer;
    private Branch branch;
    private ProductVariant variant;

    @BeforeEach
    void setUp() {
        Role role = roleRepository.save(Role.builder().roleName("CUSTOMER").build());
        customer = userRepository.save(User.builder()
                .username("customer_idempotent")
                .email("idempotent@test.com")
                .password("password123")
                .fullName("Khách Hàng Thử Nghiệm")
                .phone("0988776655")
                .role(role)
                .build());

        branch = branchRepository.save(Branch.builder()
                .branchName("Chi nhánh Q.1 Idempotent")
                .address("12 Lê Duẩn")
                .district("Quận 1")
                .phone("0909090909")
                .build());

        Category cat = categoryRepository.save(Category.builder().categoryName("Phụ kiện").slug("phu-kien-test").build());
        Brand brand = brandRepository.save(Brand.builder().brandName("Sony").build());
        Product product = productRepository.save(Product.builder()
                .productName("Tai nghe Sony WH-1000XM5")
                .slug("sony-wh-1000xm5-test")
                .category(cat)
                .brand(brand)
                .build());

        variant = variantRepository.save(ProductVariant.builder()
                .product(product)
                .sku("SONY-XM5-BLACK")
                .price(BigDecimal.valueOf(8000000))
                .build());

        // Nạp sẵn 10 cái vào chi nhánh
        inventoryService.adjustStock(
                branch.getBranchId(),
                variant.getVariantId(),
                10,
                StockMovementType.ADJUSTMENT,
                customer,
                "INIT-SONY",
                "Khởi tạo tồn kho Sony"
        );

        // Thêm vào giỏ hàng 2 cái
        cartService.addToCart(customer.getUserId(), variant.getVariantId(), 2);
    }

    @Test
    @DisplayName("Kiểm tra gửi 2 request cùng checkoutToken -> Chỉ tạo 1 đơn hàng duy nhất và trừ kho đúng 1 lần")
    void testDuplicateCheckoutTokenDoesNotCreateDuplicateOrder() {
        String token = "TEST-TOKEN-UUID-123456789";

        CheckoutRequest request1 = CheckoutRequest.builder()
                .checkoutToken(token)
                .orderType(OrderType.PICKUP)
                .pickupBranchId(branch.getBranchId())
                .paymentMethod(PaymentMethod.COD)
                .receiverName("Khách Hàng Thử Nghiệm")
                .receiverPhone("0988776655")
                .build();

        // Lần click thứ nhất
        Order order1 = orderService.placeOrder(customer.getUserId(), request1);
        assertNotNull(order1);
        assertNotNull(order1.getOrderCode());

        // Tồn kho sau lần 1 phải giảm từ 10 xuống 8
        assertEquals(8, inventoryService.getAvailableQuantity(branch.getBranchId(), variant.getVariantId()));

        // Lần click thứ 2 (double click hoặc retry mạng với cùng token)
        CheckoutRequest request2 = CheckoutRequest.builder()
                .checkoutToken(token)
                .orderType(OrderType.PICKUP)
                .pickupBranchId(branch.getBranchId())
                .paymentMethod(PaymentMethod.COD)
                .receiverName("Khách Hàng Thử Nghiệm")
                .receiverPhone("0988776655")
                .build();

        Order order2 = orderService.placeOrder(customer.getUserId(), request2);
        assertNotNull(order2);

        // Phải trả về chính xác đơn hàng cũ, không sinh mã đơn mới
        assertEquals(order1.getOrderId(), order2.getOrderId());
        assertEquals(order1.getOrderCode(), order2.getOrderCode());

        // Tồn kho vẫn phải là 8 (không bị trừ lần 2)
        assertEquals(8, inventoryService.getAvailableQuantity(branch.getBranchId(), variant.getVariantId()));
    }
}
