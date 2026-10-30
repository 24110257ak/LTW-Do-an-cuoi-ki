package vn.iotstar.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import vn.iotstar.entity.Product;
import vn.iotstar.exception.ResourceNotFoundException;
import vn.iotstar.service.*;

@Controller
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final CartService cartService;
    private final CategoryService categoryService;
    private final BrandService brandService;
    private final BranchService branchService;
    private final ReviewService reviewService;

    @GetMapping("/products")
    public String listProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) Integer brandId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            Model model
    ) {
        Sort sort = "asc".equalsIgnoreCase(direction) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Product> productPage = productService.filterCustomerProducts(categoryId, brandId, keyword, pageable);

        model.addAttribute("productPage", productPage);
        model.addAttribute("rootCategories", categoryService.getRootCategories());
        model.addAttribute("categories", categoryService.getAllActiveCategories());
        model.addAttribute("brands", brandService.getAllActiveBrands());
        model.addAttribute("branches", branchService.getAllActiveBranches());
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("selectedBrandId", brandId);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("direction", direction);
        model.addAttribute("currentPage", page);
        model.addAttribute("pageSize", size);

        if (categoryId != null) {
            categoryService.getCategoryById(categoryId).ifPresent(c -> model.addAttribute("selectedCategory", c));
        }
        if (brandId != null) {
            brandService.getBrandById(brandId).ifPresent(b -> model.addAttribute("selectedBrand", b));
        }

        return "customer/product-list";
    }

    @GetMapping("/products/{slug}")
    public String productDetail(@PathVariable String slug, Model model) {
        Product product = productService.getProductBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Sản phẩm", "slug", slug));

        model.addAttribute("product", product);
        var currentUser = vn.iotstar.util.SecurityUtils.getCurrentUser();
        long inCart = currentUser.map(u -> cartService.getCartItems(u.getUserId()).stream()
                .filter(i -> i.getVariant().getProduct().getProductId().equals(product.getProductId()))
                .mapToLong(vn.iotstar.entity.CartItem::getQuantity).sum()).orElse(0L);
        model.addAttribute("inCartQuantity",inCart);
        model.addAttribute("maxAdditionalQuantity",PurchaseLimits.remaining(inCart));
        model.addAttribute("purchaseLimit",PurchaseLimits.MAX_PER_PRODUCT);
        model.addAttribute("signedIn",currentUser.isPresent());
        model.addAttribute("variants", productService.getVariantsByProductId(product.getProductId()).stream().filter(v -> Boolean.TRUE.equals(v.getIsActive())).toList());
        model.addAttribute("images", productService.getImagesByProductId(product.getProductId()));
        model.addAttribute("branchStock", productService.getAvailableBranchesForProduct(product.getProductId()));
        model.addAttribute("reviews", reviewService.getReviewsByProduct(product.getProductId(), PageRequest.of(0, 10)));
        model.addAttribute("branches", branchService.getAllActiveBranches());

        return "customer/product-detail";
    }
}
