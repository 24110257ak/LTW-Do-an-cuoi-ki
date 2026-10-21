package vn.iotstar.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.iotstar.entity.BranchInventory;
import vn.iotstar.entity.Product;
import vn.iotstar.entity.ProductImage;
import vn.iotstar.entity.ProductVariant;
import vn.iotstar.exception.ResourceNotFoundException;
import vn.iotstar.repository.BranchInventoryRepository;
import vn.iotstar.repository.ProductImageRepository;
import vn.iotstar.repository.ProductRepository;
import vn.iotstar.repository.ProductVariantRepository;
import vn.iotstar.service.ProductService;
import vn.iotstar.util.SlugUtils;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductImageRepository productImageRepository;
    private final BranchInventoryRepository branchInventoryRepository;

    @Override
    public Page<Product> getProductsPaged(Pageable pageable) {
        return productRepository.findAllByIsActiveTrue(pageable);
    }

    @Override
    public Page<Product> getProductsByCategory(Integer categoryId, Pageable pageable) {
        return productRepository.findByCategoryCategoryIdAndIsActiveTrue(categoryId, pageable);
    }

    @Override
    public Page<Product> getProductsByBrand(Integer brandId, Pageable pageable) {
        return productRepository.findByBrandBrandIdAndIsActiveTrue(brandId, pageable);
    }

    @Override
    public Page<Product> searchProducts(String keyword, Pageable pageable) {
        if (keyword == null || keyword.isBlank()) {
            return productRepository.findAllByIsActiveTrue(pageable);
        }
        return productRepository.searchProducts(keyword.trim(), pageable);
    }

    @Override
    public List<Product> getFeaturedProducts() {
        return productRepository.findTop10ByIsFeaturedTrueAndIsActiveTrueOrderByCreatedAtDesc();
    }

    @Override
    public List<Product> getBestSellerProducts() {
        return productRepository.findTop10ByIsActiveTrueOrderByTotalSoldDesc();
    }

    @Override
    public List<Product> getTopRatedProducts() {
        return productRepository.findTop10ByIsActiveTrueOrderByAvgRatingDesc();
    }

    @Override
    public Optional<Product> getProductBySlug(String slug) {
        return productRepository.findBySlug(slug);
    }

    @Override
    public Optional<Product> getProductById(Long id) {
        return productRepository.findById(id);
    }

    @Override
    public List<ProductVariant> getVariantsByProductId(Long productId) {
        return productVariantRepository.findByProductProductId(productId);
    }

    @Override
    public Optional<ProductVariant> getVariantById(Long variantId) {
        return productVariantRepository.findById(variantId);
    }

    @Override
    public List<ProductImage> getImagesByProductId(Long productId) {
        return productImageRepository.findByProductProductIdOrderByDisplayOrderAsc(productId);
    }

    @Override
    public List<BranchInventory> getInventoryForProduct(Long productId) {
        return branchInventoryRepository.findAvailableBranchesForProduct(productId);
    }

    @Override
    public List<BranchInventory> getAvailableBranchesForProduct(Long productId) {
        return branchInventoryRepository.findAvailableBranchesForProduct(productId);
    }

    @Override
    public Integer getTotalStockForVariant(Long variantId) {
        return branchInventoryRepository.sumQuantityByVariantId(variantId);
    }

    // --- Admin methods ---
    @Override
    public Page<Product> getAllProductsPaged(Pageable pageable) {
        return productRepository.findAll(pageable);
    }

    @Override
    public Page<Product> searchProductsAdmin(Integer categoryId, Integer brandId, String keyword, Pageable pageable) {
        String cleanKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        return productRepository.searchProductsAdmin(categoryId, brandId, cleanKeyword, pageable);
    }

    @Override
    @Transactional
    public Product saveProduct(Product product) {
        if (product.getSlug() == null || product.getSlug().isBlank()) {
            product.setSlug(SlugUtils.toSlug(product.getProductName()));
        }
        return productRepository.save(product);
    }

    @Override
    @Transactional
    public void toggleProductStatus(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sản phẩm", "id", id));
        product.setIsActive(product.getIsActive() == null || !product.getIsActive());
        productRepository.save(product);
    }

    @Override
    @Transactional
    public ProductVariant saveVariant(ProductVariant variant) {
        return productVariantRepository.save(variant);
    }

    @Override
    @Transactional
    public void deleteVariant(Long variantId) {
        productVariantRepository.deleteById(variantId);
    }

    @Override
    @Transactional
    public ProductImage saveImage(ProductImage image) {
        return productImageRepository.save(image);
    }

    @Override
    @Transactional
    public void deleteImage(Long imageId) {
        productImageRepository.deleteById(imageId);
    }

    @Override
    public long countActiveProducts() {
        return productRepository.countByIsActiveTrue();
    }
}
