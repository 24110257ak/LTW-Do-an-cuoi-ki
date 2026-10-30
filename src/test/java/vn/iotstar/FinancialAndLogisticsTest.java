package vn.iotstar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.iotstar.dto.request.CheckoutRequest;
import vn.iotstar.dto.response.FinancialSummaryDto;
import vn.iotstar.dto.response.TransferSuggestionDto;
import vn.iotstar.entity.*;
import vn.iotstar.enums.OrderStatus;
import vn.iotstar.enums.OrderType;
import vn.iotstar.enums.PaymentMethod;
import vn.iotstar.enums.PaymentStatus;
import vn.iotstar.entity.StockMovementType;
import vn.iotstar.repository.*;
import vn.iotstar.service.CartService;
import vn.iotstar.service.FinancialReportService;
import vn.iotstar.service.InventoryService;
import vn.iotstar.service.OrderService;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class FinancialAndLogisticsTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartService cartService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private FinancialReportService financialReportService;

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

    @Autowired
    private BranchInventoryRepository inventoryRepository;

    @Autowired
    private OrderRepository orderRepository;

    private User customer;
    private User accountant;
    private Branch branchQ1;
    private Branch branchBinhThanh;
    private ProductVariant laptopVariant;

    @BeforeEach
    void setUp() {
        Role roleUser = roleRepository.save(Role.builder().roleName("CUSTOMER").build());
        Role roleStaff = roleRepository.save(Role.builder().roleName("STAFF").build());

        customer = userRepository.save(User.builder()
                .username("customer_logistics")
                .password("hashPass123")
                .fullName("Khách Hàng Thử Nghiệm")
                .email("cust_logistics@example.com")
                .phone("0988776655")
                .role(roleUser)
                .isActive(true)
                .build());

        accountant = userRepository.save(User.builder()
                .username("accountant_user")
                .password("hashPass123")
                .fullName("Kế Toán Kho")
                .email("accountant@example.com")
                .phone("0911223344")
                .role(roleStaff)
                .isActive(true)
                .build());

        branchQ1 = branchRepository.save(Branch.builder()
                .branchName("Chi nhánh Quận 1 - Đồng Khởi")
                .address("123 Đồng Khởi")
                .district("Quận 1")
                .province("TP. Hồ Chí Minh")
                .phone("02838111111")
                .isActive(true)
                .build());

        branchBinhThanh = branchRepository.save(Branch.builder()
                .branchName("Chi nhánh Bình Thạnh - Điện Biên Phủ")
                .address("456 Điện Biên Phủ")
                .district("Quận Bình Thạnh")
                .province("TP. Hồ Chí Minh")
                .phone("02838222222")
                .isActive(true)
                .build());

        Category cat = categoryRepository.save(Category.builder()
                .categoryName("Laptop Gaming")
                .slug("laptop-gaming-" + System.currentTimeMillis())
                .isActive(true)
                .build());

        Brand brand = brandRepository.save(Brand.builder()
                .brandName("ASUS ROG")
                .isActive(true)
                .build());

        Product product = productRepository.save(Product.builder()
                .productName("ROG Zephyrus G16")
                .slug("rog-zephyrus-g16-" + System.currentTimeMillis())
                .category(cat)
                .brand(brand)
                .basePrice(BigDecimal.valueOf(35000000))
                .isActive(true)
                .build());

        // Giá bán = 35.000.000 đ, Giá vốn (COGS) = 28.000.000 đ
        laptopVariant = variantRepository.save(ProductVariant.builder()
                .product(product)
                .color("Eclipse Gray")
                .storage("1TB NVMe")
                .ram("32GB DDR5")
                .sku("ROG-G16-GRY-1TB")
                .price(BigDecimal.valueOf(35000000))
                .costPrice(BigDecimal.valueOf(28000000))
                .isActive(true)
                .build());

        // Cấp phát kho:
        // Branch Q1: có 10 máy, safetyStock = 3
        inventoryService.adjustStock(branchQ1.getBranchId(), laptopVariant.getVariantId(), 10,
                StockMovementType.ADJUSTMENT, accountant, "PO-001", "Nhập kho ban đầu Q1");
        BranchInventory invQ1 = inventoryRepository.findByBranchBranchIdAndVariantVariantId(branchQ1.getBranchId(), laptopVariant.getVariantId()).orElseThrow();
        invQ1.setSafetyStock(3);
        inventoryRepository.save(invQ1);

        // Branch Bình Thạnh: có 1 máy (thiếu hàng dưới ngưỡng an toàn), safetyStock = 3
        inventoryService.adjustStock(branchBinhThanh.getBranchId(), laptopVariant.getVariantId(), 1,
                StockMovementType.ADJUSTMENT, accountant, "PO-002", "Nhập kho ban đầu Bình Thạnh");
        BranchInventory invBT = inventoryRepository.findByBranchBranchIdAndVariantVariantId(branchBinhThanh.getBranchId(), laptopVariant.getVariantId()).orElseThrow();
        invBT.setSafetyStock(3);
        inventoryRepository.save(invBT);
    }

    @Test
    @DisplayName("1. Smart Order Routing: Đơn giao tại Quận 1 tự động định tuyến đến Chi nhánh Quận 1")
    void testSmartOrderRouting() {
        cartService.addToCart(customer.getUserId(), laptopVariant.getVariantId(), 1);

        CheckoutRequest req = CheckoutRequest.builder()
                .orderType(OrderType.DELIVERY)
                .province("TP. Hồ Chí Minh")
                .district("Quận 1")
                .ward("Bến Nghé")
                .street("Số 1 Công Xã Paris")
                .receiverName(customer.getFullName())
                .receiverPhone(customer.getPhone())
                .paymentMethod(PaymentMethod.COD)
                .build();

        Order order = orderService.placeOrder(customer.getUserId(), req);

        assertNotNull(order);
        assertEquals(branchQ1.getBranchId(), order.getBranch().getBranchId(),
                "Hệ thống định tuyến thông minh phải gán đơn cho chi nhánh cùng Quận 1 có hàng");
    }

    @Test
    @DisplayName("2. Quản trị Giá vốn (COGS) & Lợi nhuận gộp: Snapshot chính xác và tính lợi nhuận đúng")
    void testCogsAndGrossProfitCalculation() {
        // Mua 2 chiếc ROG Zephyrus G16
        cartService.addToCart(customer.getUserId(), laptopVariant.getVariantId(), 2);

        CheckoutRequest req = CheckoutRequest.builder()
                .orderType(OrderType.DELIVERY)
                .province("TP. Hồ Chí Minh")
                .district("Quận 1")
                .paymentMethod(PaymentMethod.COD)
                .build();

        Order order = orderService.placeOrder(customer.getUserId(), req);

        // Doanh thu = 2 * 35.000.000 = 70.000.000 đ
        // Tổng giá vốn (COGS) = 2 * 28.000.000 = 56.000.000 đ
        // Lợi nhuận gộp = 70.000.000 - 56.000.000 = 14.000.000 đ
        assertEquals(0, BigDecimal.valueOf(70000000).compareTo(order.getSubtotal()));
        assertEquals(0, BigDecimal.valueOf(56000000).compareTo(order.getTotalCost()));
        assertEquals(0, BigDecimal.valueOf(14000000).compareTo(order.getGrossProfit()));

        // Kiểm tra snapshot giá vốn trên OrderItem
        assertEquals(1, order.getOrderItems().size());
        OrderItem item = order.getOrderItems().get(0);
        assertEquals(0, BigDecimal.valueOf(28000000).compareTo(item.getCostPrice()));
        assertEquals(0, BigDecimal.valueOf(14000000).compareTo(item.getLineProfit()));

        assertEquals(0, financialReportService.getFinancialOverview().getTotalRevenue().signum());
        startShipping(order);
        orderService.confirmDelivery(order.getOrderId(),order.getTotalAmount(),"Người nhận","Delivered");

        // Báo cáo tài chính tổng quan
        FinancialSummaryDto summary = financialReportService.getFinancialOverview();
        assertNotNull(summary);
        assertTrue(summary.getTotalGrossProfit().compareTo(BigDecimal.ZERO) > 0);
        assertEquals(20.0, summary.getGrossProfitMarginPercent(), 0.1); // Biên lợi nhuận: 14M / 70M = 20.0%
    }

    @Test
    @DisplayName("3. Vận hành COD: Giao hàng thành công -> Đối soát nộp quỹ ngân quỹ")
    void testCodCashReconciliationWorkflow() {
        cartService.addToCart(customer.getUserId(), laptopVariant.getVariantId(), 1);

        CheckoutRequest req = CheckoutRequest.builder()
                .orderType(OrderType.DELIVERY)
                .province("TP. Hồ Chí Minh")
                .district("Quận 1")
                .paymentMethod(PaymentMethod.COD)
                .build();

        Order order = orderService.placeOrder(customer.getUserId(), req);
        assertFalse(order.getCodCollected());
        assertFalse(order.getCodReconciled());

        startShipping(order);
        // Shipper giao hàng thành công và thu tiền mặt
        Order deliveredOrder = orderService.confirmDelivery(order.getOrderId(),order.getTotalAmount(),"Người nhận","Khách đã trả đủ tiền mặt");
        assertTrue(deliveredOrder.getCodCollected());
        assertEquals(PaymentStatus.PAID, deliveredOrder.getPaymentStatus());
        assertFalse(deliveredOrder.getCodReconciled());

        // Kế toán đối soát tiền COD nộp về quỹ chi nhánh
        Order reconciledOrder = orderService.reconcileCodPayment(deliveredOrder.getOrderId(), accountant.getUserId(), "TXN-COD-9988");
        assertTrue(reconciledOrder.getCodReconciled());
        assertNotNull(reconciledOrder.getCodReconciledAt());
        assertEquals("TXN-COD-9988", reconciledOrder.getCodTransactionId());
    }

    @Test
    @DisplayName("4. Logistics ngược (Reverse Logistics Restock Guard): Giao thất bại chưa hoàn kho, chỉ hoàn khi về kho")
    void testReverseLogisticsRestockGuard() {
        int initialStockQ1 = inventoryRepository.findByBranchBranchIdAndVariantVariantId(branchQ1.getBranchId(), laptopVariant.getVariantId())
                .orElseThrow().getQuantity();

        cartService.addToCart(customer.getUserId(), laptopVariant.getVariantId(), 1);

        CheckoutRequest req = CheckoutRequest.builder()
                .orderType(OrderType.DELIVERY)
                .province("TP. Hồ Chí Minh")
                .district("Quận 1")
                .paymentMethod(PaymentMethod.COD)
                .build();

        Order order = orderService.placeOrder(customer.getUserId(), req);

        // Sau khi đặt, tồn kho đã trừ 1
        int stockAfterOrder = inventoryRepository.findByBranchBranchIdAndVariantVariantId(branchQ1.getBranchId(), laptopVariant.getVariantId())
                .orElseThrow().getQuantity();
        assertEquals(initialStockQ1 - 1, stockAfterOrder);

        startShipping(order);
        // Shipper báo giao thất bại (khách đi vắng) -> Không được tăng tồn kho vật lý!
        Order failedOrder = orderService.reportDeliveryFailed(order.getOrderId(), "Khách đi công tác chưa nhận được");
        assertEquals(OrderStatus.DELIVERY_FAILED, failedOrder.getStatus());

        int stockAfterFailed = inventoryRepository.findByBranchBranchIdAndVariantVariantId(branchQ1.getBranchId(), laptopVariant.getVariantId())
                .orElseThrow().getQuantity();
        assertEquals(stockAfterOrder, stockAfterFailed, "Hàng còn trên xe shipper, không được tự ý hoàn kho");

        move(order,OrderStatus.RETURNING);
        // Hàng quay về chi nhánh và thủ kho kiểm tra nhập lại kho
        Order returnedOrder = orderService.inspectReturn(order.getOrderId(),allGood(order));
        assertEquals(OrderStatus.RETURNED, returnedOrder.getStatus());

        int stockAfterReturn = inventoryRepository.findByBranchBranchIdAndVariantVariantId(branchQ1.getBranchId(), laptopVariant.getVariantId())
                .orElseThrow().getQuantity();
        assertEquals(initialStockQ1, stockAfterReturn, "Thủ kho xác nhận nhập hoàn thì tồn kho phải phục hồi nguyên vẹn");
    }

    @Test
    @DisplayName("5. Đề xuất điều chuyển kho thông minh: Cảnh báo chi nhánh thiếu hàng và gợi ý kho dôi dư")
    void testSmartTransferSuggestions() {
        // Chi nhánh Bình Thạnh hiện chỉ có 1 sp laptopVariant (dưới safetyStock = 3)
        // Chi nhánh Quận 1 có 10 sp (dôi dư vượt safetyStock = 3)
        List<TransferSuggestionDto> suggestions = financialReportService.generateTransferSuggestions();

        assertNotNull(suggestions);
        assertFalse(suggestions.isEmpty(), "Hệ thống phải sinh đề xuất điều chuyển để cứu kho thiếu hàng");

        TransferSuggestionDto suggestion = suggestions.stream()
                .filter(s -> s.getTargetBranchId().equals(branchBinhThanh.getBranchId()))
                .findFirst()
                .orElse(null);

        assertNotNull(suggestion);
        assertEquals(branchQ1.getBranchId(), suggestion.getSourceBranchId(), "Nguồn chuyển phải là kho Quận 1 có dôi dư hàng");
        assertTrue(suggestion.getSuggestedQuantity() > 0);
    }

    private void move(Order order, OrderStatus next) {
        accountant.setBranch(branchQ1);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(accountant,"",accountant.getAuthorities()));
        orderService.updateOrderStatus(order.getOrderId(),next,null);
    }
    private void startShipping(Order order) { move(order,OrderStatus.CONFIRMED); move(order,OrderStatus.SHIPPING); }
    @org.junit.jupiter.api.AfterEach void clearOperator(){org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    private vn.iotstar.dto.request.ReturnInspectionForm allGood(Order order) {
        var form=new vn.iotstar.dto.request.ReturnInspectionForm();
        for(var item:order.getOrderItems()){var line=new vn.iotstar.dto.request.ReturnInspectionForm.Line();line.setOrderItemId(item.getOrderItemId());line.setGood(item.getQuantity());line.setDamaged(0);line.setMissing(0);form.getLines().add(line);}return form;
    }
}
