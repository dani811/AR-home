package io.arhome.spatial.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.arhome.spatial.domain.SpatialModels.Compartment;
import io.arhome.spatial.domain.SpatialModels.FurnitureCandidate;
import io.arhome.spatial.domain.SpatialModels.FurnitureInstance;
import io.arhome.spatial.domain.SpatialModels.ItemPlacement;
import io.arhome.spatial.domain.SpatialModels.PlacementMethod;
import io.arhome.spatial.domain.SpatialModels.PositionPrecision;
import io.arhome.spatial.domain.SpatialModels.RecognitionMode;
import io.arhome.spatial.domain.SpatialModels.ResolvedItemPose;
import io.arhome.spatial.domain.SpatialModels.Space;
import io.arhome.spatial.domain.SpatialTypes.Bounds3D;
import io.arhome.spatial.domain.SpatialTypes.Quaternion;
import io.arhome.spatial.domain.SpatialTypes.Transform3D;
import io.arhome.spatial.domain.SpatialTypes.Vector3;

class SpatialInventoryServiceTest {

    private InMemorySpatialRepository repository;
    private SpatialInventoryService service;

    @BeforeEach
    void setUp() {
        repository = new InMemorySpatialRepository();
        service = new SpatialInventoryService(
                repository,
                Clock.fixed(Instant.parse("2026-08-02T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void resolvesAnItemPoseThroughSpaceFurnitureAndNestedCompartments() {
        Space space = service.createSpace(
                "Casa",
                new Transform3D(new Vector3(10, 0, 0), Quaternion.identity()));
        FurnitureInstance wardrobe = service.registerFurniture(
                space.id(),
                "Armario dormitorio",
                "WARDROBE",
                new Transform3D(new Vector3(2, 0, 0), Quaternion.identity()),
                new Bounds3D(2.0, 2.4, 0.6),
                RecognitionMode.MANUAL_BOUNDING_BOX,
                1.0,
                null);
        Compartment leftSide = service.addCompartment(
                wardrobe.id(),
                null,
                "Módulo izquierdo",
                "SECTION",
                new Transform3D(new Vector3(0.2, 0, 0), Quaternion.identity()),
                new Bounds3D(0.9, 2.3, 0.55));
        Compartment shelf = service.addCompartment(
                wardrobe.id(),
                leftSide.id(),
                "Balda superior",
                "SHELF",
                new Transform3D(new Vector3(0, 1.8, 0), Quaternion.identity()),
                new Bounds3D(0.85, 0.04, 0.5));

        service.placeItem(
                "inventory-item-42",
                wardrobe.id(),
                shelf.id(),
                new Transform3D(new Vector3(0.1, 0.05, 0.2), Quaternion.identity()),
                PositionPrecision.CENTIMETRIC,
                PlacementMethod.AR_TAP,
                0.92);

        ResolvedItemPose result = service.resolveWorldPose("inventory-item-42");

        assertThat(result.worldTransform().translation().x())
                .isCloseTo(12.3, org.assertj.core.data.Offset.offset(1.0e-9));
        assertThat(result.worldTransform().translation().y())
                .isCloseTo(1.85, org.assertj.core.data.Offset.offset(1.0e-9));
        assertThat(result.worldTransform().translation().z())
                .isCloseTo(0.2, org.assertj.core.data.Offset.offset(1.0e-9));
        assertThat(result.spaceId()).isEqualTo(space.id());
        assertThat(result.furnitureId()).isEqualTo(wardrobe.id());
    }

    @Test
    void ranksTheKnownFurnitureUsingSpatialGeometryAndVisualEvidence() {
        Space space = service.createSpace("Casa", Transform3D.identity());
        FurnitureInstance wardrobe = service.registerFurniture(
                space.id(),
                "Armario",
                "WARDROBE",
                new Transform3D(new Vector3(1.0, 0, 0), Quaternion.identity()),
                new Bounds3D(2.0, 2.4, 0.6),
                RecognitionMode.MANUAL_BOUNDING_BOX,
                1.0,
                null);
        FurnitureInstance shelf = service.registerFurniture(
                space.id(),
                "Estantería",
                "SHELF",
                new Transform3D(new Vector3(5.0, 0, 0), Quaternion.identity()),
                new Bounds3D(0.8, 1.8, 0.35),
                RecognitionMode.MANUAL_BOUNDING_BOX,
                1.0,
                null);

        List<FurnitureCandidate> candidates = service.findRelocalizationCandidates(
                space.id(),
                new Transform3D(new Vector3(1.15, 0, 0), Quaternion.identity()),
                new Bounds3D(1.95, 2.35, 0.62),
                "WARDROBE",
                Map.of(wardrobe.id(), 0.94, shelf.id(), 0.12),
                5);

        assertThat(candidates).hasSize(2);
        assertThat(candidates.getFirst().furnitureId()).isEqualTo(wardrobe.id());
        assertThat(candidates.getFirst().score()).isGreaterThan(candidates.getLast().score());
    }

    @Test
    void rejectsACompartmentFromAnotherFurnitureInstance() {
        Space space = service.createSpace("Casa", Transform3D.identity());
        FurnitureInstance first = furniture(space.id(), "Armario");
        FurnitureInstance second = furniture(space.id(), "Cómoda");
        Compartment drawer = service.addCompartment(
                second.id(),
                null,
                "Cajón",
                "DRAWER",
                Transform3D.identity(),
                new Bounds3D(0.5, 0.2, 0.4));

        assertThatThrownBy(() -> service.placeItem(
                "item",
                first.id(),
                drawer.id(),
                Transform3D.identity(),
                PositionPrecision.ZONE,
                PlacementMethod.MANUAL,
                1.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("another furniture");
    }

    private FurnitureInstance furniture(UUID spaceId, String name) {
        return service.registerFurniture(
                spaceId,
                name,
                "STORAGE",
                Transform3D.identity(),
                new Bounds3D(1, 1, 1),
                RecognitionMode.MANUAL_BOUNDING_BOX,
                1,
                null);
    }

    private static final class InMemorySpatialRepository implements SpatialRepository {
        private final Map<UUID, Space> spaces = new HashMap<>();
        private final Map<UUID, FurnitureInstance> furniture = new HashMap<>();
        private final Map<UUID, Compartment> compartments = new HashMap<>();
        private final Map<String, ItemPlacement> placements = new HashMap<>();

        @Override
        public Space saveSpace(Space space) {
            spaces.put(space.id(), space);
            return space;
        }

        @Override
        public Optional<Space> findSpace(UUID id) {
            return Optional.ofNullable(spaces.get(id));
        }

        @Override
        public FurnitureInstance saveFurniture(FurnitureInstance value) {
            furniture.put(value.id(), value);
            return value;
        }

        @Override
        public Optional<FurnitureInstance> findFurniture(UUID id) {
            return Optional.ofNullable(furniture.get(id));
        }

        @Override
        public List<FurnitureInstance> findFurnitureBySpace(UUID spaceId) {
            return furniture.values().stream()
                    .filter(value -> value.spaceId().equals(spaceId))
                    .toList();
        }

        @Override
        public Compartment saveCompartment(Compartment compartment) {
            compartments.put(compartment.id(), compartment);
            return compartment;
        }

        @Override
        public Optional<Compartment> findCompartment(UUID id) {
            return Optional.ofNullable(compartments.get(id));
        }

        @Override
        public ItemPlacement savePlacement(ItemPlacement placement) {
            placements.put(placement.itemId(), placement);
            return placement;
        }

        @Override
        public Optional<ItemPlacement> findPlacement(String itemId) {
            return Optional.ofNullable(placements.get(itemId));
        }
    }
}
