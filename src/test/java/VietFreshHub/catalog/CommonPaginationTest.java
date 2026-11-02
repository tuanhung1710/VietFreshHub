package VietFreshHub.catalog;

import VietFreshHub.common.PageSizes;
import VietFreshHub.common.Pagination;
import VietFreshHub.common.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import static org.junit.jupiter.api.Assertions.*;

class CommonPaginationTest {
    @Test
    void sharedConfigurationHasUniqueOptionsAndAValidDefault() {
        assertTrue(PageSizes.OPTIONS.contains(PageSizes.DEFAULT));
        assertEquals(PageSizes.OPTIONS.size(),PageSizes.OPTIONS.stream().distinct().count());
        assertEquals(PageSizes.DEFAULT,PageSizes.normalize(-1));
    }

    @Test
    void emptyAndOversizedRequestsHaveASafeOneBasedPublicPage() {
        var empty = Pagination.clamp(999,PageSizes.DEFAULT,0);
        assertEquals(0,empty.getPageNumber());
        assertEquals(1,PageResponse.from(Page.empty(empty)).page());
        assertEquals(1,PageResponse.from(Page.empty(empty)).pages());
        var maximum = Pagination.request(Integer.MAX_VALUE,PageSizes.DEFAULT);
        assertTrue(maximum.getOffset()<=Integer.MAX_VALUE);
    }
}
