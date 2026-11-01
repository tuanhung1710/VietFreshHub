package VietFreshHub.catalog;

import VietFreshHub.catalog.entity.CatalogCategory;
import VietFreshHub.catalog.service.CatalogCategoryPolicy;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CatalogCategoryPolicyTest {
    @Test
    void selectingParentIncludesActiveDescendantsAndRejectsBrokenBranches() {
        var categories = List.of(category(1, null, "ACTIVE"), category(2, 1L, "ACTIVE"),
                category(3, 1L, "INACTIVE"), category(4, 3L, "ACTIVE"), category(5, 99L, "ACTIVE"));
        assertEquals(Set.of(1L, 2L), CatalogCategoryPolicy.matchingIds(categories, 1L));
        assertEquals(Set.of(2L), CatalogCategoryPolicy.matchingIds(categories, 2L));
        assertEquals(Set.of(), CatalogCategoryPolicy.matchingIds(categories, 3L));
    }

    private CatalogCategory category(long id, Long parent, String status) {
        var category = new CatalogCategory();
        category.setCategoryId(id);
        category.setParentCategoryId(parent);
        category.setStatus(status);
        return category;
    }
}
