package VietFreshHub.shop;

import VietFreshHub.inventory.dto.InventoryAttentionSummary;
import VietFreshHub.order.dto.OrderCountsResponse;
import VietFreshHub.order.service.OrderService;
import VietFreshHub.product.service.ProductService;
import VietFreshHub.shop.controller.ShopController;
import VietFreshHub.shop.service.ShopService;
import VietFreshHub.vietmap.service.VietMapService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.dao.DataAccessResourceFailureException;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopControllerDashboardTest {
    @Test
    void dashboardShowsEmptyOrdersProductsAndInventoryWithoutAlerts() {
        OrderService orders = mock(OrderService.class);
        ShopService shops = mock(ShopService.class);
        ProductService products = mock(ProductService.class);
        Authentication authentication = mock(Authentication.class);
        when(shops.getManagedShopCode(authentication)).thenReturn("SHP-42");
        when(orders.getOrderCounts(authentication)).thenReturn(new OrderCountsResponse(0, 0, 0, 0, 0, 0));
        var sales = new VietFreshHub.order.dto.ShopReportSummary.Sales(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0L);
        when(orders.getShopSalesSummary(authentication, null)).thenReturn(sales);
        when(orders.getTodaysOrders(authentication)).thenReturn(List.of());
        when(orders.getBestSellingProducts(authentication)).thenReturn(List.of());
        when(products.inventoryAttentionSummary(authentication)).thenReturn(new InventoryAttentionSummary(0, 0, 0, 2));
        Model model = new ExtendedModelMap();
        ShopController controller = new ShopController(orders, shops, mock(VietMapService.class), products);

        assertEquals("store_manager/shop", controller.showSellerPage(authentication, model));
        assertEquals(sales, model.getAttribute("todaySales"));
        assertEquals(List.of(), model.getAttribute("todaysOrders"));
        assertEquals(List.of(), model.getAttribute("bestSellingProducts"));
        assertEquals(new InventoryAttentionSummary(0, 0, 0, 2), model.getAttribute("inventoryAttention"));
        verify(orders).getShopSalesSummary(authentication, null);
    }

    @Test
    void dashboardDistinguishesNoInventoryFromInventoryWithoutAlerts() {
        InventoryAttentionSummary noData = new InventoryAttentionSummary(0, 0, 0, 0);
        InventoryAttentionSummary healthy = new InventoryAttentionSummary(0, 0, 0, 3);

        assertEquals(false, noData.hasInventoryData());
        assertEquals(false, noData.hasAlerts());
        assertEquals(true, healthy.hasInventoryData());
        assertEquals(false, healthy.hasAlerts());
    }

    @Test
    void dashboardReportsInventoryLoadFailureSeparately() {
        OrderService orders = mock(OrderService.class);
        ShopService shops = mock(ShopService.class);
        ProductService products = mock(ProductService.class);
        Authentication authentication = mock(Authentication.class);
        when(shops.getManagedShopCode(authentication)).thenReturn("SHP-42");
        when(orders.getOrderCounts(authentication)).thenReturn(new OrderCountsResponse(0, 0, 0, 0, 0, 0));
        when(orders.getShopSalesSummary(authentication, null)).thenReturn(
                new VietFreshHub.order.dto.ShopReportSummary.Sales(
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0L));
        when(orders.getTodaysOrders(authentication)).thenReturn(List.of());
        when(orders.getBestSellingProducts(authentication)).thenReturn(List.of());
        when(products.inventoryAttentionSummary(authentication))
                .thenThrow(new DataAccessResourceFailureException("inventory unavailable"));
        Model model = new ExtendedModelMap();
        ShopController controller = new ShopController(orders, shops, mock(VietMapService.class), products);

        assertEquals("store_manager/shop", controller.showSellerPage(authentication, model));
        assertEquals(true, model.getAttribute("inventoryLoadError"));
        assertEquals(null, model.getAttribute("inventoryAttention"));
    }
}
