package VietFreshHub.vietmap.service;

import VietFreshHub.vietmap.dto.VietMapPlaceResponse;
import VietFreshHub.vietmap.dto.VietMapSuggestionResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

@Service
public class VietMapService {

    private final String apiKey;
    private final String baseUrl;
    private final RestClient restClient;
    private final ObjectReader routeResponseReader;

    public VietMapService(
            @Value("${vietmap.api-key:}") String apiKey,
            @Value("${vietmap.base-url:}") String baseUrl,
            JsonMapper jsonMapper
    ) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.routeResponseReader = jsonMapper.readerFor(JsonNode.class)
                .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS,
                        DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public List<VietMapSuggestionResponse> search(String text) {
        String searchText = validateText(text);
        return getSuggestions("/api/search/v4", searchText);
    }

    public List<VietMapSuggestionResponse> autocomplete(String text) {
        String searchText = validateText(text);
        if (searchText.codePointCount(0, searchText.length()) < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Vui lòng nhập ít nhất 2 ký tự để gợi ý địa chỉ.");
        }
        return getSuggestions("/api/autocomplete/v4", searchText);
    }

    public VietMapPlaceResponse place(String refId) {
        if (refId == null || refId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Vui lòng chọn mã tham chiếu địa chỉ.");
        }
        JsonNode response = getResponse(buildUri("/api/place/v4", "refid", refId, false));
        if (response == null || !response.isObject()) {
            throw malformedResponse();
        }
        Integer districtId = integerValue(response, "district_id");
        String district = textValue(response, "district");
        return new VietMapPlaceResponse(
                integerValue(response, "city_id"),
                textValue(response, "city"),
                districtId != null && districtId == 0 ? null : districtId,
                district == null || district.isBlank() ? null : district,
                integerValue(response, "ward_id"),
                textValue(response, "ward"),
                textValue(response, "address"),
                requiredText(response, "display"),
                coordinateValue(response, "lat", 90),
                coordinateValue(response, "lng", 180)
        );
    }

    public BigDecimal motorcycleRoadDistanceMeters(BigDecimal pickupLatitude, BigDecimal pickupLongitude,
                                                   BigDecimal destinationLatitude, BigDecimal destinationLongitude) {
        validateCoordinate(pickupLatitude, 90, "Vĩ độ điểm lấy hàng");
        validateCoordinate(pickupLongitude, 180, "Kinh độ điểm lấy hàng");
        validateCoordinate(destinationLatitude, 90, "Vĩ độ điểm giao hàng");
        validateCoordinate(destinationLongitude, 180, "Kinh độ điểm giao hàng");

        String pickupPoint = pickupLatitude.toPlainString() + "," + pickupLongitude.toPlainString();
        String destinationPoint = destinationLatitude.toPlainString() + "," + destinationLongitude.toPlainString();
        JsonNode response = getRouteResponse(buildRouteUri(pickupPoint, destinationPoint));
        if (response == null || !response.isObject()) {
            throw malformedResponse();
        }
        String code = requiredText(response, "code");
        if ("ZERO_RESULTS".equals(code)) {
            throw noRoute();
        }
        if ("OVER_DAILY_LIMIT".equals(code)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Dịch vụ VietMap đã hết hạn mức yêu cầu trong ngày. Vui lòng thử lại sau.");
        }
        if (!"OK".equals(code)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Dịch vụ VietMap không thể tính tuyến đường xe máy. Vui lòng thử lại sau.");
        }
        JsonNode paths = response.get("paths");
        if (paths == null || !paths.isArray()) {
            throw malformedResponse();
        }
        if (paths.isEmpty()) {
            throw noRoute();
        }
        JsonNode route = paths.get(0);
        if (route == null || !route.isObject()) {
            throw malformedResponse();
        }
        JsonNode distance = route.get("distance");
        if (distance == null || !distance.isNumber()) {
            throw malformedResponse();
        }
        BigDecimal distanceMeters = distance.decimalValue(null);
        if (distanceMeters == null || distanceMeters.signum() < 0) {
            throw malformedResponse();
        }
        return distanceMeters;
    }

