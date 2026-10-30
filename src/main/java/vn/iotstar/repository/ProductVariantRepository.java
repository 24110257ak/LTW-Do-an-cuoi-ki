package vn.iotstar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.iotstar.entity.ProductVariant;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from ProductVariant e where e.variantId = :id")
    Optional<ProductVariant> findForCheckout(@org.springframework.data.repository.query.Param("id") Long id);

    List<ProductVariant> findByProductProductId(Long productId);
    List<ProductVariant> findByProductProductIdAndIsActiveTrue(Long productId);
    Optional<ProductVariant> findBySku(String sku);
    boolean existsBySku(String sku);
}

