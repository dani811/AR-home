package io.arhome.spatial.api;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.arhome.spatial.application.SpatialInventoryService;
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

@RestController
@RequestMapping("/api/v1/spatial")
public class SpatialController {

    private final SpatialInventoryService service;

    public SpatialController(SpatialInventoryService service) {
        this.service = service;
    }

    @PostMapping("/spaces")
    ResponseEntity<Space> createSpace(@Valid @RequestBody CreateSpaceRequest request) {
        Space created = service.createSpace(request.name(), request.worldTransform());
        return ResponseEntity.created(URI.create("/api/v1/spatial/spaces/" + created.id())).body(created);
    }

    @PostMapping("/spaces/{spaceId}/furniture")
    ResponseEntity<FurnitureInstance> registerFurniture(
            @PathVariable UUID spaceId,
            @Valid @RequestBody RegisterFurnitureRequest request) {

        FurnitureInstance created = service.registerFurniture(
                spaceId,
                request.name(),
                request.category(),
                request.spaceTransform(),
                request.bounds(),
                request.recognitionMode(),
                request.confidence(),
                request.visualDescriptor());
        return ResponseEntity.created(URI.create("/api/v1/spatial/furniture/" + created.id())).body(created);
    }

    @PostMapping("/spaces/{spaceId}/furniture/relocalization-candidates")
    List<FurnitureCandidate> findRelocalizationCandidates(
            @PathVariable UUID spaceId,
            @Valid @RequestBody RelocalizationRequest request) {
        return service.findRelocalizationCandidates(
                spaceId,
                request.observedTransform(),
                request.observedBounds(),
                request.categoryHint(),
                request.visualScores(),
                request.limit());
    }

    @PostMapping("/furniture/{furnitureId}/compartments")
    ResponseEntity<Compartment> addCompartment(
            @PathVariable UUID furnitureId,
            @Valid @RequestBody AddCompartmentRequest request) {

        Compartment created = service.addCompartment(
                furnitureId,
                request.parentCompartmentId(),
                request.name(),
                request.type(),
                request.parentTransform(),
                request.bounds());
        return ResponseEntity.created(URI.create("/api/v1/spatial/compartments/" + created.id())).body(created);
    }

    @PutMapping("/items/{itemId}/placement")
    ItemPlacement placeItem(
            @PathVariable String itemId,
            @Valid @RequestBody PlaceItemRequest request) {

        return service.placeItem(
                itemId,
                request.furnitureId(),
                request.compartmentId(),
                request.localTransform(),
                request.precision(),
                request.method(),
                request.confidence());
    }

    @GetMapping("/items/{itemId}/placement")
    ItemPlacement getPlacement(@PathVariable String itemId) {
        return service.getPlacement(itemId);
    }

    @GetMapping("/items/{itemId}/world-pose")
    ResolvedItemPose resolveWorldPose(@PathVariable String itemId) {
        return service.resolveWorldPose(itemId);
    }

    public record CreateSpaceRequest(
            @NotBlank String name,
            Transform3D worldTransform) {
    }

    public record RegisterFurnitureRequest(
            @NotBlank String name,
            @NotBlank String category,
            Transform3D spaceTransform,
            @NotNull Bounds3D bounds,
            RecognitionMode recognitionMode,
            @DecimalMin("0.0") @DecimalMax("1.0") double confidence,
            String visualDescriptor) {
    }

    public record RelocalizationRequest(
            @NotNull Transform3D observedTransform,
            @NotNull Bounds3D observedBounds,
            String categoryHint,
            Map<UUID, @DecimalMin("0.0") @DecimalMax("1.0") Double> visualScores,
            int limit) {

        public RelocalizationRequest {
            if (limit == 0) {
                limit = 5;
            }
        }
    }

    public record AddCompartmentRequest(
            UUID parentCompartmentId,
            @NotBlank String name,
            @NotBlank String type,
            Transform3D parentTransform,
            @NotNull Bounds3D bounds) {
    }

    public record PlaceItemRequest(
            @NotNull UUID furnitureId,
            UUID compartmentId,
            Transform3D localTransform,
            PositionPrecision precision,
            PlacementMethod method,
            @DecimalMin("0.0") @DecimalMax("1.0") double confidence) {
    }
}
