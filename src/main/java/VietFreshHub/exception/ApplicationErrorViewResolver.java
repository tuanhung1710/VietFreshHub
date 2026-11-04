package VietFreshHub.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.autoconfigure.error.ErrorViewResolver;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;

/** Dùng chung giao diện lỗi cho các trang HTML; Spring Boot vẫn xử lý phản hồi API. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApplicationErrorViewResolver implements ErrorViewResolver {

    @Override
    public ModelAndView resolveErrorView(HttpServletRequest request, HttpStatus status, Map<String, Object> model) {
        ErrorContent content = contentFor(status);
        ModelAndView view = new ModelAndView("error/http-error");
        view.setStatus(status);
        view.addObject("status", status.value());
        view.addObject("errorTitle", content.title());
        view.addObject("errorDescription", content.description());
        view.addObject("showLogin", status == HttpStatus.UNAUTHORIZED);
        return view;
    }

    private ErrorContent contentFor(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> new ErrorContent("Thông tin không hợp lệ",
                    "Yêu cầu chưa thể được xử lý. Vui lòng kiểm tra lại đường dẫn và thông tin đã nhập.");
            case UNAUTHORIZED -> new ErrorContent("Vui lòng đăng nhập",
                    "Bạn cần đăng nhập để tiếp tục truy cập nội dung này.");
            case FORBIDDEN -> new ErrorContent("Bạn không có quyền truy cập",
                    "Tài khoản hiện tại không được phép truy cập nội dung hoặc thực hiện thao tác này.");
            case NOT_FOUND -> new ErrorContent("Không tìm thấy trang",
                    "Trang bạn đang tìm có thể đã được di chuyển, không còn tồn tại hoặc đường dẫn chưa chính xác.");
            case METHOD_NOT_ALLOWED -> new ErrorContent("Thao tác không được hỗ trợ",
                    "Trang này không hỗ trợ thao tác vừa thực hiện. Vui lòng trở về trang chủ để tiếp tục.");
            case NOT_ACCEPTABLE -> new ErrorContent("Định dạng phản hồi không được hỗ trợ",
                    "Nội dung không thể hiển thị theo định dạng được yêu cầu. Vui lòng trở về trang chủ để tiếp tục.");
            case REQUEST_TIMEOUT -> new ErrorContent("Yêu cầu đã hết thời gian chờ",
                    "Yêu cầu mất quá nhiều thời gian để hoàn tất. Vui lòng kiểm tra kết nối và thử lại sau.");
            case CONFLICT -> new ErrorContent("Dữ liệu đã thay đổi",
                    "Yêu cầu bị xung đột với dữ liệu hiện tại. Vui lòng kiểm tra thông tin mới nhất trước khi thực hiện lại.");
            case GONE -> new ErrorContent("Nội dung không còn khả dụng",
                    "Nội dung này đã được gỡ khỏi hệ thống. Bạn có thể trở về trang chủ để khám phá nội dung khác.");
            case CONTENT_TOO_LARGE -> new ErrorContent("Dữ liệu gửi lên quá lớn",
                    "Dung lượng dữ liệu vượt quá giới hạn cho phép. Vui lòng giảm kích thước tệp hoặc nội dung gửi lên.");
            case UNSUPPORTED_MEDIA_TYPE -> new ErrorContent("Định dạng dữ liệu không được hỗ trợ",
                    "Vui lòng kiểm tra định dạng tệp hoặc dữ liệu trước khi gửi lại yêu cầu.");
            case UNPROCESSABLE_CONTENT -> new ErrorContent("Không thể xử lý thông tin",
                    "Thông tin gửi lên chưa phù hợp với yêu cầu. Vui lòng kiểm tra lại trước khi tiếp tục.");
            case TOO_MANY_REQUESTS -> new ErrorContent("Bạn thao tác quá nhanh",
                    "Hệ thống nhận được nhiều yêu cầu trong thời gian ngắn. Vui lòng chờ một chút rồi thử lại.");
            case INTERNAL_SERVER_ERROR -> new ErrorContent("Hệ thống đang gặp sự cố",
                    "VietFresh Hub chưa thể xử lý yêu cầu lúc này. Vui lòng quay lại sau ít phút.");
            case NOT_IMPLEMENTED -> new ErrorContent("Tính năng chưa được hỗ trợ",
                    "Hệ thống hiện chưa hỗ trợ yêu cầu này. Vui lòng trở về trang chủ để tiếp tục.");
            case BAD_GATEWAY -> new ErrorContent("Kết nối dịch vụ bị gián đoạn",
                    "Hệ thống chưa nhận được phản hồi hợp lệ từ dịch vụ liên kết. Vui lòng thử lại sau ít phút.");
            case SERVICE_UNAVAILABLE -> new ErrorContent("Dịch vụ tạm thời không khả dụng",
                    "Hệ thống đang bận hoặc tạm thời gián đoạn. Vui lòng quay lại sau ít phút.");
            case GATEWAY_TIMEOUT -> new ErrorContent("Dịch vụ phản hồi quá chậm",
                    "Dịch vụ liên kết chưa phản hồi kịp thời. Vui lòng thử lại sau ít phút.");
            default -> status.is4xxClientError()
                    ? new ErrorContent("Không thể thực hiện yêu cầu",
                            "Vui lòng kiểm tra lại đường dẫn và thông tin yêu cầu trước khi tiếp tục.")
                    : new ErrorContent("Hệ thống tạm thời gián đoạn",
                            "VietFresh Hub chưa thể hoàn tất yêu cầu. Vui lòng quay lại sau ít phút.");
        };
    }

    private record ErrorContent(String title, String description) { }
}
