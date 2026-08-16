package com.demomart.order.purchase;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    List<PurchaseOrder> findByUserIdOrderByCreatedAtDesc(Long userId);
}

