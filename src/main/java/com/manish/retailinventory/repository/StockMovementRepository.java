package com.manish.retailinventory.repository;

import com.manish.retailinventory.entity.*;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    @Query("select m from StockMovement m where (:pid = 0L or m.product.id = :pid)")
    Page<StockMovement> search(@Param("pid") long productId, Pageable pageable);

    List<StockMovement> findTop10ByOrderByCreatedAtDesc();
}
