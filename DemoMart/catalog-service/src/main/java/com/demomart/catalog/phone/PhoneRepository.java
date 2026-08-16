package com.demomart.catalog.phone;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PhoneRepository extends JpaRepository<Phone, Long> {
    @Query("select p from Phone p where lower(p.brand) like lower(concat('%', :search, '%')) or lower(p.model) like lower(concat('%', :search, '%')) order by p.brand, p.model")
    List<Phone> search(@Param("search") String search);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Phone p where p.id = :id")
    Optional<Phone> findLockedById(@Param("id") Long id);
}

