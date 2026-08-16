package com.demomart.identity;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:identity-test",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false",
        "app.seed-demo-users=false"
})
class IdentityServiceApplicationTests {
    @Test void contextLoads() {}
}

