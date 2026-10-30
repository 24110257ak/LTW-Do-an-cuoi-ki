package vn.iotstar.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.iotstar.entity.Product;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from Product e where e.productId = :id")
    Optional<Product> findForCheckout(@org.springframework.data.repository.query.Param("id") Long id);

    Optional<Product> findBySlug(String slug);
    Page<Product> findAllByIsActiveTrue(Pageable pageable);
    Page<Product> findByCategoryCategoryIdAndIsActiveTrue(Integer categoryId, Pageable pageable);
    Page<Product> findByBrandBrandIdAndIsActiveTrue(Integer brandId, Pageable pageable);
    List<Product> findTop10ByIsFeaturedTrueAndIsActiveTrueOrderByCreatedAtDesc();
    List<Product> findTop10ByIsActiveTrueOrderByTotalSoldDesc();
    List<Product> findTop10ByIsActiveTrueOrderByAvgRatingDesc();

    @Query("SELECT p FROM Product p WHERE p.isActive = true AND " +
           "(LOWER(p.productName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Product> searchProducts(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.isActive = true AND " +
           "(:hasCategories = false OR p.category.categoryId IN :categoryIds) AND " +
           "(:brandId IS NULL OR p.brand.brandId = :brandId) AND " +
           "(:keyword IS NULL OR LOWER(p.productName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Product> filterCustomerProducts(
            @Param("hasCategories") boolean hasCategories,
            @Param("categoryIds") List<Integer> categoryIds,
            @Param("brandId") Integer brandId,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Query("SELECT p FROM Product p WHERE " +
           "(:categoryId IS NULL OR p.category.categoryId = :categoryId OR (p.category.parent IS NOT NULL AND p.category.parent.categoryId = :categoryId)) AND " +
           "(:brandId IS NULL OR p.brand.brandId = :brandId) AND " +
           "(:keyword IS NULL OR LOWER(p.productName) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Product> searchProductsAdmin(
            @Param("categoryId") Integer categoryId,
            @Param("brandId") Integer brandId,
            @Param("keyword") String keyword,
            Pageable pageable);

    boolean existsBySlug(String slug);
    long countByIsActiveTrue();
}
