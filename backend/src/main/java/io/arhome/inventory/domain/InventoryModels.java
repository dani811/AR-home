package io.arhome.inventory.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class InventoryModels {

    private InventoryModels() {
    }

    public enum TrackingMode { BULK, SERIALIZED }
    public enum ItemCondition { NEW, GOOD, WORN, DAMAGED, BROKEN, UNDER_REPAIR, UNTESTED }
    public enum Availability { AVAILABLE, RESERVED, IN_USE, LOANED, LOST, DISPOSED }
    public enum RelationType { COMPONENT_OF, PART_OF_SET, ACCESSORY_OF, CONSUMABLE_FOR, SPARE_FOR, COMPATIBLE_WITH }
    public enum LoanDirection { OUTGOING, INCOMING }

    public record InventoryCategory(
            UUID id,
            UUID parentId,
            String name,
            Instant createdAt,
            Instant updatedAt) {

        public InventoryCategory {
            Objects.requireNonNull(id, "id");
            requireText(name, "name");
            requireTimestamps(createdAt, updatedAt);
            if (id.equals(parentId)) {
                throw new IllegalArgumentException("category cannot be its own parent");
            }
        }
    }

    public record StockPolicy(
            BigDecimal minStock,
            BigDecimal maxStock,
            BigDecimal reorderPoint) {

        public StockPolicy {
            requireNonNegative(minStock, "minStock");
            requireNonNegative(maxStock, "maxStock");
            requireNonNegative(reorderPoint, "reorderPoint");
            if (minStock != null && maxStock != null && minStock.compareTo(maxStock) > 0) {
                throw new IllegalArgumentException("minStock must be <= maxStock");
            }
            if (reorderPoint != null && maxStock != null && reorderPoint.compareTo(maxStock) > 0) {
                throw new IllegalArgumentException("reorderPoint must be <= maxStock");
            }
        }
    }

    public record InventoryItem(
            UUID id,
            UUID categoryId,
            String name,
            TrackingMode trackingMode,
            String unitOfMeasure,
            StockPolicy stockPolicy,
            String brand,
            String model,
            String manufacturerPartNumber,
            String internalSku,
            String gtin,
            Set<String> tags,
            String notes,
            Instant createdAt,
            Instant updatedAt) {

        public InventoryItem {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(categoryId, "categoryId");
            requireText(name, "name");
            Objects.requireNonNull(trackingMode, "trackingMode");
            requireText(unitOfMeasure, "unitOfMeasure");
            stockPolicy = stockPolicy == null ? new StockPolicy(null, null, null) : stockPolicy;
            tags = tags == null ? Set.of() : Set.copyOf(tags);
            if (tags.stream().anyMatch(tag -> tag == null || tag.isBlank())) {
                throw new IllegalArgumentException("tags must not contain blank values");
            }
            requireTimestamps(createdAt, updatedAt);
        }
    }

    public record InventoryUnit(
            UUID id,
            UUID itemId,
            String serialNumber,
            ItemCondition condition,
            Availability availability,
            LocalDate acquiredOn,
            BigDecimal purchasePrice,
            LocalDate warrantyUntil,
            String notes,
            Instant createdAt,
            Instant updatedAt) {

        public InventoryUnit {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(itemId, "itemId");
            Objects.requireNonNull(condition, "condition");
            Objects.requireNonNull(availability, "availability");
            requireNonNegative(purchasePrice, "purchasePrice");
            requireTimestamps(createdAt, updatedAt);
        }
    }

    public record ItemRelation(
            UUID id,
            UUID sourceItemId,
            UUID targetItemId,
            RelationType type,
            BigDecimal requiredQuantity,
            Instant createdAt) {

        public ItemRelation {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(sourceItemId, "sourceItemId");
            Objects.requireNonNull(targetItemId, "targetItemId");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(createdAt, "createdAt");
            requirePositive(requiredQuantity, "requiredQuantity");
            if (sourceItemId.equals(targetItemId)) {
                throw new IllegalArgumentException("relation cannot target the same item");
            }
        }
    }

    public record Loan(
            UUID id,
            LoanDirection direction,
            UUID itemId,
            UUID unitId,
            BigDecimal quantity,
            String counterpartyDisplayName,
            Instant lentAt,
            Instant expectedReturnAt,
            Instant returnedAt,
            String notes,
            Instant createdAt,
            Instant updatedAt) {

        public Loan {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(itemId, "itemId");
            requireText(counterpartyDisplayName, "counterpartyDisplayName");
            Objects.requireNonNull(lentAt, "lentAt");
            requireTimestamps(createdAt, updatedAt);
            if ((unitId == null) == (quantity == null)) {
                throw new IllegalArgumentException("loan must reference either one unit or a bulk quantity");
            }
            requirePositive(quantity, "quantity");
            if (expectedReturnAt != null && expectedReturnAt.isBefore(lentAt)) {
                throw new IllegalArgumentException("expectedReturnAt must not be before lentAt");
            }
            if (returnedAt != null && returnedAt.isBefore(lentAt)) {
                throw new IllegalArgumentException("returnedAt must not be before lentAt");
            }
        }

        public boolean active() {
            return returnedAt == null;
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static void requireNonNegative(BigDecimal value, String field) {
        if (value != null && value.signum() < 0) {
            throw new IllegalArgumentException(field + " must be >= 0");
        }
    }

    private static void requirePositive(BigDecimal value, String field) {
        if (value != null && value.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be > 0");
        }
    }

    private static void requireTimestamps(Instant createdAt, Instant updatedAt) {
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }
}
