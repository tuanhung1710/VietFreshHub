package VietFreshHub.Inventory.dto;
import VietFreshHub.Inventory.entity.*;
import java.util.List;
public record BatchResponse(InventoryBatch batch,int available,boolean expired,boolean nearExpiry,
 BatchMarkdown markdown,List<StockAllocation> allocations,String productName,String sku,String unit) {}
