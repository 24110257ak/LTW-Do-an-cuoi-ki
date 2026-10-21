package vn.iotstar.service;

import vn.iotstar.entity.Cart;
import vn.iotstar.entity.CartItem;

import java.math.BigDecimal;
import java.util.List;

public interface CartService {
    Cart getOrCreateCart(Long userId);
    List<CartItem> getCartItems(Long userId);
    CartItem addToCart(Long userId, Long variantId, int quantity);
    CartItem updateQuantity(Long userId, Long cartItemId, int quantity);
    void removeFromCart(Long userId, Long cartItemId);
    void clearCart(Long userId);
    int getCartItemCount(Long userId);
    BigDecimal getCartSubtotal(Long userId);
}
