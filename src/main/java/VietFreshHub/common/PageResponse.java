package VietFreshHub.common;

import org.springframework.data.domain.Page;
import java.util.List;

public record PageResponse<T>(List<T> items, int page, int pageSize, long total, int pages) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber() + 1, page.getSize(), page.getTotalElements(),
                Math.max(1, page.getTotalPages()));
    }
}
