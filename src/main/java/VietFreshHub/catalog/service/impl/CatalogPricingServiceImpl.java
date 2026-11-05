package VietFreshHub.catalog.service.impl;

import VietFreshHub.catalog.dto.CatalogOffer;
import VietFreshHub.catalog.repository.CatalogOfferRepository;
import VietFreshHub.catalog.service.CatalogPricingService;
import VietFreshHub.inventory.rule.InventoryRules;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogPricingServiceImpl implements CatalogPricingService {
    private final CatalogOfferRepository offers;

    @Override
    public Map<Long, CatalogOffer> offers(List<Long> variantIds) {
        if (variantIds.isEmpty()) {
            return Map.of();
        }
        return offers.findOffers(variantIds, InventoryRules.today());
    }
}
