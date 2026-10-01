package VietFreshHub.exception;

import VietFreshHub.Auth.exception.RegistrationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RegistrationException.class)
    public String handleRegistrationException(
            RegistrationException ex,
            RedirectAttributes redirectAttributes) {

        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        return "redirect:/register";
    }

    @ExceptionHandler(Exception.class)
    public String handleException(Exception ex, Model model) {
        log.error("Đã xảy ra lỗi không mong muốn", ex);

        model.addAttribute(
                "errorMessage",
                "Hệ thống đang gặp lỗi. Vui lòng thử lại sau."
        );

        return "error";
    }
}
