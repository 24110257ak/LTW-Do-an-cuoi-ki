package vn.iotstar.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.iotstar.dto.request.CheckoutRequest;
import vn.iotstar.dto.response.CheckoutPricingDto;
import vn.iotstar.entity.*;
import vn.iotstar.enums.*;
import vn.iotstar.exception.BadRequestException;
import vn.iotstar.repository.*;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CheckoutPricingService {
    private final jakarta.persistence.EntityManager entityManager;
    private final CartService cartService;
    private final BranchRepository branches;
    private final BranchInventoryRepository inventory;
    private final DeliveryProviderRepository providers;
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final CouponService coupons;

    public record Quote(List<CartItem> items, Branch branch, DeliveryProvider provider,
                        Coupon coupon, CheckoutPricingDto pricing) {}

    @Transactional(readOnly = true)
    public CheckoutPricingDto preview(Long userId, CheckoutRequest request) {
        return calculate(userId, request, false).pricing();
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Quote prepareOrder(Long userId, CheckoutRequest request) {
        return calculate(userId, request, true);
    }

    private Quote calculate(Long userId, CheckoutRequest request, boolean reserve) {
        if (request == null || request.getOrderType() == null) throw new BadRequestException("Vui lòng chọn hình thức nhận hàng");
        if (request.getPaymentMethod() == null) throw new BadRequestException("Vui lòng chọn phương thức thanh toán");
        var items = new ArrayList<>(itemsForCheckout(userId, request));
        PurchaseLimits.validateItems(items);
        if (items.isEmpty()) throw new BadRequestException("Giỏ hàng của bạn đang trống, không thể thanh toán!");
        items.sort(Comparator.comparing((CartItem i) -> i.getVariant().getProduct().getProductId())
                .thenComparing(i -> i.getVariant().getVariantId()));
        BigDecimal subtotal = BigDecimal.ZERO;
        for (var item : items) {
            var variant = item.getVariant();
            var product = variant.getProduct();
            if (reserve) {
                product = products.findForCheckout(product.getProductId())
                        .orElseThrow(() -> new BadRequestException("Sản phẩm không còn tồn tại"));
                variant = variants.findForCheckout(variant.getVariantId())
                        .orElseThrow(() -> new BadRequestException("Biến thể không còn tồn tại"));
                entityManager.refresh(product);
                entityManager.refresh(variant);
                item.setVariant(variant);
            }
            if (!Boolean.TRUE.equals(product.getIsActive()) || !Boolean.TRUE.equals(variant.getIsActive()))
                throw new BadRequestException("Sản phẩm hoặc phiên bản đã ngừng kinh doanh: " + product.getProductName());
            if (item.getQuantity() == null || item.getQuantity() <= 0 || variant.getEffectivePrice() == null
                    || variant.getEffectivePrice().signum() <= 0 || variant.getEffectivePrice().stripTrailingZeros().scale() > 0 || (variant.getCostPrice() != null && variant.getCostPrice().signum() < 0))
                throw new BadRequestException("Số lượng hoặc giá sản phẩm không hợp lệ");
            subtotal = subtotal.add(variant.getEffectivePrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        Branch branch;
        DeliveryProvider provider = null;
        BigDecimal shipping = BigDecimal.ZERO;
        if (request.getOrderType() == OrderType.PICKUP) {
            if (request.getPickupBranchId() == null) throw new BadRequestException("Vui lòng chọn chi nhánh nhận hàng");
            branch = branches.findById(request.getPickupBranchId())
                    .filter(b -> Boolean.TRUE.equals(b.getIsActive()))
                    .orElseThrow(() -> new BadRequestException("Chi nhánh nhận hàng không tồn tại hoặc đã ngừng hoạt động"));
        } else {
            if (request.getProviderId() != null) provider = activeProvider(request.getProviderId());
            branch = resolveBranch(request.getProvince(), request.getDistrict(), items);
            shipping = shippingFee(request.getProvince(), request.getDistrict(), subtotal, request.getProviderId());
        }
        if (reserve && branch != null) {
            branch = branches.findForCheckout(branch.getBranchId()).orElseThrow(() -> new BadRequestException("Chi nhánh không còn tồn tại"));
            entityManager.refresh(branch);
        }
        if (branch == null || !Boolean.TRUE.equals(branch.getIsActive()))
            throw new BadRequestException("Hiện chưa có chi nhánh phù hợp để xử lý đơn hàng");
        if (!hasStock(branch, items)) throw new BadRequestException("Chi nhánh phục vụ không đủ hàng cho toàn bộ đơn");
        Coupon coupon = null;
        BigDecimal discount = BigDecimal.ZERO;
        if (request.getCouponCode() != null && !request.getCouponCode().isBlank()) {
            coupon = reserve ? coupons.reserveCoupon(request.getCouponCode(), subtotal, branch.getBranchId())
                    : coupons.validateCoupon(request.getCouponCode(), subtotal, branch.getBranchId());
            discount = coupons.calculateDiscount(coupon, subtotal);
        }
        return new Quote(items, branch, provider, coupon,
                new CheckoutPricingDto(subtotal, shipping, discount, subtotal.subtract(discount).add(shipping)));
    }

    @Transactional(readOnly = true)
    public List<CartItem> itemsForCheckout(Long userId, CheckoutRequest request) {
        if (request.getBuyNowVariantId() == null) {
            if (request.getBuyNowQuantity() != null) throw new BadRequestException("Thiếu phiên bản mua ngay");
            return cartService.getCartItems(userId);
        }
        Integer quantity = request.getBuyNowQuantity();
        if (quantity == null || quantity < 1 || quantity > PurchaseLimits.MAX_PER_PRODUCT)
            throw new BadRequestException("Số lượng mua ngay phải từ 1 đến 10");
        var variant = variants.findById(request.getBuyNowVariantId())
                .orElseThrow(() -> new BadRequestException("Phiên bản mua ngay không còn tồn tại"));
        return List.of(CartItem.builder().variant(variant).quantity(quantity).build());
    }

    private boolean hasStock(Branch branch, List<CartItem> items) {
        Map<Long, Long> quantities = new TreeMap<>();
        for (var item : items) quantities.merge(item.getVariant().getVariantId(), item.getQuantity().longValue(), Long::sum);
        return quantities.entrySet().stream().allMatch(e -> inventory
                .findByBranchBranchIdAndVariantVariantId(branch.getBranchId(), e.getKey())
                .map(i -> i.getQuantity() != null && i.getQuantity().longValue() >= e.getValue()).orElse(false));
    }

    public Branch resolveBranch(String province, String district, List<CartItem> items) {
        return branches.findAllByIsActiveTrue().stream().filter(b -> hasStock(b, items))
                .sorted(Comparator.comparingInt((Branch b) -> locality(b, province, district))
                        .thenComparing(Branch::getBranchId))
                .findFirst().orElseThrow(() -> new BadRequestException("Hiện chưa có chi nhánh đủ hàng để xử lý đơn hàng"));
    }

    private int locality(Branch b, String province, String district) {
        boolean sameProvince = province != null && !province.isBlank() && b.getProvince() != null
                && normalizeLocality(province).equals(normalizeLocality(b.getProvince()));
        if (sameProvince && district != null && !district.isBlank() && b.getDistrict() != null
                && normalizeLocality(district).equals(normalizeLocality(b.getDistrict()))) return 0;
        return sameProvince ? 1 : 2;
    }

    private DeliveryProvider activeProvider(Integer id) {
        return providers.findById(id).filter(p -> Boolean.TRUE.equals(p.getIsActive()) && p.getBaseFee() != null && p.getBaseFee().signum() >= 0 && p.getBaseFee().stripTrailingZeros().scale() <= 0)
                .orElseThrow(() -> new BadRequestException("Đơn vị vận chuyển không hợp lệ hoặc đã ngừng hoạt động"));
    }

    public BigDecimal shippingFee(String province, String district, BigDecimal subtotal, Integer providerId) {
        DeliveryProvider provider = providerId == null ? null : activeProvider(providerId);
        if (subtotal != null && subtotal.compareTo(BigDecimal.valueOf(5_000_000)) >= 0) return BigDecimal.ZERO;
        if (provider != null) return provider.getBaseFee();
        if (province == null || province.isBlank()) return BigDecimal.valueOf(30_000);
        if ("hochiminh".equals(normalizeLocality(province))) {
            String dist = normalizeLocality(district);
            if (Set.of("1", "3", "5", "10", "binhthanh", "phunhuan", "govap", "tanbinh", "tanphu", "thuduc").contains(dist))
                return BigDecimal.valueOf(25_000);
            return BigDecimal.valueOf(30_000);
        }
        return BigDecimal.valueOf(40_000);
    }

    // API địa chỉ dùng "Thành phố ...", còn dữ liệu mẫu dùng "TP. ...".
    private String normalizeLocality(String value) {
        if (value == null) return "";
        String normalized = java.text.Normalizer.normalize(value.toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').trim()
                .replaceFirst("^(?:(?:tinh|thanh pho|quan|huyen|phuong|xa|thi xa|thi tran)\\s+|tp\\.?\\s*)", "")
                .replaceAll("[.\\s]", "");
        return Set.of("hcm", "saigon").contains(normalized) ? "hochiminh" : normalized;
    }
}
