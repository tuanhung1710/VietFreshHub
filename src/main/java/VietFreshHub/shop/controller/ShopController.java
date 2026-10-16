package VietFreshHub.shop.controller;

import VietFreshHub.order.service.OrderService;
import VietFreshHub.shop.dto.ShopPickupLocationRequest;
import VietFreshHub.shop.service.ShopService;
import VietFreshHub.vietmap.dto.VietMapPlaceResponse;
import VietFreshHub.vietmap.service.VietMapService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.validation.BindingResult;
import org.springframework.transaction.TransactionException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class ShopController {

    private final OrderService orderService;
    private final ShopService shopService;
    private final VietMapService vietMapService;

    @GetMapping("/seller")
    public String showSellerPage(Authentication authentication, Model model) {
        model.addAttribute("orderCounts", orderService.getOrderCounts(authentication));
        return "store_manager/shop";
    }

    @GetMapping("/seller/pickup-location")
    public String showPickupLocation(Authentication authentication, Model model) {
        preparePickupLocationPage(authentication, model);
        model.addAttribute("pickupLocationForm", new ShopPickupLocationRequest());
        return "store_manager/pickup-location";
    }

    @PostMapping("/seller/pickup-location")
    public String savePickupLocation(
            Authentication authentication,
            @Valid @ModelAttribute("pickupLocationForm") ShopPickupLocationRequest form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        shopService.getManagedShopId(authentication);
        if (bindingResult.hasErrors()) {
            preparePickupLocationPage(authentication, model);
            return "store_manager/pickup-location";
        }

        try {
            VietMapPlaceResponse place = vietMapService.place(form.getRefId());
            shopService.validatePickupLocation(place);
            shopService.savePickupLocation(authentication, place);
        } catch (ResponseStatusException exception) {
            bindingResult.rejectValue("refId", "pickupLocation.provider", exception.getReason());
        } catch (IllegalArgumentException exception) {
            bindingResult.rejectValue("refId", "pickupLocation.invalid", exception.getMessage());
        } catch (DataAccessException | TransactionException exception) {
            bindingResult.reject("pickupLocation.saveFailed",
                    "Không thể lưu địa điểm lấy hàng. Vui lòng thử lại sau.");
        }

        if (bindingResult.hasErrors()) {
            preparePickupLocationPage(authentication, model);
            return "store_manager/pickup-location";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật địa điểm lấy hàng.");
        return "redirect:/seller/pickup-location";
    }

    private void preparePickupLocationPage(Authentication authentication, Model model) {
        Long shopId = shopService.getManagedShopId(authentication);
        model.addAttribute("pickupLocation", shopService.findLocationByShopId(shopId).orElse(null));
    }
}
