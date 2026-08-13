package com.example.googlesso.inventory;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {
    List<InventoryItem> findAllByOrderByNameAsc();
    boolean existsBySkuIgnoreCase(String sku);
}
