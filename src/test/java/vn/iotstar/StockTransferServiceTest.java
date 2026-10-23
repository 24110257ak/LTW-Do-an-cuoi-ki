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
import vn.iotstar.service.StockTransferService;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class StockTransferServiceTest {

    @Autowired
    private StockTransferService stockTransferService;

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

    private Branch branchSource;
    private Branch branchDest;
    private ProductVariant variant;
    private User manager;

    @BeforeEach
    void setUp() {
        Role role = roleRepository.save(Role.builder().roleName("ADMIN").build());
        manager = userRepository.save(User.builder()
                .username("manager_test")
                .email("manager_test@test.com")
                .password("password123")
                .fullName("Quản Lý Kho Chuỗi")
                .role(role)
                .build());

        branchSource = branchRepository.save(Branch.builder()
                .branchName("Chi nhánh Nguồn (Q.1)")
                .address("123 Lê Lợi")
                .district("Quận 1")
                .phone("0901234567")
                .build());

        branchDest = branchRepository.save(Branch.builder()
                .branchName("Chi nhánh Đích (Q.3)")
                .address("456 CMT8")
                .district("Quận 3")
                .phone("0907654321")
                .build());

        Category cat = categoryRepository.save(Category.builder().categoryName("Laptop").slug("laptop-test").build());
        Brand brand = brandRepository.save(Brand.builder().brandName("Dell").build());
        Product product = productRepository.save(Product.builder()
                .productName("Dell XPS 13")
                .slug("dell-xps-13-test")
                .category(cat)
                .brand(brand)
                .build());

        variant = variantRepository.save(ProductVariant.builder()
                .product(product)
                .sku("DELL-XPS-TEST")
                .price(BigDecimal.valueOf(35000000))
                .build());

        // Nạp sẵn 10 cái vào chi nhánh nguồn
        inventoryService.adjustStock(
                branchSource.getBranchId(),
                variant.getVariantId(),
                10,
                StockMovementType.ADJUSTMENT,
                manager,
                "INIT",
                "Khởi tạo kho nguồn"
        );
    }

    @Test
    @DisplayName("Kiểm tra tạo phiếu điều chuyển thành công với trạng thái PENDING")
    void testCreateTransferSuccess() {
        Map<Long, Integer> items = new HashMap<>();
        items.put(variant.getVariantId(), 4);

        StockTransfer transfer = stockTransferService.createTransfer(
                branchSource.getBranchId(),
                branchDest.getBranchId(),
                items,
                manager,
                "Điều chuyển cân bằng kho"
        );

        assertNotNull(transfer.getTransferId());
        assertTrue(transfer.getTransferCode().startsWith("TRF-"));
        assertEquals(TransferStatus.PENDING, transfer.getStatus());
        assertEquals(1, transfer.getItems().size());
        assertEquals(4, transfer.getItems().get(0).getQuantity());
    }

    @Test
    @DisplayName("Kiểm tra tạo phiếu chuyển kho trùng chi nhánh sẽ bị từ chối")
    void testCreateTransferSameBranch() {
        Map<Long, Integer> items = new HashMap<>();
        items.put(variant.getVariantId(), 2);

        assertThrows(IllegalArgumentException.class, () -> {
            stockTransferService.createTransfer(
                    branchSource.getBranchId(),
                    branchSource.getBranchId(),
                    items,
                    manager,
                    "Lỗi trùng kho"
            );
        });
    }

    @Test
    @DisplayName("Kiểm tra toàn bộ chu kỳ: Tạo -> Duyệt (trừ kho nguồn) -> Vận chuyển -> Hoàn tất (cộng kho đích)")
    void testFullTransferLifecycle() {
        Map<Long, Integer> items = new HashMap<>();
        items.put(variant.getVariantId(), 3);

        // 1. Tạo phiếu
        StockTransfer transfer = stockTransferService.createTransfer(
                branchSource.getBranchId(),
                branchDest.getBranchId(),
                items,
                manager,
                "Chuyển 3 máy Dell"
        );
        assertEquals(TransferStatus.PENDING, transfer.getStatus());
        assertEquals(10, inventoryService.getAvailableQuantity(branchSource.getBranchId(), variant.getVariantId()));

        // 2. Duyệt xuất kho (Trừ tồn kho nguồn)
        stockTransferService.approveTransfer(transfer.getTransferId(), manager);
        StockTransfer approved = stockTransferService.getTransferById(transfer.getTransferId());
        assertEquals(TransferStatus.APPROVED, approved.getStatus());
        assertEquals(7, inventoryService.getAvailableQuantity(branchSource.getBranchId(), variant.getVariantId()));
        assertEquals(0, inventoryService.getAvailableQuantity(branchDest.getBranchId(), variant.getVariantId()));

        // 3. Vận chuyển
        stockTransferService.shipTransfer(transfer.getTransferId(), manager);
        StockTransfer inTransit = stockTransferService.getTransferById(transfer.getTransferId());
        assertEquals(TransferStatus.IN_TRANSIT, inTransit.getStatus());

        // 4. Nhận hàng tại kho đích (Cộng tồn kho đích)
        stockTransferService.completeTransfer(transfer.getTransferId(), manager);
        StockTransfer completed = stockTransferService.getTransferById(transfer.getTransferId());
        assertEquals(TransferStatus.COMPLETED, completed.getStatus());
        assertEquals(7, inventoryService.getAvailableQuantity(branchSource.getBranchId(), variant.getVariantId()));
        assertEquals(3, inventoryService.getAvailableQuantity(branchDest.getBranchId(), variant.getVariantId()));
    }

    @Test
    @DisplayName("Kiểm tra hủy phiếu sau khi đã duyệt xuất kho -> Tự động hoàn lại hàng cho kho nguồn")
    void testCancelTransferRestoresSourceStock() {
        Map<Long, Integer> items = new HashMap<>();
        items.put(variant.getVariantId(), 4);

        StockTransfer transfer = stockTransferService.createTransfer(
                branchSource.getBranchId(),
                branchDest.getBranchId(),
                items,
                manager,
                "Chuyển thử nghiệm để hủy"
        );

        // Duyệt xuất kho (Kho nguồn còn 10 - 4 = 6)
        stockTransferService.approveTransfer(transfer.getTransferId(), manager);
        assertEquals(6, inventoryService.getAvailableQuantity(branchSource.getBranchId(), variant.getVariantId()));

        // Hủy phiếu
        stockTransferService.cancelTransfer(transfer.getTransferId(), manager, "Kế hoạch thay đổi");
        StockTransfer cancelled = stockTransferService.getTransferById(transfer.getTransferId());
        assertEquals(TransferStatus.CANCELLED, cancelled.getStatus());

        // Tồn kho nguồn phải được hoàn lại đủ 10
        assertEquals(10, inventoryService.getAvailableQuantity(branchSource.getBranchId(), variant.getVariantId()));
    }
}
