package io.arhome.inventory.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public final class InventoryIdentityModels {

    private InventoryIdentityModels() {
    }

    public enum IdentifierType {
        GTIN, EAN, UPC, SERIAL, MANUFACTURER_PART_NUMBER, MAC_ADDRESS, MATTER_SETUP, QR_PAYLOAD, OTHER
    }

    public enum BarcodeSymbology {
        EAN_13, UPC_A, CODE_128, GS1_128, DATA_MATRIX, QR_CODE, NONE, UNKNOWN
    }

    public enum IdentifierSource {
        SCANNED, MANUAL, IMPORTED
    }

    public enum Sensitivity {
        PUBLIC, PRIVATE, SECRET
    }

    public enum DocumentType {
        MANUAL, QUICK_START, DATASHEET, WARRANTY, INVOICE, RECEIPT, PAIRING_LABEL, PHOTO, OTHER
    }

    public record IdentifierValue(
            String rawValue,
            String normalizedValue,
            Sensitivity sensitivity) {

        public IdentifierValue {
            requireText(rawValue, "rawValue");
            Objects.requireNonNull(sensitivity, "sensitivity");
            normalizedValue = normalizeOptional(normalizedValue);
        }

        @Override
        public String toString() {
            return sensitivity == Sensitivity.PUBLIC ? rawValue : "[REDACTED]";
        }
    }

    public record ItemIdentifier(
            UUID id,
            UUID itemId,
            UUID unitId,
            IdentifierType type,
            BarcodeSymbology symbology,
            IdentifierValue value,
            IdentifierSource source,
            boolean verified,
            Instant capturedAt,
            Instant createdAt,
            Instant updatedAt) {

        public ItemIdentifier {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(itemId, "itemId");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(symbology, "symbology");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(source, "source");
            requireTimestamps(createdAt, updatedAt);
            if (capturedAt != null && capturedAt.isAfter(updatedAt)) {
                throw new IllegalArgumentException("capturedAt must not be after updatedAt");
            }
        }

        public Sensitivity sensitivity() {
            return value.sensitivity();
        }
    }

    public record ItemDocument(
            UUID id,
            UUID itemId,
            UUID unitId,
            DocumentType type,
            String storageReference,
            String mimeType,
            String language,
            String version,
            String sha256,
            Sensitivity sensitivity,
            Instant createdAt,
            Instant updatedAt) {

        public ItemDocument {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(itemId, "itemId");
            Objects.requireNonNull(type, "type");
            requireText(storageReference, "storageReference");
            requireText(mimeType, "mimeType");
            language = normalizeOptional(language);
            version = normalizeOptional(version);
            sha256 = normalizeOptional(sha256);
            Objects.requireNonNull(sensitivity, "sensitivity");
            requireTimestamps(createdAt, updatedAt);
            if (sha256 != null && !sha256.matches("(?i)[0-9a-f]{64}")) {
                throw new IllegalArgumentException("sha256 must be 64 hexadecimal characters");
            }
        }
    }

    public static String normalizeIdentifier(IdentifierType type, String value) {
        requireText(value, "value");
        Objects.requireNonNull(type, "type");
        String trimmed = value.trim();
        return switch (type) {
            case GTIN, EAN, UPC -> trimmed.replaceAll("[^0-9]", "");
            case MAC_ADDRESS -> trimmed.replaceAll("[^0-9A-Fa-f]", "").toUpperCase(Locale.ROOT);
            default -> trimmed;
        };
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
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
