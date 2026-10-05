package VietFreshHub.Product.repository;
import VietFreshHub.Product.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface CategoryRepository extends JpaRepository<Category,Long> {
    List<Category> findAllByOrderByNameAsc();
}

