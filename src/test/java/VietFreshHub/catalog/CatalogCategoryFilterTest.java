package VietFreshHub.catalog;

import VietFreshHub.catalog.entity.CatalogCategory;
import VietFreshHub.catalog.repository.CatalogProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogCategoryFilterTest {
    final CatalogProductRepository repository = mock(CatalogProductRepository.class, CALLS_REAL_METHODS);
    CatalogCategory category(long id, Long parent, String slug, String status) {
        var c = new CatalogCategory(); c.setCategoryId(id); c.setParentCategoryId(parent);
        c.setSlug(slug); c.setStatus(status); return c;
    }
    @Test void filtersBySelectedCategoryAndActiveDescendantsBeforePagination() {
        when(repository.findCatalogCategories()).thenReturn(List.of(
                category(1L,null,"trai-cay","ACTIVE"), category(2L,1L,"qua-tang-trai-cay","ACTIVE"),
                category(3L,2L,"hop-qua","ACTIVE"), category(4L,2L,"qua-cu","INACTIVE"),
                category(5L,1L,"trai-cay-nhap-khau","ACTIVE")));
        var page = PageRequest.of(1,12);
        when(repository.findVisibleCatalogForCategories(eq("táo"),eq(Set.of(2L,3L)),any(),eq(page)))
                .thenReturn(Page.empty(page));
        assertTrue(repository.findVisibleCatalog("táo","qua-tang-trai-cay",page).isEmpty());
        verify(repository).findVisibleCatalogForCategories(eq("táo"),eq(Set.of(2L,3L)),any(),eq(page));
    }
    @Test void futureGroupsAreVisibleButDisabledGroupsAreNot() {
        when(repository.findCatalogCategories()).thenReturn(List.of(category(1L,null,"trai-cay-huu-co","INACTIVE")));
        var service = new VietFreshHub.catalog.service.impl.CatalogServiceImpl(repository, null, null);
        var options = service.categories();
        assertEquals(9, options.size());
        assertTrue(options.stream().anyMatch(c -> c.slug().equals("qua-tang-trai-cay")));
        assertFalse(options.stream().anyMatch(c -> c.slug().equals("trai-cay-huu-co")));
    }
    @Test void unknownOrInactiveCategoryDoesNotFallBackToAllProducts() {
        when(repository.findCatalogCategories()).thenReturn(List.of(category(1L,null,"old","INACTIVE")));
        assertTrue(repository.findVisibleCatalog("","old",PageRequest.of(0,12)).isEmpty());
        assertTrue(repository.findVisibleCatalog("","missing",PageRequest.of(0,12)).isEmpty());
        verify(repository,never()).findVisibleCatalogForCategories(any(),any(),any(),any());
    }
}
