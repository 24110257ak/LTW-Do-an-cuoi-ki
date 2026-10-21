package vn.iotstar.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.iotstar.entity.ViewedProduct;

import java.util.List;
import java.util.Optional;

@Repository
public interface ViewedProductRepository extends JpaRepository<ViewedProduct, Long> {
    List<ViewedProduct> findByUserUserIdOrderByViewedAtDesc(Long userId, Pageable pageable);
    Optional<ViewedProduct> findByUserUserIdAndProductProductId(Long userId, Long productId);
    void deleteByUserUserIdAndProductProductId(Long userId, Long productId);
}
