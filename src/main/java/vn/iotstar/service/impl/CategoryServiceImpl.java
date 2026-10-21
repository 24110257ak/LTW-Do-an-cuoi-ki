package vn.iotstar.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.iotstar.entity.Category;
import vn.iotstar.exception.ResourceNotFoundException;
import vn.iotstar.repository.CategoryRepository;
import vn.iotstar.service.CategoryService;
import vn.iotstar.util.SlugUtils;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    @Override
    public List<Category> getRootCategories() {
        return categoryRepository.findByParentIsNullAndIsActiveTrueOrderByDisplayOrderAsc();
    }

    @Override
    public List<Category> getAllActiveCategories() {
        return categoryRepository.findAllByIsActiveTrueOrderByDisplayOrderAsc();
    }

    @Override
    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    @Override
    public Optional<Category> getCategoryBySlug(String slug) {
        return categoryRepository.findBySlug(slug);
    }

    @Override
    public Optional<Category> getCategoryById(Integer id) {
        return categoryRepository.findById(id);
    }

    @Override
    @Transactional
    public Category saveCategory(Category category) {
        if (category.getSlug() == null || category.getSlug().isBlank()) {
            category.setSlug(SlugUtils.toSlug(category.getCategoryName()));
        }
        return categoryRepository.save(category);
    }

    @Override
    @Transactional
    public void toggleCategoryStatus(Integer id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Danh mục", "id", id));
        category.setIsActive(category.getIsActive() == null || !category.getIsActive());
        categoryRepository.save(category);
    }
}
