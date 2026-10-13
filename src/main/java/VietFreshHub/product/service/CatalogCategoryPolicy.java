package VietFreshHub.product.service;

import VietFreshHub.product.entity.Category;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Same active-ancestor rule as dbo.fn_catalog_category_active; portable to test databases. */
public final class CatalogCategoryPolicy {
    private CatalogCategoryPolicy() {}
    public static Set<Long> activeIds(List<Category> categories) {
        Map<Long,Category> byId=categories.stream().collect(Collectors.toMap(Category::getCategoryId,Function.identity()));
        Set<Long> active=new HashSet<>();
        for(Category category:categories) {
            Long id=category.getCategoryId(); Set<Long> visited=new HashSet<>();
            while(id!=null && visited.size()<100 && visited.add(id)) {
                Category ancestor=byId.get(id);
                if(ancestor==null || !"ACTIVE".equals(ancestor.getStatus())) break;
                id=ancestor.getParentCategoryId();
            }
            if(id==null) active.add(category.getCategoryId());
        }
        return active;
    }
}
