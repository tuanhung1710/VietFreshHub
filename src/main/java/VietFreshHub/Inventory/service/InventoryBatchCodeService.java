package VietFreshHub.Inventory.service;

import VietFreshHub.Inventory.repository.InventoryBatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/** Creates a lot identifier once. Editing the lot's dates never changes its code. */
@Service @RequiredArgsConstructor
public class InventoryBatchCodeService {
    private final InventoryBatchRepository batches;

    public String generateCode(Long variantId,LocalDate receivedDate) {
        LocalDate date=receivedDate==null?InventoryRules.today():receivedDate;
        String prefix="LOT-"+date.format(DateTimeFormatter.BASIC_ISO_DATE)+"-";
        String code;
        do {
            code=prefix+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);
        } while(batches.existsByVariantIdAndBatchCodeIgnoreCase(variantId,code));
        return code;
    }
}
