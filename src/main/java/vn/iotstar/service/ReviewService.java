package vn.iotstar.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vn.iotstar.entity.Review;

import java.util.List;

public interface ReviewService {
    Page<Review> getReviewsByProduct(Long productId, Pageable pageable);
    Review addReview(Long userId, Long orderItemId, byte stars, String content, List<String> imageUrls);
    boolean hasUserReviewedItem(Long orderItemId);
}
