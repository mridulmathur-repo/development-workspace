package com.example.googlesso.web;

import com.example.googlesso.inventory.InventoryItem;
import com.example.googlesso.inventory.InventoryItemRepository;
import com.example.googlesso.service.CurrentUserService;
import com.example.googlesso.user.AppUser;
import java.time.Instant;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class InventoryController {
    private final InventoryItemRepository inventory;
    private final CurrentUserService currentUsers;

    public InventoryController(InventoryItemRepository inventory, CurrentUserService currentUsers) {
        this.inventory = inventory;
        this.currentUsers = currentUsers;
    }

    @GetMapping("/inventory")
    String list(@AuthenticationPrincipal OAuth2User principal, Model model) {
        AppUser user = currentUsers.requireUser(principal);
        model.addAttribute("user", user);
        model.addAttribute("items", inventory.findAllByOrderByNameAsc());
        return "inventory-list";
    }

    @GetMapping("/inventory/add")
    String addForm(@AuthenticationPrincipal OAuth2User principal) {
        currentUsers.requireAdmin(principal);
        return "inventory-add";
    }

    @PostMapping("/inventory/add")
    String add(@AuthenticationPrincipal OAuth2User principal,
               @RequestParam String sku,
               @RequestParam String name,
               @RequestParam int quantity,
               @RequestParam(required = false, defaultValue = "") String description,
               Model model) {
        AppUser admin = currentUsers.requireAdmin(principal);
        if (sku.isBlank() || name.isBlank() || quantity < 0) {
            model.addAttribute("error", "SKU and name are required, and quantity cannot be negative.");
            return "inventory-add";
        }
        if (inventory.existsBySkuIgnoreCase(sku.trim())) {
            model.addAttribute("error", "That SKU already exists.");
            return "inventory-add";
        }
        inventory.save(new InventoryItem(sku.trim(), name.trim(), quantity,
                description.trim(), Instant.now(), admin.getEmail()));
        return "redirect:/inventory?added";
    }
}
