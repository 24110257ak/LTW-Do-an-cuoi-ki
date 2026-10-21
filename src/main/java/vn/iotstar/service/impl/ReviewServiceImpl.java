package vn.iotstar.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.iotstar.entity.Order;
import vn.iotstar.entity.OrderItem;
import vn.iotstar.entity.Product;
import vn.iotstar.entity.Review;
import vn.iotstar.entity.User;
import vn.iotstar.enums.OrderStatus;
import vn.iotstar.exception.BadRequestException;
import vn.iotstar.exception.ResourceNotFoundException;
import vn.iotstar.repository.OrderItemRepository;
import vn.iotstar.repository.ProductRepository;
import vn.iotstar.repository.ReviewRepository;
import vn.iotstar.repository.UserRepository;
import vn.iotstar.service.ReviewService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Override
    public Page<Review> getReviewsByProduct(Long productId, Pageable pageable) {
        return reviewRepository.findByProductProductIdAndIsActiveTrueOrderByCreatedAtDesc(productId, pageable);
    }

    @Override
    @Transactional
    public Review addReview(Long userId, Long orderItemId, byte stars, String content, List<String> imageUrls) {
        if (content == null || content.trim().length() < 50) {
            throw new BadRequestException("Nội dung đánh giá phải có độ dài tối thiểu 50 ký tự theo quy định!");
        }
        if (stars < 1 || stars > 5) {
            throw new BadRequestException("Số sao đánh giá phải từ 1 đến 5!");
        }

        if (reviewRepository.existsByOrderItemOrderItemId(orderItemId)) {
            throw new BadRequestException("Sản phẩm trong đơn hàng này đã được bạn đánh giá trước đó!");
        }

        OrderItem orderItem = orderItemRepository.findById(orderItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Mục đơn hàng", "id", orderItemId));

        Order order = orderItem.getOrder();
        if (!order.getUser().getUserId().equals(userId)) {
            throw new BadRequestException("Bạn không phải người mua đơn hàng này!");
        }

        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new BadRequestException("Chỉ có thể đánh giá khi đơn hàng đã được giao thành công (DELIVERED)!");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng", "id", userId));

        Product product = orderItem.getVariant().getProduct();

        String imagesJson = null;
        if (imageUrls != null && !imageUrls.isEmpty()) {
            imagesJson = String.join(";", imageUrls);
        }

        Review review = Review.builder()
                .order(order)
                .orderItem(orderItem)
                .user(user)
                .product(product)
                .stars(stars)
                .content(content.trim())
                .imageUrls(imagesJson)
                .isActive(true)
                .build();

        review = reviewRepository.save(review);

        // Cập nhật lại số lượng đánh giá và điểm sao trung bình của sản phẩm
        int currentCount = product.getReviewCount() != null ? product.getReviewCount() : 0;
        BigDecimal currentAvg = product.getAvgRating() != null ? product.getAvgRating() : BigDecimal.ZERO;

        BigDecimal totalStars = currentAvg.multiply(BigDecimal.valueOf(currentCount)).add(BigDecimal.valueOf(stars));
        int newCount = currentCount + 1;
        BigDecimal newAvg = totalStars.divide(BigDecimal.valueOf(newCount), 2, RoundingMode.HALF_UP);

        product.setReviewCount(newCount);
        product.setAvgRating(newAvg);
        productRepository.save(product);

        return review;
    }

    @Override
    public boolean hasUserReviewedItem(Long orderItemId) {
        return reviewRepository.existsByOrderItemOrderItemId(orderItemId);
    }
}
