package io.github.cococzl.coquartz.service;

import java.util.Set;

/** Operator-supplied proof of stopped nodes, scoped to one application and scheduler. */
public record AuditRecoveryScope(String applicationNamespace, String schedulerName,
                                 Set<String> confirmedStoppedInstances, String evidenceReference) {
    public AuditRecoveryScope {
        if (applicationNamespace == null || applicationNamespace.isBlank() || schedulerName == null
                || schedulerName.isBlank() || evidenceReference == null || evidenceReference.isBlank()) {
            throw new IllegalArgumentException("Recovery requires application, scheduler and stopped-node evidence");
        }
        confirmedStoppedInstances = Set.copyOf(confirmedStoppedInstances);
        if (confirmedStoppedInstances.stream().anyMatch(String::isBlank)) throw new IllegalArgumentException("Blank node identity");
    }
}
