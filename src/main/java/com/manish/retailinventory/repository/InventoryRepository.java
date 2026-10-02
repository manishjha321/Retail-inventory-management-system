package com.manish.retailinventory.repository;

import com.manish.retailinventory.entity.*;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    boolean existsByWarehouseId(Long warehouseId);

    /** Row lock so two requests cannot change the same stock row at once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i where i.product.id = :pid and i.warehouse.id = :wid")
    Optional<Inventory> findForUpdate(@Param("pid") Long productId, @Param("wid") Long warehouseId);

    @Query("""
        select i from Inventory i join fetch i.product p join fetch i.warehouse w
        where (:pid = 0L or p.id = :pid) and (:wid = 0L or w.id = :wid)
        order by p.name, w.name
        """)
    List<Inventory> search(@Param("pid") long productId, @Param("wid") long warehouseId);

    /** Active warehouses that can cover qty, most available first. Locked for the reservation. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select i from Inventory i
        where i.product.id = :pid
          and i.warehouse.id in (select w.id from Warehouse w where w.active = true)
          and (i.quantity - i.reservedQuantity) >= :qty
        order by (i.quantity - i.reservedQuantity) desc
        """)
    List<Inventory> findCandidatesForUpdate(@Param("pid") Long productId, @Param("qty") int qty);

    @Query("select coalesce(sum(i.quantity - i.reservedQuantity), 0) from Inventory i where i.product.id = :pid")
    long totalAvailable(@Param("pid") Long productId);

    @Query("select coalesce(sum(i.quantity), 0) from Inventory i")
    long totalUnits();

    /** Rows: warehouse name, units on hand, stock value at cost price. */
    @Query("""
        select w.name, coalesce(sum(i.quantity), 0), sum(i.quantity * p.costPrice)
        from Inventory i join i.product p join i.warehouse w
        group by w.name order by w.name
        """)
    List<Object[]> stockByWarehouse();
}
