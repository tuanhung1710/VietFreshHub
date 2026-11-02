package VietFreshHub.product.repository;

import VietFreshHub.product.entity.Category;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    /** Lấy toàn bộ danh mục theo tên để kiểm tra chuỗi danh mục cha và hiển thị lựa chọn. */
    List<Category> findAllByOrderByNameAsc();

    boolean existsBySlugIgnoreCase(String slug);

    boolean existsBySlugIgnoreCaseAndCategoryIdNot(String slug, Long categoryId);
}
