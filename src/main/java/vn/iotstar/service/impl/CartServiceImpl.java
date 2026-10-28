package vn.iotstar.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.iotstar.entity.Cart;
import vn.iotstar.entity.CartItem;
import vn.iotstar.entity.ProductVariant;
import vn.iotstar.entity.User;
import vn.iotstar.exception.BadRequestException;
import vn.iotstar.exception.ResourceNotFoundException;
import vn.iotstar.repository.CartItemRepository;
import vn.iotstar.repository.CartRepository;
import vn.iotstar.repository.ProductVariantRepository;
import vn.iotstar.repository.UserRepository;
import vn.iotstar.service.CartService;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public Cart getOrCreateCart(Long userId) {
        return cartRepository.findByUserUserId(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new ResourceNotFoundException("Người dùng", "id", userId));
                    Cart newCart = Cart.builder().user(user).build();
                    return cartRepository.save(newCart);
                });
    }

    @Override
    public List<CartItem> getCartItems(Long userId) {
        return cartRepository.findByUserUserId(userId)
                .map(cart -> cartItemRepository.findByCartCartId(cart.getCartId()))
                .orElse(Collections.emptyList());
    }

    @Override
    @Transactional
    public CartItem addToCart(Long userId, Long variantId, int quantity) {
        if (quantity <= 0) {
            throw new BadRequestException("Số lượng thêm vào giỏ phải lớn hơn 0!");
        }

        Cart cart = getOrCreateCart(userId);
        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Biến thể sản phẩm", "id", variantId));

        if (!Boolean.TRUE.equals(variant.getIsActive())) {
            throw new BadRequestException("Sản phẩm hiện đang tạm ngừng kinh doanh!");
        }

        return cartItemRepository.findByCartCartIdAndVariantVariantId(cart.getCartId(), variantId)
                .map(existingItem -> {
                    existingItem.setQuantity(existingItem.getQuantity() + quantity);
                    return cartItemRepository.save(existingItem);
                })
                .orElseGet(() -> {
                    CartItem newItem = CartItem.builder()
                            .cart(cart)
                            .variant(variant)
                            .quantity(quantity)
                            .build();
                    return cartItemRepository.save(newItem);
                });
    }

    @Override
    @Transactional
    public CartItem updateQuantity(Long userId, Long cartItemId, int quantity) {
        Cart cart = getOrCreateCart(userId);
        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Mục giỏ hàng", "id", cartItemId));

        if (!item.getCart().getCartId().equals(cart.getCartId())) {
            throw new BadRequestException("Bạn không có quyền thao tác trên mục giỏ hàng này!");
        }

        if (quantity <= 0) {
            cartItemRepository.delete(item);
            return null;
        }

        item.setQuantity(quantity);
        return cartItemRepository.save(item);
    }

    @Override
    @Transactional
    public void removeFromCart(Long userId, Long cartItemId) {
        Cart cart = getOrCreateCart(userId);
        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Mục giỏ hàng", "id", cartItemId));

        if (!item.getCart().getCartId().equals(cart.getCartId())) {
            throw new BadRequestException("Bạn không có quyền thao tác trên mục giỏ hàng này!");
        }

        cartItemRepository.delete(item);
    }

    @Override
    @Transactional
    public void clearCart(Long userId) {
        cartRepository.findByUserUserId(userId).ifPresent(cart -> {
            cartItemRepository.deleteByCartCartId(cart.getCartId());
        });
    }

    @Override
    public int getCartItemCount(Long userId) {
        return cartItemRepository.countTotalQuantityByUserId(userId);
    }

    @Override
    public BigDecimal getCartSubtotal(Long userId) {
        List<CartItem> items = getCartItems(userId);
        return items.stream()
                .map(item -> item.getVariant().getEffectivePrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
