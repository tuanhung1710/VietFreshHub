package VietFreshHub.inventory.dto;

import VietFreshHub.inventory.entity.AuditEvent;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AuditResponse {

    private AuditEvent event;
    private String actionLabel;
    private String entityLabel;
    private String actorLabel;
}
