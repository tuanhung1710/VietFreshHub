package VietFreshHub.catalog.service;

import VietFreshHub.catalog.entity.CatalogCategory;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Same active-ancestor rule as dbo.fn_catalog_category_active; portable to test databases. */
public final class CatalogCategoryPolicy {
    private CatalogCategoryPolicy() {}
    public static Set<Long> activeIds(List<CatalogCategory> categories) {
        Map<Long,CatalogCategory> byId=categories.stream().collect(Collectors.toMap(CatalogCategory::getCategoryId,Function.identity()));
        Set<Long> active=new HashSet<>();
        for(CatalogCategory category:categories) {
            Long id=category.getCategoryId(); Set<Long> visited=new HashSet<>();
            while(id!=null && visited.size()<100 && visited.add(id)) {
                CatalogCategory ancestor=byId.get(id);
                if(ancestor==null || !"ACTIVE".equals(ancestor.getStatus())) break;
                id=ancestor.getParentCategoryId();
            }
            if(id==null) active.add(category.getCategoryId());
        }
        return active;
    }
}
