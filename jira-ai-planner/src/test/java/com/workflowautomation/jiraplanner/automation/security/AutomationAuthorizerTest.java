package com.workflowautomation.jiraplanner.automation.security;

import com.workflowautomation.jiraplanner.config.AutomationProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AutomationAuthorizerTest {

    @Test
    void requiresBothExplicitEnablementAndMatchingControlToken() {
        AutomationProperties properties = new AutomationProperties();
        properties.setEnabled(true);
        properties.setControlToken("a-long-control-token");
        AutomationAuthorizer authorizer = new AutomationAuthorizer(properties);

        assertThatCode(() -> authorizer.requireAuthorized("a-long-control-token")).doesNotThrowAnyException();
        assertThatThrownBy(() -> authorizer.requireAuthorized("wrong"))
                .isInstanceOf(AutomationAccessException.class);

        properties.setEnabled(false);
        assertThatThrownBy(() -> authorizer.requireAuthorized("a-long-control-token"))
                .isInstanceOf(AutomationAccessException.class)
                .hasMessageContaining("disabled");
    }
}
