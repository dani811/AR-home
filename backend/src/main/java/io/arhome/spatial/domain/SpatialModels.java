package io.arhome.spatial.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import io.arhome.spatial.domain.SpatialTypes.Bounds3D;
import io.arhome.spatial.domain.SpatialTypes.Transform3D;

public final class SpatialModels {

    private SpatialModels() {
    }

    public enum RecognitionMode {
        MANUAL_BOUNDING_BOX,
        VISUAL_RELOCALIZATION,
        MARKER_ASSISTED,
        AUTOMATIC
    }

    public enum PositionPrecision {
        APPROXIMATE,
        ZONE,
        CENTIMETRIC
    }

    public enum PlacementMethod {
        MANUAL,
        AR_TAP,
        VISUAL_ESTIMATE,
        IMPORTED
    }

    public record Space(
            UUID id,
            String name,
            Transform3D worldTransform,
            Instant createdAt,
            Instant updatedAt) {

        public Space {
            Objects.requireNonNull(id, "id");
            requireText(name, "name");
            Objects.requireNonNull(worldTransform, "worldTransform");
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(updatedAt, "updatedAt");
        }
    }

    public record FurnitureInstance(
            UUID id,
            UUID spaceId,
            String name,
            String category,
            Transform3D spaceTransform,
            Bounds3D bounds,
            RecognitionMode recognitionMode,
            double confidence,
            String visualDescriptor,
            Instant createdAt,
            Instant updatedAt) {

        public FurnitureInstance {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(spaceId, "spaceId");
            requireText(name, "name");
            requireText(category, "category");
            Objects.requireNonNull(spaceTransform, "spaceTransform");
            Objects.requireNonNull(bounds, "bounds");
            Objects.requireNonNull(recognitionMode, "recognitionMode");
            requireConfidence(confidence);
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(updatedAt, "updatedAt");
        }
    }

    public record Compartment(
            UUID id,
            UUID furnitureId,
            UUID parentCompartmentId,
            String name,
            String type,
            Transform3D parentTransform,
            Bounds3D bounds,
            Instant createdAt,
            Instant updatedAt) {

        public Compartment {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(furnitureId, "furnitureId");
            requireText(name, "name");
            requireText(type, "type");
            Objects.requireNonNull(parentTransform, "parentTransform");
            Objects.requireNonNull(bounds, "bounds");
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(updatedAt, "updatedAt");
        }
    }

    public record ItemPlacement(
            String itemId,
            UUID furnitureId,
            UUID compartmentId,
            Transform3D localTransform,
            PositionPrecision precision,
            PlacementMethod method,
            double confidence,
            Instant createdAt,
            Instant updatedAt) {

        public ItemPlacement {
            requireText(itemId, "itemId");
            Objects.requireNonNull(furnitureId, "furnitureId");
            Objects.requireNonNull(localTransform, "localTransform");
            Objects.requireNonNull(precision, "precision");
            Objects.requireNonNull(method, "method");
            requireConfidence(confidence);
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(updatedAt, "updatedAt");
        }
    }

    public record ResolvedItemPose(
            String itemId,
            UUID spaceId,
            UUID furnitureId,
            UUID compartmentId,
            Transform3D worldTransform,
            PositionPrecision precision,
            double confidence) {
    }

    public record FurnitureCandidate(
            UUID furnitureId,
            String name,
            String category,
            double score,
            double positionScore,
            double geometryScore,
            double visualScore,
            double categoryScore) {
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static void requireConfidence(double confidence) {
        if (confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
    }
}
