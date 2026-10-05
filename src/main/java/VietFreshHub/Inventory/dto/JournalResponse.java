package VietFreshHub.Inventory.dto;
import VietFreshHub.Inventory.entity.InventoryTransaction;
public record JournalResponse(InventoryTransaction event,String productName,String sku,String batchCode) {}
