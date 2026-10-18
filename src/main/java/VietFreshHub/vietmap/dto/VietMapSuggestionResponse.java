package VietFreshHub.vietmap.dto;

import java.util.List;

public record VietMapSuggestionResponse(
        String refId,
        String name,
        String address,
        String display,
        List<Boundary> boundaries,
        VietMapSuggestionResponse dataOld
) {

    public record Boundary(
            Integer type,
            Integer id,
            String name,
            String prefix,
            String fullName
    ) {
    }
}
