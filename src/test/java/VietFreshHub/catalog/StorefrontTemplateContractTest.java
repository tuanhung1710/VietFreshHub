package VietFreshHub.catalog;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

class StorefrontTemplateContractTest {
    @Test
    void stylesheetIsCopiedExactlyFromTheUserTemplate() throws Exception {
        String reference = Files.readString(Path.of("src/test/resources/storefront/reference.html")).replace("\r\n", "\n");
        var matcher = Pattern.compile("<style>(.*?)</style>", Pattern.DOTALL).matcher(reference);
        assertTrue(matcher.find());
        String actual = Files.readString(Path.of("src/main/resources/static/css/customer-shop.css")).replace("\r\n", "\n");
        assertEquals(matcher.group(1), actual);
    }

    @Test
    void allTemplateSectionsAndControlsAreRetainedWithoutDemoProductArrays() throws Exception {
        String shell = Files.readString(Path.of("src/main/resources/templates/customer/home.html"));
        for (String id : new String[]{"home", "catalog", "shopspage", "shopdetail", "homecategories", "topproducts",
                "homeshops", "homeproducts", "homePagination", "sidebar", "filterCategories", "locations", "filterShops",
                "ratings", "results", "pagination", "shoplist", "modal", "applyPrice", "toggleFilters", "reset"}) {
            assertTrue(shell.contains("id=\"" + id + "\""), id);
        }
        assertFalse(shell.contains("const raw="));
        assertFalse(shell.contains("const products="));
        assertFalse(shell.contains("sf-"));
        assertFalse(shell.contains("storefront.css"));
    }
}
