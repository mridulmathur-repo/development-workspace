package com.workflowautomation.jiraplanner.planning;

import java.util.List;

public class PlanValidationException extends RuntimeException {

    private final List<String> violations;

    public PlanValidationException(List<String> violations) {
        super(String.join("; ", violations));
        this.violations = List.copyOf(violations);
    }

    public List<String> getViolations() {
        return violations;
    }
}
