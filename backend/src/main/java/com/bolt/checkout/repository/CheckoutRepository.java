package com.bolt.checkout.repository;

import com.bolt.checkout.entity.CheckoutRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CheckoutRepository extends JpaRepository<CheckoutRecord, Long> {
    List<CheckoutRecord> findByUserId(Long userId);
}