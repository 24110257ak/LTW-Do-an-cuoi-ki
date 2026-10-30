package vn.iotstar.service;

import vn.iotstar.entity.CartItem;
import vn.iotstar.exception.BadRequestException;
import java.util.*;

public final class PurchaseLimits {
    public static final int MAX_PER_PRODUCT = 10;
    private PurchaseLimits() {}

    public static int remaining(long inCart) { return (int)Math.max(0, MAX_PER_PRODUCT - inCart); }
    public static String cartMessage(long inCart) {
        return "Giỏ hàng hiện đã có " + inCart + " sản phẩm này (cộng các phiên bản). Bạn chỉ có thể thêm tối đa "
                + remaining(inCart) + " sản phẩm; giới hạn " + MAX_PER_PRODUCT + " sản phẩm cho mỗi giỏ/đơn.";
    }
    public static void validateItems(List<CartItem> items) {
        Map<Long, Long> totals = new HashMap<>();
        for (var item : items) {
            if (item.getQuantity() == null || item.getQuantity() < 1 || item.getQuantity() > MAX_PER_PRODUCT)
                throw new BadRequestException("Mỗi sản phẩm chỉ được đặt từ 1 đến " + MAX_PER_PRODUCT + " cái.");
            totals.merge(item.getVariant().getProduct().getProductId(), item.getQuantity().longValue(), Long::sum);
        }
        if (totals.values().stream().anyMatch(n -> n > MAX_PER_PRODUCT))
            throw new BadRequestException("Mỗi sản phẩm được đặt tối đa " + MAX_PER_PRODUCT + " cái, cộng các phiên bản.");
    }
}
