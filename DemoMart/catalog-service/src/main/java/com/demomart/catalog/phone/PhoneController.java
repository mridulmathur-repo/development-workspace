package com.demomart.catalog.phone;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/phones")
public class PhoneController {
    private final PhoneRepository phones;
    private final String internalServiceKey;

    public PhoneController(PhoneRepository phones,
                           @Value("${app.internal-service-key}") String internalServiceKey) {
        this.phones = phones;
        this.internalServiceKey = internalServiceKey;
    }

    @GetMapping
    List<PhoneView> list(@RequestParam(defaultValue = "") String search) {
        List<Phone> result = search.isBlank() ? phones.findAll() : phones.search(search.trim());
        return result.stream().map(PhoneView::from).toList();
    }

    @GetMapping("/{id}")
    PhoneView one(@PathVariable Long id) { return PhoneView.from(find(id)); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('INVENTORY_MANAGER','ADMIN')")
    PhoneView add(@Valid @RequestBody PhoneRequest request) {
        Phone phone = new Phone(request.brand(), request.model(), request.description(), request.price(),
                request.stock(), request.color(), request.storage());
        return PhoneView.from(phones.save(phone));
    }

    @PostMapping("/{id}/reserve")
    @Transactional
    PhoneView reserve(@PathVariable Long id,
                      @RequestHeader(value = "X-Internal-Service-Key", required = false) String suppliedKey) {
        if (!internalServiceKey.equals(suppliedKey)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This operation is reserved for internal services");
        }
        Phone phone = phones.findLockedById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Phone not found"));
        if (phone.getStock() <= 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Phone is out of stock");
        phone.reserveOne();
        return PhoneView.from(phone);
    }

    private Phone find(Long id) {
        return phones.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Phone not found"));
    }

    public record PhoneRequest(@NotBlank String brand, @NotBlank String model, @NotBlank String description,
                               @DecimalMin("0.01") BigDecimal price, @Min(0) int stock,
                               String color, String storage) {}
    public record PhoneView(Long id, String brand, String model, String description, BigDecimal price,
                            int stock, String color, String storage) {
        static PhoneView from(Phone phone) {
            return new PhoneView(phone.getId(), phone.getBrand(), phone.getModel(), phone.getDescription(),
                    phone.getPrice(), phone.getStock(), phone.getColor(), phone.getStorage());
        }
    }
}
