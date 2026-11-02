package VietFreshHub.inventory.enums;

public enum InventoryAlertType {
    LOW_STOCK("INVENTORY_VARIANT"),
    NEAR_EXPIRY("INVENTORY_BATCH"),
    EXPIRED_STOCK("INVENTORY_BATCH");

    private final String entityType;

    InventoryAlertType(String entityType) {
        this.entityType = entityType;
    }

    public String entityType() {
        return entityType;
    }
}
