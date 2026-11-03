package VietFreshHub.exception;

import VietFreshHub.auth.exception.RegistrationException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RegistrationException.class)
    public String handleRegistrationException(
            RegistrationException ex,
            RedirectAttributes redirectAttributes) {

        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        return "redirect:/register";
    }

    @ExceptionHandler({NoResourceFoundException.class, ResourceNotFoundException.class})
    public void handleNotFound(HttpServletResponse response) throws IOException {
        // Chuyển qua /error để hiển thị trang lỗi và giữ đúng mã HTTP 404.
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
    }
}
