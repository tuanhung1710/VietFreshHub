package VietFreshHub.exception;

import VietFreshHub.Auth.exception.RegistrationException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(RegistrationException.class)
    public String handleRegistrationException(RegistrationException ex, RedirectAttributes flash) {
        flash.addFlashAttribute("errorMessage", ex.getMessage());
        return "redirect:/register";
    }
    @ExceptionHandler(ResourceNotFoundException.class)
    public ModelAndView handleResourceNotFoundException(ResourceNotFoundException ex) {
        return errorView("error/not-found", HttpStatus.NOT_FOUND, ex.getMessage());
    }
    @ExceptionHandler({OutOfStockException.class, IllegalArgumentException.class})
    public ModelAndView handleInvalidInput(RuntimeException ex) {
        return errorView("error/bad-request", HttpStatus.BAD_REQUEST, ex.getMessage());
    }
    @ExceptionHandler(BindException.class)
    public ModelAndView handleValidationException(BindException ex) {
        String message = ex.getBindingResult().getAllErrors().isEmpty()
                ? "Thông tin không hợp lệ. Vui lòng kiểm tra lại."
                : ex.getBindingResult().getAllErrors().getFirst().getDefaultMessage();
        return errorView("error/bad-request", HttpStatus.BAD_REQUEST, message);
    }
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ModelAndView handleMalformedRequest(Exception ex) {
        return errorView("error/bad-request", HttpStatus.BAD_REQUEST,
                "Thông tin yêu cầu không hợp lệ hoặc còn thiếu. Vui lòng kiểm tra lại.");
    }
    @ExceptionHandler(NoResourceFoundException.class)
    public ModelAndView handleNoResource(NoResourceFoundException ex) {
        return errorView("error/not-found", HttpStatus.NOT_FOUND, "Không tìm thấy trang bạn yêu cầu.");
    }
    private ModelAndView errorView(String template, HttpStatus status, String message) {
        ModelAndView view = new ModelAndView(template);
        view.setStatus(status);
        view.addObject("message", message);
        return view;
    }
}
