package com.manish.retailinventory.repository;

import com.manish.retailinventory.entity.*;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {
    Page<SalesOrder> findByStatus(OrderStatus status, Pageable pageable);

    Optional<SalesOrder> findByOrderNumberIgnoreCase(String orderNumber);

    /** Rows: sku, name, units sold, revenue - ignores orders with the excluded status (CANCELLED). */
    @Query("""
        select p.sku, p.name, sum(i.quantity), sum(i.quantity * i.unitPrice)
        from SalesOrderItem i join i.product p join i.order o
        where o.status <> :excluded
        group by p.sku, p.name
        order by sum(i.quantity) desc
        """)
    List<Object[]> topSelling(@Param("excluded") OrderStatus excluded, Pageable pageable);

    @Query("select o.status, count(o) from SalesOrder o group by o.status")
    List<Object[]> countByStatusRows();
}
