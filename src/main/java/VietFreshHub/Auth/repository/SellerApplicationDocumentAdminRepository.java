package VietFreshHub.Auth.repository;

import VietFreshHub.Auth.entity.SellerApplicationDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SellerApplicationDocumentAdminRepository
        extends JpaRepository<SellerApplicationDocument, Long> {

    List<SellerApplicationDocument> findByApplication_ApplicationIdOrderByUploadedAtAsc(Long applicationId);

    Optional<SellerApplicationDocument> findByDocumentIdAndApplication_ApplicationId(
            Long documentId,
            Long applicationId
    );
}
