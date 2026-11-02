package VietFreshHub.product;

import VietFreshHub.product.config.ProductImageWebConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

class ProductImageWebConfigTest {
    @TempDir Path tempDirectory;

    @Test
    void servesLegacyFilesOnlyFromConfiguredDirectory() throws Exception {
        Path legacyDirectory = Files.createDirectory(tempDirectory.resolve("uploads"));
        Path image = Files.writeString(legacyDirectory.resolve("legacy.jpg"), "legacy-image");
        Files.writeString(tempDirectory.resolve("outside.txt"), "outside-file");

        try (AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                    "test-properties", Map.of("vietfresh.product.upload-directory", legacyDirectory.toString())));
            context.register(TestWebConfiguration.class);
            context.refresh();
            MockMvc mvc = webAppContextSetup(context).build();

            mvc.perform(get("/images/product-upload/legacy.jpg"))
                    .andExpect(status().isOk())
                    .andExpect(content().bytes(Files.readAllBytes(image)));
            mvc.perform(get("/images/product-upload/missing.jpg"))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/images/product-upload/%2e%2e/outside.txt"))
                    .andExpect(status().isNotFound());
        }
    }

    @Configuration
    @EnableWebMvc
    @Import(ProductImageWebConfig.class)
    static class TestWebConfiguration {}
}
