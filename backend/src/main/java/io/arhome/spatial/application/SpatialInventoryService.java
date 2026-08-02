package io.arhome.spatial.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import io.arhome.spatial.domain.SpatialTypes.Transform3D;
import io.arhome.spatial.domain.SpatialTypes.Vector3;

@Service
public class SpatialInventoryService {

    private final SpatialRepository repository;
    private final Clock clock;

    public SpatialInventoryService(SpatialRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public Space createSpace(String name, Transform3D worldTransform) {
        Instant now = clock.instant();
        return repository.saveSpace(new Space(
                UUID.randomUUID(),
                name,
                defaultTransform(worldTransform),
                now,
                now));
    }

    @Transactional
    public FurnitureInstance registerFurniture(
            UUID spaceId,
            String name,
            String category,
            Transform3D spaceTransform,
            Bounds3D bounds,
            RecognitionMode recognitionMode,
            double confidence,
            String visualDescriptor) {

        requireSpace(spaceId);
        Instant now = clock.instant();
        return repository.saveFurniture(new FurnitureInstance(
                UUID.randomUUID(),
                spaceId,
                name,
                category,
                defaultTransform(spaceTransform),
                bounds,
                recognitionMode == null ? RecognitionMode.MANUAL_BOUNDING_BOX : recognitionMode,
                confidence,
                visualDescriptor,
                now,
                now));
    }

    @Transactional
    public Compartment addCompartment(
            UUID furnitureId,
            UUID parentCompartmentId,
            String name,
            String type,
            Transform3D parentTransform,
            Bounds3D bounds) {

        requireFurniture(furnitureId);
        if (parentCompartmentId != null) {
            Compartment parent = requireCompartment(parentCompartmentId);
            if (!parent.furnitureId().equals(furnitureId)) {
                throw new IllegalArgumentException("Parent compartment belongs to another furniture instance");
            }
        }

        Instant now = clock.instant();
        return repository.saveCompartment(new Compartment(
                UUID.randomUUID(),
                furnitureId,
                parentCompartmentId,
                name,
                type,
                defaultTransform(parentTransform),
                bounds,
                now,
                now));
    }

    @Transactional
    public ItemPlacement placeItem(
            String itemId,
            UUID furnitureId,
            UUID compartmentId,
            Transform3D localTransform,
            PositionPrecision precision,
            PlacementMethod method,
            double confidence) {

        requireFurniture(furnitureId);
        if (compartmentId != null) {
            Compartment compartment = requireCompartment(compartmentId);
            if (!compartment.furnitureId().equals(furnitureId)) {
                throw new IllegalArgumentException("Compartment belongs to another furniture instance");
            }
        }

        Instant now = clock.instant();
        Instant createdAt = repository.findPlacement(itemId)
                .map(ItemPlacement::createdAt)
                .orElse(now);

        return repository.savePlacement(new ItemPlacement(
                itemId,
                furnitureId,
                compartmentId,
                defaultTransform(localTransform),
                precision == null ? PositionPrecision.ZONE : precision,
                method == null ? PlacementMethod.MANUAL : method,
                confidence,
                createdAt,
                now));
    }

    @Transactional(readOnly = true)
    public ItemPlacement getPlacement(String itemId) {
        return repository.findPlacement(itemId)
                .orElseThrow(() -> new NoSuchElementException("Item placement not found: " + itemId));
    }

    @Transactional(readOnly = true)
    public List<FurnitureCandidate> findRelocalizationCandidates(
            UUID spaceId,
            Transform3D observedTransform,
            Bounds3D observedBounds,
            String categoryHint,
            Map<UUID, Double> visualScores,
            int limit) {

        requireSpace(spaceId);
        Objects.requireNonNull(observedTransform, "observedTransform");
        Objects.requireNonNull(observedBounds, "observedBounds");
        Map<UUID, Double> safeVisualScores = visualScores == null ? Map.of() : visualScores;
        int safeLimit = Math.max(1, Math.min(limit, 20));

        return repository.findFurnitureBySpace(spaceId).stream()
                .map(candidate -> scoreCandidate(
                        candidate,
                        observedTransform,
                        observedBounds,
                        categoryHint,
                        safeVisualScores.get(candidate.id())))
                .sorted(Comparator.comparingDouble(FurnitureCandidate::score).reversed())
                .limit(safeLimit)
                .toList();
    }

    @Transactional(readOnly = true)
    public ResolvedItemPose resolveWorldPose(String itemId) {
        ItemPlacement placement = getPlacement(itemId);
        FurnitureInstance furniture = requireFurniture(placement.furnitureId());
        Space space = requireSpace(furniture.spaceId());

        Transform3D world = space.worldTransform().compose(furniture.spaceTransform());
        for (Compartment compartment : compartmentChain(placement.compartmentId(), furniture.id())) {
            world = world.compose(compartment.parentTransform());
        }
        world = world.compose(placement.localTransform());

        return new ResolvedItemPose(
                itemId,
                space.id(),
                furniture.id(),
                placement.compartmentId(),
                world,
                placement.precision(),
                placement.confidence());
    }

    private static FurnitureCandidate scoreCandidate(
            FurnitureInstance candidate,
            Transform3D observedTransform,
            Bounds3D observedBounds,
            String categoryHint,
            Double visualScoreInput) {

        double distance = distance(
                candidate.spaceTransform().translation(),
                observedTransform.translation());
        double positionScore = clamp01(1.0 - distance / 3.0);
        double geometryScore = geometrySimilarity(candidate.bounds(), observedBounds);
        double visualScore = visualScoreInput == null ? 0.5 : clamp01(visualScoreInput);
        double categoryScore = categoryHint == null || categoryHint.isBlank()
                ? 0.5
                : candidate.category().equalsIgnoreCase(categoryHint) ? 1.0 : 0.0;
        double score = 0.45 * positionScore
                + 0.30 * geometryScore
                + 0.20 * visualScore
                + 0.05 * categoryScore;

        return new FurnitureCandidate(
                candidate.id(),
                candidate.name(),
                candidate.category(),
                score,
                positionScore,
                geometryScore,
                visualScore,
                categoryScore);
    }

    private static double distance(Vector3 first, Vector3 second) {
        double dx = first.x() - second.x();
        double dy = first.y() - second.y();
        double dz = first.z() - second.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double geometrySimilarity(Bounds3D first, Bounds3D second) {
        double widthError = relativeError(first.width(), second.width());
        double heightError = relativeError(first.height(), second.height());
        double depthError = relativeError(first.depth(), second.depth());
        return clamp01(1.0 - (widthError + heightError + depthError) / 3.0);
    }

    private static double relativeError(double first, double second) {
        return Math.abs(first - second) / Math.max(first, second);
    }

    private static double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private Deque<Compartment> compartmentChain(UUID leafId, UUID furnitureId) {
        Deque<Compartment> chain = new ArrayDeque<>();
        Set<UUID> visited = new HashSet<>();
        UUID currentId = leafId;

        while (currentId != null) {
            if (!visited.add(currentId)) {
                throw new IllegalStateException("Compartment hierarchy contains a cycle");
            }
            Compartment current = requireCompartment(currentId);
            if (!current.furnitureId().equals(furnitureId)) {
                throw new IllegalStateException("Compartment hierarchy crosses furniture instances");
            }
            chain.addFirst(current);
            currentId = current.parentCompartmentId();
        }
        return chain;
    }

    private Space requireSpace(UUID id) {
        return repository.findSpace(id)
                .orElseThrow(() -> new NoSuchElementException("Space not found: " + id));
    }

    private FurnitureInstance requireFurniture(UUID id) {
        return repository.findFurniture(id)
                .orElseThrow(() -> new NoSuchElementException("Furniture not found: " + id));
    }

    private Compartment requireCompartment(UUID id) {
        return repository.findCompartment(id)
                .orElseThrow(() -> new NoSuchElementException("Compartment not found: " + id));
    }

    private static Transform3D defaultTransform(Transform3D transform) {
        return transform == null ? Transform3D.identity() : transform;
    }
}
