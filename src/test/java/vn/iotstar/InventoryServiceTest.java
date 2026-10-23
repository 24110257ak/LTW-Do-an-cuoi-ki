package vn.iotstar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.iotstar.entity.*;
import vn.iotstar.repository.*;
import vn.iotstar.service.InventoryService;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class InventoryServiceTest {

    @Autowired
    private InventoryService inventoryService;

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

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private StockLogRepository stockLogRepository;

    private Branch branch;
    private ProductVariant variant;
    private User testUser;

    @BeforeEach
    void setUp() {
        Role role = roleRepository.save(Role.builder().roleName("ADMIN").build());
        testUser = userRepository.save(User.builder()
                .username("testadmin")
                .email("testadmin@test.com")
                .password("password123")
                .fullName("Admin Test")
                .role(role)
                .build());

        branch = branchRepository.save(Branch.builder()
                .branchName("Chi nhánh Test Q.1")
                .address("123 Lê Lợi")
                .district("Quận 1")
                .province("TP. Hồ Chí Minh")
                .phone("0901234567")
                .build());

        Category category = categoryRepository.save(Category.builder()
                .categoryName("Điện thoại")
                .slug("dien-thoai-test")
                .build());

        Brand brand = brandRepository.save(Brand.builder()
                .brandName("Apple")
                .build());

        Product product = productRepository.save(Product.builder()
                .productName("iPhone 16 Pro")
                .slug("iphone-16-pro-test")
                .category(category)
                .brand(brand)
                .build());

        variant = variantRepository.save(ProductVariant.builder()
                .product(product)
                .sku("IPHONE16P-TEST")
                .color("Titan Sa Mạc")
                .price(BigDecimal.valueOf(28000000))
                .build());
    }

    @Test
    @DisplayName("Kiểm tra nhập kho tăng số lượng và ghi sổ nhật ký StockLog")
    void testAdjustStockPositive() {
        inventoryService.adjustStock(
                branch.getBranchId(),
                variant.getVariantId(),
                20,
                StockMovementType.TRANSFER_IN,
                testUser,
                "REF-001",
                "Nhập kho ban đầu"
        );

        int available = inventoryService.getAvailableQuantity(branch.getBranchId(), variant.getVariantId());
        assertEquals(20, available);

        List<StockLog> logs = stockLogRepository.findByBranchBranchIdOrderByCreatedAtDesc(branch.getBranchId());
        assertFalse(logs.isEmpty());
        assertEquals(20, logs.get(0).getQuantityChange());
        assertEquals(20, logs.get(0).getQuantityAfter());
        assertEquals(StockMovementType.TRANSFER_IN, logs.get(0).getMovementType());
        assertEquals("REF-001", logs.get(0).getReferenceCode());
    }

    @Test
    @DisplayName("Kiểm tra xuất kho giảm số lượng và ghi nhận đúng tồn sau")
    void testAdjustStockNegative() {
        // Nhập trước 15
        inventoryService.adjustStock(branch.getBranchId(), variant.getVariantId(), 15,
                StockMovementType.TRANSFER_IN, testUser, "REF-IN", "Nhập");

        // Xuất bán 5
        inventoryService.adjustStock(branch.getBranchId(), variant.getVariantId(), -5,
                StockMovementType.SALE, testUser, "ORDER-001", "Bán hàng");

        int available = inventoryService.getAvailableQuantity(branch.getBranchId(), variant.getVariantId());
        assertEquals(10, available);

        List<StockLog> logs = stockLogRepository.findByBranchBranchIdOrderByCreatedAtDesc(branch.getBranchId());
        assertEquals(2, logs.size());
        assertEquals(-5, logs.get(0).getQuantityChange());
        assertEquals(10, logs.get(0).getQuantityAfter());
        assertEquals(StockMovementType.SALE, logs.get(0).getMovementType());
    }

    @Test
    @DisplayName("Kiểm tra xuất vượt tồn kho sẽ ném lỗi IllegalStateException")
    void testAdjustStockInsufficient() {
        inventoryService.adjustStock(branch.getBranchId(), variant.getVariantId(), 5,
                StockMovementType.TRANSFER_IN, testUser, "REF-IN", "Nhập 5 cái");

        assertThrows(IllegalStateException.class, () -> {
            inventoryService.adjustStock(branch.getBranchId(), variant.getVariantId(), -10,
                    StockMovementType.SALE, testUser, "ORDER-OVER", "Bán 10 cái");
        });
    }

    @Test
    @DisplayName("Kiểm tra kiểm kê điều chỉnh admin bắt buộc có lý do")
    void testAdminAdjustRequiresReason() {
        assertThrows(IllegalArgumentException.class, () -> {
            inventoryService.adminAdjustStock(branch.getBranchId(), variant.getVariantId(), 5, "", testUser);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            inventoryService.adminAdjustStock(branch.getBranchId(), variant.getVariantId(), 5, null, testUser);
        });
    }
}
