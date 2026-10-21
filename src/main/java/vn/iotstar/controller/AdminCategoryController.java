package vn.iotstar.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.iotstar.entity.Category;
import vn.iotstar.service.CategoryService;

@Controller
@RequestMapping("/admin/categories")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public String listCategories(Model model) {
        model.addAttribute("categories", categoryService.getAllCategories());
        model.addAttribute("newCategory", new Category());
        return "admin/categories/list";
    }

    @PostMapping("/save")
    public String saveCategory(
            @ModelAttribute("newCategory") Category category,
            RedirectAttributes redirectAttributes) {

        if (category.getDisplayOrder() == null) {
            category.setDisplayOrder(0);
        }
        if (category.getIsActive() == null) {
            category.setIsActive(true);
        }

        categoryService.saveCategory(category);
        redirectAttributes.addFlashAttribute("successMessage", "Lưu danh mục thành công!");
        return "redirect:/admin/categories";
    }

    @PostMapping("/{id}/toggle")
    public String toggleStatus(@PathVariable("id") Integer id, RedirectAttributes redirectAttributes) {
        categoryService.toggleCategoryStatus(id);
        redirectAttributes.addFlashAttribute("successMessage", "Cập nhật trạng thái danh mục thành công!");
        return "redirect:/admin/categories";
    }
}
