package vn.iotstar.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.iotstar.entity.Review;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    Page<Review> findByProductProductIdAndIsActiveTrueOrderByCreatedAtDesc(Long productId, Pageable pageable);
    List<Review> findByUserUserIdOrderByCreatedAtDesc(Long userId);
    Optional<Review> findByOrderItemOrderItemId(Long orderItemId);
    boolean existsByOrderItemOrderItemId(Long orderItemId);
}
