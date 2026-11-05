package VietFreshHub.payment.vnpay;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VnpayPropertiesTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(VnpayPropertiesConfiguration.class);

    @Test
    void enabledVnpayFailsStartupWhenSecretIsMissing() {
        contextRunner.withPropertyValues("app.vnpay.enabled=true")
                .run(context -> {
                    assertTrue(context.getStartupFailure() != null);
                    assertTrue(exceptionMessages(context.getStartupFailure())
                            .contains("hashSecretConfiguredWhenEnabled"));
                });
    }

    @Test
    void enabledVnpayFailsStartupWhenSecretIsBlank() {
        contextRunner.withPropertyValues(
                        "app.vnpay.enabled=true",
                        "app.vnpay.hash-secret=   ")
                .run(context -> assertTrue(context.getStartupFailure() != null));
    }

    @Test
    void enabledVnpayStartsWhenSecretIsConfigured() {
        contextRunner.withPropertyValues(
                        "app.vnpay.enabled=true",
                        "app.vnpay.hash-secret=test-secret")
                .run(context -> {
                    assertTrue(context.getStartupFailure() == null);
                    assertTrue(context.getBean(VnpayProperties.class).isReady());
                });
    }

    @Test
    void disabledVnpayCanStartWithoutSecretButCannotBeUsed() {
        contextRunner.withPropertyValues("app.vnpay.enabled=false")
                .run(context -> {
                    assertTrue(context.getStartupFailure() == null);
                    assertFalse(context.getBean(VnpayProperties.class).isReady());
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(VnpayProperties.class)
    static class VnpayPropertiesConfiguration {
        @Bean(name = "configurationPropertiesValidator")
        @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
        static LocalValidatorFactoryBean configurationPropertiesValidator() {
            return new LocalValidatorFactoryBean();
        }
    }

    private static String exceptionMessages(Throwable failure) {
        StringBuilder messages = new StringBuilder();
        for (Throwable current = failure; current != null; current = current.getCause()) {
            messages.append(current.getMessage()).append('\n');
        }
        return messages.toString();
    }
}
