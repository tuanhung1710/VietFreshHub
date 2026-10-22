package VietFreshHub.vietmap.controller;

import VietFreshHub.vietmap.dto.VietMapPlaceResponse;
import VietFreshHub.vietmap.dto.VietMapRouteDistanceResponse;
import VietFreshHub.vietmap.dto.VietMapSuggestionResponse;
import VietFreshHub.vietmap.service.VietMapService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/vietmap")
public class VietMapController {

    private final VietMapService vietMapService;

    @GetMapping("/search")
    public List<VietMapSuggestionResponse> search(@RequestParam(value = "text", required = false) String text) {
        return vietMapService.search(text);
    }

    @GetMapping("/autocomplete")
    public List<VietMapSuggestionResponse> autocomplete(@RequestParam(value = "text", required = false) String text) {
        return vietMapService.autocomplete(text);
    }

    @GetMapping("/place")
    public VietMapPlaceResponse place(@RequestParam(value = "refId", required = false) String refId) {
        return vietMapService.place(refId);
    }

    @GetMapping("/route-distance")
    public VietMapRouteDistanceResponse routeDistance(
            @RequestParam(value = "pickupLat", required = false) BigDecimal pickupLatitude,
            @RequestParam(value = "pickupLng", required = false) BigDecimal pickupLongitude,
            @RequestParam(value = "deliveryLat", required = false) BigDecimal destinationLatitude,
            @RequestParam(value = "deliveryLng", required = false) BigDecimal destinationLongitude
    ) {
        return new VietMapRouteDistanceResponse(vietMapService.motorcycleRoadDistanceMeters(
                pickupLatitude, pickupLongitude, destinationLatitude, destinationLongitude));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleInvalidCoordinate() {
        return handleVietMapError(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Tọa độ phải là số thập phân hợp lệ."));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleVietMapError(ResponseStatusException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason());
        return ResponseEntity.status(exception.getStatusCode()).body(problem);
    }
}
