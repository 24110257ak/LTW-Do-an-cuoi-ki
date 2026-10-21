package vn.iotstar.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vn.iotstar.entity.BranchInventory;
import vn.iotstar.entity.Product;
import vn.iotstar.entity.ProductImage;
import vn.iotstar.entity.ProductVariant;

import java.util.List;
import java.util.Optional;

public interface ProductService {
    Page<Product> getProductsPaged(Pageable pageable);
    Page<Product> getProductsByCategory(Integer categoryId, Pageable pageable);
    Page<Product> getProductsByBrand(Integer brandId, Pageable pageable);
    Page<Product> searchProducts(String keyword, Pageable pageable);

    List<Product> getFeaturedProducts();
    List<Product> getBestSellerProducts();
    List<Product> getTopRatedProducts();

    Optional<Product> getProductBySlug(String slug);
    Optional<Product> getProductById(Long id);

    List<ProductVariant> getVariantsByProductId(Long productId);
    Optional<ProductVariant> getVariantById(Long variantId);
    List<ProductImage> getImagesByProductId(Long productId);

    List<BranchInventory> getInventoryForProduct(Long productId);
    List<BranchInventory> getAvailableBranchesForProduct(Long productId);
    Integer getTotalStockForVariant(Long variantId);
}
