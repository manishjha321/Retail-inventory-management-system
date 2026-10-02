package com.manish.retailinventory.repository;

import com.manish.retailinventory.entity.Product;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsBySkuIgnoreCase(String sku);

    boolean existsByCategoryId(Long categoryId);

    long countByActiveTrue();

    /** q must be lower-case ("" = no text filter); categoryId 0 = all categories. */
    @Query("""
        select p from Product p
        where p.active = true
          and (:cid = 0L or p.category.id = :cid)
          and (:q = '' or lower(p.name) like concat('%', :q, '%') or lower(p.sku) like concat('%', :q, '%'))
        """)
    Page<Product> search(@Param("q") String q, @Param("cid") long categoryId, Pageable pageable);

    /** Rows: id, sku, name, reorderLevel, totalAvailable - for products at or below reorder level. */
    @Query("""
        select p.id, p.sku, p.name, p.reorderLevel, coalesce(sum(i.quantity - i.reservedQuantity), 0)
        from Product p left join Inventory i on i.product = p
        where p.active = true
        group by p.id, p.sku, p.name, p.reorderLevel
        having coalesce(sum(i.quantity - i.reservedQuantity), 0) <= p.reorderLevel
        order by p.name
        """)
    List<Object[]> findLowStockRows();
}
