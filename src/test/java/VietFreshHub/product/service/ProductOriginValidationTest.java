package VietFreshHub.product.service;

import VietFreshHub.product.dto.ProductRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProductOriginValidationTest {
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void close() { factory.close(); }

    @Test
    void originIsRequiredAndCannotContainOnlyWhitespace() {
        var form = new ProductRequest();
        assertFalse(validator.validateProperty(form, "origin").isEmpty());
        form.setOrigin(" \t\n ");
        assertFalse(validator.validateProperty(form, "origin").isEmpty());
    }

    @Test
    void originAllowsTwoHundredCharactersButRejectsLongerValues() {
        var form = new ProductRequest();
        form.setOrigin("X".repeat(200));
        assertTrue(validator.validateProperty(form, "origin").isEmpty());
        form.setOrigin("X".repeat(201));
        assertFalse(validator.validateProperty(form, "origin").isEmpty());
    }

    @Test
    void keepsUnicodeOriginAndTrimsOnlySurroundingWhitespace() {
        var form = new ProductRequest();
        form.setOrigin("  Tiền Giang, Việt Nam  ");
        assertEquals("Tiền Giang, Việt Nam", form.getOrigin());
        assertTrue(validator.validateProperty(form, "origin").isEmpty());
    }
}
