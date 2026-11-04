package VietFreshHub.common;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.LocalDateTime;

/** Các cột đã nâng cấp lưu giờ Việt Nam, còn dữ liệu thời gian trong Java dùng UTC. */
@Converter
public class VietnamDatabaseTime implements AttributeConverter<LocalDateTime, LocalDateTime> {
    public static LocalDateTime toDatabase(LocalDateTime utc) {
        return utc == null ? null : utc.plusHours(7);
    }

    @Override
    public LocalDateTime convertToDatabaseColumn(LocalDateTime utc) {
        return toDatabase(utc);
    }

    @Override
    public LocalDateTime convertToEntityAttribute(LocalDateTime local) {
        return local == null ? null : local.minusHours(7);
    }
}
