package vn.iotstar.controller.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.iotstar.dto.response.ApiResponse;
import vn.iotstar.entity.Category;
import vn.iotstar.exception.ResourceNotFoundException;
import vn.iotstar.service.CategoryService;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Tag(name = "Danh mục sản phẩm (Category API)", description = "Các API truy vấn danh mục mở công khai và quản trị phân cấp danh mục")
public class CategoryApiController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "Lấy danh sách tất cả danh mục đang hoạt động", description = "Công khai, không yêu cầu đăng nhập")
    public ResponseEntity<ApiResponse<List<Category>>> getAllActiveCategories() {
        List<Category> categories = categoryService.getAllActiveCategories();
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh mục thành công", categories));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết danh mục theo ID", description = "Công khai, không yêu cầu đăng nhập")
    public ResponseEntity<ApiResponse<Category>> getCategoryById(@PathVariable Integer id) {
        Category category = categoryService.getCategoryById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Danh mục", "id", id));
        return ResponseEntity.ok(ApiResponse.ok("Lấy thông tin danh mục thành công", category));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Tạo mới danh mục sản phẩm (Yêu cầu ADMIN)", description = "Yêu cầu Bearer JWT với quyền ROLE_ADMIN")
    public ResponseEntity<ApiResponse<Category>> createCategory(@RequestBody Category category) {
        Category created = categoryService.saveCategory(category);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Tạo danh mục mới thành công", created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Cập nhật danh mục sản phẩm (Yêu cầu ADMIN)", description = "Yêu cầu Bearer JWT với quyền ROLE_ADMIN")
    public ResponseEntity<ApiResponse<Category>> updateCategory(@PathVariable Integer id, @RequestBody Category request) {
        Category existing = categoryService.getCategoryById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Danh mục", "id", id));

        existing.setCategoryName(request.getCategoryName());
        existing.setSlug(request.getSlug());
        existing.setImageUrl(request.getImageUrl());
        existing.setDisplayOrder(request.getDisplayOrder());
        existing.setIsActive(request.getIsActive());

        Category updated = categoryService.saveCategory(existing);
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật danh mục thành công", updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(summary = "Bật/tắt trạng thái hoạt động danh mục (Yêu cầu ADMIN)", description = "Yêu cầu Bearer JWT với quyền ROLE_ADMIN")
    public ResponseEntity<ApiResponse<Void>> toggleStatus(@PathVariable Integer id) {
        categoryService.toggleCategoryStatus(id);
        return ResponseEntity.ok(ApiResponse.ok("Thay đổi trạng thái danh mục thành công", null));
    }
}
