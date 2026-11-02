package VietFreshHub.catalog.service;

import VietFreshHub.catalog.entity.CatalogCategory;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Same active-ancestor rule as dbo.fn_catalog_category_active; portable to test databases. */
public final class CatalogCategoryPolicy {
    private CatalogCategoryPolicy() {}
    public static Set<Long> matchingIds(List<CatalogCategory> categories, Long selectedId) {
        Set<Long> active = activeIds(categories);
        if (selectedId == null) return active;
        if (!active.contains(selectedId)) return Set.of();
        Set<Long> result = new HashSet<>();
        result.add(selectedId);
        boolean changed;
        do {
            changed = false;
            for (CatalogCategory category : categories) {
                if (active.contains(category.getCategoryId()) && result.contains(category.getParentCategoryId())) {
                    changed |= result.add(category.getCategoryId());
                }
            }
        } while (changed);
        return result;
    }
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
