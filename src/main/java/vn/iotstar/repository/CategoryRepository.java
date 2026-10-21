package vn.iotstar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.iotstar.entity.Category;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Integer> {
    Optional<Category> findBySlug(String slug);
    List<Category> findByParentIsNullAndIsActiveTrueOrderByDisplayOrderAsc();
    List<Category> findByParentCategoryIdAndIsActiveTrueOrderByDisplayOrderAsc(Integer parentId);
    List<Category> findAllByIsActiveTrueOrderByDisplayOrderAsc();
    boolean existsBySlug(String slug);
}

