package VietFreshHub.Product.service;
import VietFreshHub.Product.dto.*;
import VietFreshHub.Product.entity.Category;
import org.springframework.security.core.Authentication;
import org.springframework.data.domain.Page;
import java.util.List;
import java.time.LocalDate;
public interface ProductService {
    Page<ProductResponse> search(Authentication auth,String q,Long category,String status,boolean archived,String stock,int page,int size);
    Page<ProductResponse> search(Authentication auth,String q,Long category,String status,boolean archived,String stock,
        String business,String approval,LocalDate createdFrom,LocalDate createdTo,int page,int size);
    CatalogSummary catalogSummary(Authentication auth);
    ProductResponse get(Authentication auth,Long id);
    List<Category> categories();
    ProductRequest editForm(Authentication auth,Long id);
    VariantRequest variantForm(Authentication auth,Long product,Long variant);
    Long create(Authentication auth,ProductRequest form);
    void update(Authentication auth,Long id,ProductRequest form);
    void changeStatus(Authentication auth,Long id,ActionRequest form);
    void addVariant(Authentication auth,Long product,VariantRequest form);
    void updateVariant(Authentication auth,Long product,Long variant,VariantRequest form);
    void changeVariantStatus(Authentication auth,Long product,Long variant,VariantActionRequest form);
    Page<VietFreshHub.Product.entity.PriceHistory> priceHistory(Authentication auth,Long product,Long variant,int page);
}
