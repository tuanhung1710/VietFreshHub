package VietFreshHub.order.dto;
import java.util.List;
public record ReorderResult(int addedLines,long addedUnits,List<SkippedItem> skipped) {
    public record SkippedItem(String productName,String variantName,String reason) implements java.io.Serializable {}
}
