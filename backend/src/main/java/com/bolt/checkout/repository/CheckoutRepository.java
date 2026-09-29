package com.bolt.checkout.repository;

import com.bolt.checkout.entity.CheckoutRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CheckoutRepository extends JpaRepository<CheckoutRecord, Long> {
    List<CheckoutRecord> findByUserId(Long userId);

    /**
     * Newest first, paged. The explicit sort matters: without it the database is free to
     * return rows in any order, which makes paging over a growing history non-deterministic.
     */
    Page<CheckoutRecord> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
