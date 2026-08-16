package com.demomart.order.purchase;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final PurchaseOrderRepository orders;
    private final RestClient catalog;
    private final String internalServiceKey;

    public OrderController(PurchaseOrderRepository orders,
                           @LoadBalanced RestClient.Builder clientBuilder,
                           @Value("${app.internal-service-key}") String internalServiceKey) {
        this.orders = orders;
        this.catalog = clientBuilder.baseUrl("http://catalog-service").build();
        this.internalServiceKey = internalServiceKey;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PurchaseOrder place(@AuthenticationPrincipal Jwt jwt,
                        @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                        @Valid @RequestBody PlaceOrderRequest request) {
        PhoneView phone;
        try {
            phone = catalog.post().uri("/api/phones/{id}/reserve", request.phoneId())
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .header("X-Internal-Service-Key", internalServiceKey)
                    .retrieve().body(PhoneView.class);
        } catch (HttpClientErrorException.Conflict exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The selected phone is out of stock");
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Catalog service is unavailable", exception);
        }
        if (phone == null) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Catalog returned no phone");
        PurchaseOrder order = new PurchaseOrder(Long.valueOf(jwt.getSubject()), jwt.getClaimAsString("email"),
                phone.id(), phone.brand() + " " + phone.model(), phone.price(), request.addressLine1(),
                request.addressLine2(), request.city(), request.postalCode(), request.country());
        return orders.save(order);
    }

    @GetMapping("/mine")
    List<PurchaseOrder> mine(@AuthenticationPrincipal Jwt jwt) {
        return orders.findByUserIdOrderByCreatedAtDesc(Long.valueOf(jwt.getSubject()));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    List<PurchaseOrder> all() { return orders.findAll(); }

    public record PlaceOrderRequest(@NotNull Long phoneId, @NotBlank String addressLine1,
                                    String addressLine2, @NotBlank String city,
                                    @NotBlank String postalCode, @NotBlank String country) {}
    public record PhoneView(Long id, String brand, String model, String description,
                            BigDecimal price, int stock, String color, String storage) {}
}