    private void validateCoordinate(BigDecimal coordinate, int limit, String label) {
        if (coordinate == null || coordinate.abs().compareTo(BigDecimal.valueOf(limit)) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    label + " phải được cung cấp và nằm trong khoảng -" + limit + " đến " + limit + ".");
        }
    }

    private String validateText(String text) {
        if (text == null || text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Vui lòng nhập nội dung tìm kiếm địa chỉ.");
        }
        return text.strip();
    }

    private List<VietMapSuggestionResponse> getSuggestions(String path, String text) {
        JsonNode response = getResponse(buildUri(path, "text", text, true));
        if (response == null || !response.isArray()) {
            throw malformedResponse();
        }
        List<VietMapSuggestionResponse> suggestions = new ArrayList<>();
        for (JsonNode candidate : response) {
            suggestions.add(mapSuggestion(candidate, true));
        }
        return suggestions;
    }

    private URI buildUri(String path, String parameter, String value, boolean includeDisplayType) {
        try {
            UriComponentsBuilder builder = uriBuilder(path).queryParam(parameter, "{value}");
            if (includeDisplayType) {
                builder.queryParam("display_type", 5);
            }
            return builder.encode().buildAndExpand(apiKey, value).toUri();
        } catch (IllegalArgumentException exception) {
            throw unavailableConfiguration();
        }
    }

    private URI buildRouteUri(String pickupPoint, String destinationPoint) {
        try {
            return uriBuilder("/api/route/v4")
                    .queryParam("point", "{pickupPoint}")
                    .queryParam("point", "{destinationPoint}")
                    .queryParam("vehicle", "motorcycle")
                    .encode()
                    .buildAndExpand(apiKey, pickupPoint, destinationPoint)
                    .toUri();
        } catch (IllegalArgumentException exception) {
            throw unavailableConfiguration();
        }
    }

    private UriComponentsBuilder uriBuilder(String path) {
        if (apiKey == null || apiKey.isBlank() || baseUrl == null || baseUrl.isBlank()) {
            throw unavailableConfiguration();
        }
        try {
            URI baseUri = URI.create(baseUrl);
            if (!"https".equalsIgnoreCase(baseUri.getScheme()) || baseUri.getHost() == null
                    || baseUri.getUserInfo() != null || baseUri.getQuery() != null
                    || baseUri.getFragment() != null) {
                throw unavailableConfiguration();
            }
            return UriComponentsBuilder.fromUri(baseUri)
                    .replacePath(path)
                    .queryParam("apikey", "{apiKey}");
        } catch (IllegalArgumentException exception) {
            throw unavailableConfiguration();
        }
    }

    private JsonNode getResponse(URI uri) {
        return getResponse(uri, JsonNode.class);
    }

    private JsonNode getRouteResponse(URI uri) {
        String body = getResponse(uri, String.class);
        if (body == null || body.isBlank()) {
            throw malformedResponse();
        }
        try {
            return routeResponseReader.readTree(body);
        } catch (JacksonException exception) {
            throw malformedResponse();
        }
    }

    private <T> T getResponse(URI uri, Class<T> responseType) {
        try {
            return restClient.get()
                    .uri(uri)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(), (request, response) -> {
                        if (response.getStatusCode().value() == 429) {
                            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                                    "Dịch vụ địa chỉ VietMap đang giới hạn yêu cầu. Vui lòng thử lại sau.");
                        }
                        if (response.getStatusCode().value() == 504) {
                            throw upstreamTimeout();
                        }
                        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                                "Dịch vụ địa chỉ VietMap trả về lỗi. Vui lòng thử lại sau.");
                    })
                    .body(responseType);
        } catch (ResourceAccessException exception) {
            if (isTimeout(exception)) {
                throw upstreamTimeout();
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Không thể kết nối dịch vụ địa chỉ VietMap. Vui lòng thử lại sau.");
        } catch (RestClientException exception) {
            if (isTimeout(exception)) {
                throw upstreamTimeout();
            }
            throw malformedResponse();
        }
    }

    private VietMapSuggestionResponse mapSuggestion(JsonNode candidate, boolean includeOldFormat) {
        if (candidate == null || !candidate.isObject()) {
            throw malformedResponse();
        }
        VietMapSuggestionResponse dataOld = null;
        JsonNode oldFormat = candidate.get("data_old");
        if (includeOldFormat && oldFormat != null && !oldFormat.isNull()) {
            dataOld = mapSuggestion(oldFormat, false);
        }
        return new VietMapSuggestionResponse(
                requiredText(candidate, "ref_id"),
                textValue(candidate, "name"),
                textValue(candidate, "address"),
                requiredText(candidate, "display"),
                mapBoundaries(candidate.get("boundaries")),
                dataOld
        );
    }

    private List<VietMapSuggestionResponse.Boundary> mapBoundaries(JsonNode boundaries) {
        if (boundaries == null || boundaries.isNull()) {
            return null;
        }
        if (!boundaries.isArray()) {
            throw malformedResponse();
        }
        List<VietMapSuggestionResponse.Boundary> result = new ArrayList<>();
        for (JsonNode boundary : boundaries) {
            if (!boundary.isObject()) {
                throw malformedResponse();
            }
            result.add(new VietMapSuggestionResponse.Boundary(
                    integerValue(boundary, "type"),
                    integerValue(boundary, "id"),
                    textValue(boundary, "name"),
                    textValue(boundary, "prefix"),
                    textValue(boundary, "full_name")
            ));
        }
        return result;
    }

    private String textValue(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isString()) {
            throw malformedResponse();
        }
        return value.stringValue();
    }

    private String requiredText(JsonNode object, String field) {
        String value = textValue(object, field);
        if (value == null || value.isBlank()) {
            throw malformedResponse();
        }
        return value;
    }

    private Integer integerValue(JsonNode object, String field) {
        JsonNode value = object.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw malformedResponse();
        }
        return value.intValue();
    }

    private BigDecimal coordinateValue(JsonNode object, String field, int limit) {
        JsonNode value = object.get(field);
        if (value == null || !value.isNumber()) {
            throw malformedResponse();
        }
        BigDecimal coordinate = value.decimalValue(null);
        if (coordinate == null || coordinate.abs().compareTo(BigDecimal.valueOf(limit)) > 0) {
            throw malformedResponse();
        }
        return coordinate;
    }

    private boolean isTimeout(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException
                    || cause instanceof TimeoutException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private ResponseStatusException unavailableConfiguration() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Dịch vụ địa chỉ VietMap chưa được cấu hình đầy đủ.");
    }

    private ResponseStatusException noRoute() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Không tìm thấy tuyến đường xe máy từ điểm lấy hàng đến điểm giao hàng.");
    }

    private ResponseStatusException upstreamTimeout() {
        return new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,
                "Dịch vụ địa chỉ VietMap phản hồi quá chậm. Vui lòng thử lại sau.");
    }

    private ResponseStatusException malformedResponse() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Dữ liệu địa chỉ từ VietMap không hợp lệ.");
    }
}
