package VietFreshHub.sellerapplication.service;

import VietFreshHub.mail.service.MailTemplateService;
import VietFreshHub.sellerapplication.event.SellerApplicationApprovedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class SellerApplicationApprovalEmailListener {

    private final MailTemplateService mailTemplateService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendApprovalEmail(SellerApplicationApprovedEvent event) {
        try {
            mailTemplateService.sendSellerApplicationApproved(
                    event.recipientEmail(),
                    event.recipientName(),
                    event.businessName()
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "Could not send seller application approval email for applicationId={}",
                    event.applicationId(),
                    exception
            );
        }
    }
}
