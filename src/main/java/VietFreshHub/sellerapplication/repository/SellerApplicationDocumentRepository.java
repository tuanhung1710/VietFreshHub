package VietFreshHub.sellerapplication.repository;

import VietFreshHub.sellerapplication.entity.SellerApplicationDocument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerApplicationDocumentRepository
        extends JpaRepository<SellerApplicationDocument, Long> {
}