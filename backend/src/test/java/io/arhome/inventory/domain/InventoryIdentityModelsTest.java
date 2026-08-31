package io.arhome.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.arhome.inventory.domain.InventoryIdentityModels.BarcodeSymbology;
import io.arhome.inventory.domain.InventoryIdentityModels.DocumentType;
import io.arhome.inventory.domain.InventoryIdentityModels.IdentifierSource;
import io.arhome.inventory.domain.InventoryIdentityModels.IdentifierType;
import io.arhome.inventory.domain.InventoryIdentityModels.IdentifierValue;
import io.arhome.inventory.domain.InventoryIdentityModels.ItemDocument;
import io.arhome.inventory.domain.InventoryIdentityModels.ItemIdentifier;
import io.arhome.inventory.domain.InventoryIdentityModels.Sensitivity;

class InventoryIdentityModelsTest {

    private static final Instant NOW = Instant.parse("2026-08-31T00:00:00Z");

    @Test
    void redactsNonPublicIdentifierValuesFromToString() {
        var secret = new IdentifierValue("34970123", null, Sensitivity.SECRET);
        var privateValue = new IdentifierValue("SN-1234", null, Sensitivity.PRIVATE);

        assertThat(secret.toString()).isEqualTo("[REDACTED]");
        assertThat(privateValue.toString()).isEqualTo("[REDACTED]");
    }

    @Test
    void keepsSemanticTypeSeparateFromQrSymbology() {
        var value = new IdentifierValue("MT:EXAMPLE", null, Sensitivity.SECRET);
        var identifier = new ItemIdentifier(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                IdentifierType.MATTER_SETUP, BarcodeSymbology.QR_CODE, value,
                IdentifierSource.SCANNED, true, NOW, NOW, NOW);

        assertThat(identifier.type()).isEqualTo(IdentifierType.MATTER_SETUP);
        assertThat(identifier.symbology()).isEqualTo(BarcodeSymbology.QR_CODE);
        assertThat(identifier.toString()).doesNotContain("MT:EXAMPLE");
    }

    @Test
    void normalizesBarcodeValuesWithoutChangingGenericPayloads() {
        assertThat(InventoryIdentityModels.normalizeIdentifier(IdentifierType.EAN, " 84 123-456 "))
                .isEqualTo("84123456");
        assertThat(InventoryIdentityModels.normalizeIdentifier(IdentifierType.SERIAL, " SN-12-A "))
                .isEqualTo("SN-12-A");
    }

    @Test
    void supportsModelLevelManualMetadata() {
        var document = new ItemDocument(
                UUID.randomUUID(), UUID.randomUUID(), null,
                DocumentType.MANUAL, "docs/manual-1", "application/pdf",
                "es", "1.0", null, Sensitivity.PUBLIC, NOW, NOW);

        assertThat(document.unitId()).isNull();
        assertThat(document.type()).isEqualTo(DocumentType.MANUAL);
    }

    @Test
    void rejectsInvalidSha256Metadata() {
        assertThatThrownBy(() -> new ItemDocument(
                UUID.randomUUID(), UUID.randomUUID(), null,
                DocumentType.INVOICE, "docs/invoice", "application/pdf",
                null, null, "not-a-sha", Sensitivity.PRIVATE, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sha256");
    }
}
