package vn.iotstar.service;

import vn.iotstar.entity.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryService {
    List<Category> getRootCategories();
    List<Category> getAllActiveCategories();
    List<Category> getAllCategories();
    Optional<Category> getCategoryBySlug(String slug);
    Optional<Category> getCategoryById(Integer id);
    Category saveCategory(Category category);
    void toggleCategoryStatus(Integer id);
}
