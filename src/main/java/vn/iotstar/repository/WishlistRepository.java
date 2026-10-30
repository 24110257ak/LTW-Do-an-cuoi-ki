package vn.iotstar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.iotstar.entity.Wishlist;

import java.util.List;
import java.util.Optional;

@Repository
public interface WishlistRepository extends JpaRepository<Wishlist, Long> {
    List<Wishlist> findByUserUserIdOrderByAddedAtDesc(Long userId);
    boolean existsByUserUserIdAndProductProductId(Long userId, Long productId);
    void deleteByUserUserIdAndProductProductId(Long userId, Long productId);
    Optional<Wishlist> findByUserUserIdAndProductProductId(Long userId, Long productId);
    @org.springframework.data.jpa.repository.Query("select w.product.productId from Wishlist w where w.user.userId=:id")
    List<Long> findLikedProductIds(@org.springframework.data.repository.query.Param("id") Long id);
}

