package io.arhome.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.arhome.inventory.domain.InventoryModels.Availability;
import io.arhome.inventory.domain.InventoryModels.InventoryItem;
import io.arhome.inventory.domain.InventoryModels.ItemCondition;
import io.arhome.inventory.domain.InventoryModels.Loan;
import io.arhome.inventory.domain.InventoryModels.LoanDirection;
import io.arhome.inventory.domain.InventoryModels.StockPolicy;
import io.arhome.inventory.domain.InventoryModels.TrackingMode;

class InventoryModelsTest {

    private static final Instant NOW = Instant.parse("2026-08-31T00:00:00Z");

    @Test
    void rejectsInvalidMinMaxPolicy() {
        assertThatThrownBy(() -> new StockPolicy(decimal("5"), decimal("4"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minStock");
    }

    @Test
    void copiesTagsToKeepItemImmutable() {
        var tags = new java.util.HashSet<>(Set.of("12V"));
        InventoryItem item = new InventoryItem(
                UUID.randomUUID(), UUID.randomUUID(), "Taladro", TrackingMode.SERIALIZED, "unit",
                new StockPolicy(BigDecimal.ZERO, decimal("2"), decimal("1")),
                "Bosch", "GSB", null, null, null, tags, null, NOW, NOW);

        tags.add("changed");

        assertThat(item.tags()).containsExactly("12V");
    }

    @Test
    void conditionAndAvailabilityRemainIndependent() {
        var unit = new InventoryModels.InventoryUnit(
                UUID.randomUUID(), UUID.randomUUID(), "SN-1",
                ItemCondition.DAMAGED, Availability.AVAILABLE,
                null, null, null, null, NOW, NOW);

        assertThat(unit.condition()).isEqualTo(ItemCondition.DAMAGED);
        assertThat(unit.availability()).isEqualTo(Availability.AVAILABLE);
    }

    @Test
    void serialLoanIsActiveUntilReturned() {
        Loan loan = new Loan(
                UUID.randomUUID(), LoanDirection.OUTGOING, UUID.randomUUID(), UUID.randomUUID(), null,
                "Carlos", NOW, NOW.plusSeconds(86400), null, null, NOW, NOW);

        assertThat(loan.active()).isTrue();
    }

    @Test
    void rejectsLoanWithUnitAndBulkQuantityAtTheSameTime() {
        assertThatThrownBy(() -> new Loan(
                UUID.randomUUID(), LoanDirection.OUTGOING, UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ONE,
                "Carlos", NOW, null, null, null, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("either one unit");
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
