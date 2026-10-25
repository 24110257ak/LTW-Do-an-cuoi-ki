package vn.iotstar.controller.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.iotstar.dto.response.ApiResponse;
import vn.iotstar.entity.Product;
import vn.iotstar.exception.ResourceNotFoundException;
import vn.iotstar.service.ProductService;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Sản phẩm & Biến thể (Product API)", description = "Các API tra cứu sản phẩm, tìm kiếm phân trang và quản lý danh mục sản phẩm")
public class ProductApiController {

    private final ProductService productService;

    @GetMapping
    @Operation(summary = "Tìm kiếm & phân trang danh sách sản phẩm", description = "Hỗ trợ lọc theo từ khóa, danh mục, thương hiệu và sắp xếp")
    public ResponseEntity<ApiResponse<Page<Product>>> getProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) Integer brandId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction
    ) {
        Sort sort = "asc".equalsIgnoreCase(direction) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Product> result = productService.filterCustomerProducts(categoryId, brandId, keyword, pageable);

        return ResponseEntity.ok(ApiResponse.ok("Tra cứu sản phẩm thành công", result));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy chi tiết sản phẩm theo ID", description = "Trả về thông tin chi tiết, biến thể, hình ảnh sản phẩm")
    public ResponseEntity<ApiResponse<Product>> getProductById(@PathVariable Long id) {
        Product product = productService.getProductById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sản phẩm", "id", id));
        return ResponseEntity.ok(ApiResponse.ok("Lấy chi tiết sản phẩm thành công", product));
    }

    @GetMapping("/slug/{slug}")
    @Operation(summary = "Lấy chi tiết sản phẩm theo Slug đường dẫn", description = "Công khai, tối ưu SEO URL")
    public ResponseEntity<ApiResponse<Product>> getProductBySlug(@PathVariable String slug) {
        Product product = productService.getProductBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Sản phẩm", "slug", slug));
        return ResponseEntity.ok(ApiResponse.ok("Lấy chi tiết sản phẩm thành công", product));
    }

    @GetMapping("/featured")
    @Operation(summary = "Lấy danh sách sản phẩm nổi bật", description = "Danh sách sản phẩm được đánh dấu nổi bật trên trang chủ")
    public ResponseEntity<ApiResponse<List<Product>>> getFeaturedProducts() {
        List<Product> products = productService.getFeaturedProducts();
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách sản phẩm nổi bật thành công", products));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Tạo mới sản phẩm (Yêu cầu ADMIN)", description = "Yêu cầu Bearer JWT với quyền ROLE_ADMIN")
    public ResponseEntity<ApiResponse<Product>> createProduct(@RequestBody Product product) {
        Product created = productService.saveProduct(product);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Tạo sản phẩm mới thành công", created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Cập nhật sản phẩm (Yêu cầu ADMIN)", description = "Yêu cầu Bearer JWT với quyền ROLE_ADMIN")
    public ResponseEntity<ApiResponse<Product>> updateProduct(@PathVariable Long id, @RequestBody Product request) {
        Product existing = productService.getProductById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sản phẩm", "id", id));

        existing.setProductName(request.getProductName());
        existing.setSlug(request.getSlug());
        existing.setDescription(request.getDescription());
        existing.setSpecifications(request.getSpecifications());
        existing.setBasePrice(request.getBasePrice());
        existing.setCategory(request.getCategory());
        existing.setBrand(request.getBrand());
        existing.setIsFeatured(request.getIsFeatured());
        existing.setIsActive(request.getIsActive());

        Product updated = productService.saveProduct(existing);
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật sản phẩm thành công", updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Bật/tắt trạng thái kinh doanh của sản phẩm (Yêu cầu ADMIN)", description = "Yêu cầu Bearer JWT với quyền ROLE_ADMIN")
    public ResponseEntity<ApiResponse<Void>> toggleProductStatus(@PathVariable Long id) {
        productService.toggleProductStatus(id);
        return ResponseEntity.ok(ApiResponse.ok("Thay đổi trạng thái sản phẩm thành công", null));
    }
}
