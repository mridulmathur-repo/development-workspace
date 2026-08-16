package com.demomart.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:catalog-test",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false"
})
class CatalogServiceApplicationTests {
    @Test void contextLoads() {}
}

