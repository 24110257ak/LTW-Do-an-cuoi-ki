package vn.iotstar.service;

import vn.iotstar.entity.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryService {
    List<Category> getRootCategories();
    List<Category> getAllActiveCategories();
    Optional<Category> getCategoryBySlug(String slug);
    Optional<Category> getCategoryById(Integer id);
}
