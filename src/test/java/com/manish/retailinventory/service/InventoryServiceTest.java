package com.manish.retailinventory.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.manish.retailinventory.dto.InventoryDtos.AdjustRequest;
import com.manish.retailinventory.dto.InventoryDtos.TransferRequest;
import com.manish.retailinventory.entity.Inventory;
import com.manish.retailinventory.entity.Product;
import com.manish.retailinventory.entity.StockMovement;
import com.manish.retailinventory.entity.Warehouse;
import com.manish.retailinventory.exception.BusinessRuleException;
import com.manish.retailinventory.repository.InventoryRepository;
import com.manish.retailinventory.repository.ProductRepository;
import com.manish.retailinventory.repository.StockMovementRepository;
import com.manish.retailinventory.repository.WarehouseRepository;
import com.manish.retailinventory.security.CurrentUserService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {
    @Mock InventoryRepository inventoryRepo;
    @Mock StockMovementRepository movementRepo;
    @Mock ProductRepository productRepo;
    @Mock WarehouseRepository warehouseRepo;
    @Mock CurrentUserService currentUser;

    InventoryService service;
    Product product;
    Warehouse wh1;
    Warehouse wh2;

    @BeforeEach
    void setUp() {
        service = new InventoryService(inventoryRepo, movementRepo, productRepo, warehouseRepo, currentUser);
        product = new Product();
        product.setId(1L);
        product.setSku("LAP-001");
        product.setActive(true);
        wh1 = warehouse(1L, "WH-DEL");
        wh2 = warehouse(2L, "WH-NOI");
    }

    private Warehouse warehouse(Long id, String code) {
        Warehouse w = new Warehouse();
        w.setId(id);
        w.setCode(code);
        w.setName(code);
        w.setActive(true);
        return w;
    }

    private Inventory stock(Warehouse w, int qty, int reserved) {
        Inventory i = new Inventory();
        i.setProduct(product);
        i.setWarehouse(w);
        i.setQuantity(qty);
        i.setReservedQuantity(reserved);
        return i;
    }

    @Test
    void reserveIncreasesReservedQuantityAndReturnsWarehouse() {
        Inventory inv = stock(wh1, 50, 10);
        when(inventoryRepo.findCandidatesForUpdate(1L, 5)).thenReturn(List.of(inv));

        Warehouse result = service.reserve(product, 5, 99L);

        assertEquals(wh1, result);
        assertEquals(15, inv.getReservedQuantity());
        assertEquals(40 - 5, inv.available());
        verify(movementRepo).save(any(StockMovement.class));
    }

    @Test
    void reserveFailsWhenNoWarehouseHasEnoughStock() {
        when(inventoryRepo.findCandidatesForUpdate(1L, 500)).thenReturn(List.of());
        assertThrows(BusinessRuleException.class, () -> service.reserve(product, 500, 99L));
    }

    @Test
    void shipReducesQuantityAndReservation() {
        Inventory inv = stock(wh1, 50, 10);
        when(inventoryRepo.findForUpdate(1L, 1L)).thenReturn(Optional.of(inv));

        service.ship(product, wh1, 10, 99L);

        assertEquals(40, inv.getQuantity());
        assertEquals(0, inv.getReservedQuantity());
    }

    @Test
    void adjustBelowReservedIsRejected() {
        Inventory inv = stock(wh1, 20, 15);
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));
        when(warehouseRepo.findById(1L)).thenReturn(Optional.of(wh1));
        when(inventoryRepo.findForUpdate(1L, 1L)).thenReturn(Optional.of(inv));

        assertThrows(BusinessRuleException.class,
                () -> service.adjust(new AdjustRequest(1L, 1L, -10, "damaged")));
        assertEquals(20, inv.getQuantity());
    }

    @Test
    void transferMovesStockBetweenWarehouses() {
        Inventory src = stock(wh1, 30, 0);
        Inventory dst = stock(wh2, 5, 0);
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));
        when(warehouseRepo.findById(1L)).thenReturn(Optional.of(wh1));
        when(warehouseRepo.findById(2L)).thenReturn(Optional.of(wh2));
        when(inventoryRepo.findForUpdate(1L, 1L)).thenReturn(Optional.of(src));
        when(inventoryRepo.findForUpdate(1L, 2L)).thenReturn(Optional.of(dst));

        service.transfer(new TransferRequest(1L, 1L, 2L, 12));

        assertEquals(18, src.getQuantity());
        assertEquals(17, dst.getQuantity());
    }

    @Test
    void transferToSameWarehouseIsRejected() {
        assertThrows(BusinessRuleException.class, () -> service.transfer(new TransferRequest(1L, 1L, 1L, 1)));
        verify(inventoryRepo, never()).findForUpdate(anyLong(), anyLong());
    }
}
