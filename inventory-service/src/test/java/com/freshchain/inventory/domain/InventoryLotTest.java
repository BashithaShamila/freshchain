package com.freshchain.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** The invariants the entity refuses to break, before the database has to. */
class InventoryLotTest {

    private static final LocalDate NEXT_MONTH = LocalDate.now().plusMonths(1);

    @Test
    void reservingBeyondWhatIsAvailableIsRefused() {
        InventoryLot lot = InventoryLot.receive("CHK-BRST-5LB", "WH-COL-01", 10, NEXT_MONTH);
        lot.reserve(8);

        assertThatThrownBy(() -> lot.reserve(3))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("has 2 available but 3 were requested");

        assertThat(lot.getQtyReserved()).isEqualTo(8);
    }

    @Test
    void releasingMoreThanIsHeldIsRefused() {
        InventoryLot lot = InventoryLot.receive("CHK-BRST-5LB", "WH-COL-01", 10, NEXT_MONTH);
        lot.reserve(4);

        assertThatThrownBy(() -> lot.release(5)).isInstanceOf(InsufficientStockException.class);
        assertThat(lot.getQtyReserved()).isEqualTo(4);
    }

    @Test
    void shippingDropsOnHandAndReservedTogether() {
        InventoryLot lot = InventoryLot.receive("CHK-BRST-5LB", "WH-COL-01", 10, NEXT_MONTH);
        lot.reserve(6);

        lot.consume(6);

        assertThat(lot.getQtyOnHand()).isEqualTo(4);
        assertThat(lot.getQtyReserved()).isZero();
        assertThat(lot.available()).isEqualTo(4);
    }

    @Test
    void aWriteOffCannotEatIntoStockThatIsAlreadyPromised() {
        InventoryLot lot = InventoryLot.receive("CHK-BRST-5LB", "WH-COL-01", 10, NEXT_MONTH);
        lot.reserve(7);

        assertThatThrownBy(() -> lot.adjust(-5))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("already reserved");

        assertThat(lot.getQtyOnHand()).isEqualTo(10);
    }

    @Test
    void aWriteOffIsAllowedDownToTheReservedFloor() {
        InventoryLot lot = InventoryLot.receive("CHK-BRST-5LB", "WH-COL-01", 10, NEXT_MONTH);
        lot.reserve(7);

        lot.adjust(-3);

        assertThat(lot.getQtyOnHand()).isEqualTo(7);
        assertThat(lot.available()).isZero();
    }

    @Test
    void receivingStockWithNoQuantityIsRefused() {
        assertThatThrownBy(() -> InventoryLot.receive("CHK-BRST-5LB", "WH-COL-01", 0, NEXT_MONTH))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
