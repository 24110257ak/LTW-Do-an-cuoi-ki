package vn.iotstar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.iotstar.entity.ProductVariant;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    List<ProductVariant> findByProductProductId(Long productId);
    List<ProductVariant> findByProductProductIdAndIsActiveTrue(Long productId);
    Optional<ProductVariant> findBySku(String sku);
    boolean existsBySku(String sku);
}
