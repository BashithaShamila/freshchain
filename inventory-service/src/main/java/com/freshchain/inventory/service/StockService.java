package com.freshchain.inventory.service;

import com.freshchain.inventory.domain.InventoryLot;
import com.freshchain.inventory.domain.Product;
import com.freshchain.inventory.repository.InventoryLotRepository;
import com.freshchain.inventory.repository.ProductRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Receiving, write-offs and availability reads. */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockService {

    private final InventoryLotRepository lots;
    private final ProductRepository products;

    /** Stock arrives at the dock. One receipt, one lot, one expiry date. */
    @Transactional
    public InventoryLot receive(String sku, String warehouseId, int qty, LocalDate expiryDate) {
        Product product = products.findById(sku)
                .orElseThrow(() -> new NoSuchElementException("unknown sku " + sku));
        if (!expiryDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "expiry date " + expiryDate + " is not in the future; this stock is already unsellable");
        }
        InventoryLot lot = lots.save(InventoryLot.receive(sku, warehouseId, qty, expiryDate));
        log.info("received lot {} of {} x{} into {} expiring {}",
                lot.getLotId(), product.getSku(), qty, warehouseId, expiryDate);
        return lot;
    }

    /**
     * Shrinkage, damage or a recount. A negative delta cannot push on-hand below
     * what is already reserved — that stock is promised to an order, and the
     * honest response is to fail the write-off and force someone to look at it.
     */
    @Transactional
    public InventoryLot adjust(UUID lotId, int delta, String reason) {
        InventoryLot lot = lots.lockById(lotId)
                .orElseThrow(() -> new NoSuchElementException("unknown lot " + lotId));
        int before = lot.getQtyOnHand();
        lot.adjust(delta);
        log.info("adjusted lot {} from {} to {} ({}): {}",
                lotId, before, lot.getQtyOnHand(), delta > 0 ? "+" + delta : delta, reason);
        return lot;
    }

    @Transactional(readOnly = true)
    public List<InventoryLot> lotsFor(String sku, String warehouseId) {
        if (!products.existsById(sku)) {
            throw new NoSuchElementException("unknown sku " + sku);
        }
        return lots.findForAvailability(sku, warehouseId);
    }

    @Transactional(readOnly = true)
    public Product product(String sku) {
        return products.findById(sku)
                .orElseThrow(() -> new NoSuchElementException("unknown sku " + sku));
    }
}
