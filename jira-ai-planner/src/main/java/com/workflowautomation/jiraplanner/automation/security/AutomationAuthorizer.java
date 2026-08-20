package com.workflowautomation.jiraplanner.automation.security;

import com.workflowautomation.jiraplanner.config.AutomationProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class AutomationAuthorizer {

    private final AutomationProperties properties;

    public AutomationAuthorizer(AutomationProperties properties) {
        this.properties = properties;
    }

    public void requireAuthorized(String suppliedToken) {
        if (!properties.isEnabled()) {
            throw new AutomationAccessException("MVP2 automation is disabled");
        }
        String expected = properties.getControlToken();
        if (expected == null || expected.isBlank()) {
            throw new AutomationAccessException("AUTOMATION_CONTROL_TOKEN must be configured before enabling MVP2");
        }
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] suppliedBytes = (suppliedToken == null ? "" : suppliedToken).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedBytes, suppliedBytes)) {
            throw new AutomationAccessException("Invalid automation control token");
        }
    }
}
