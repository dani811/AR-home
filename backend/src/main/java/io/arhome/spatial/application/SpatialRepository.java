package io.arhome.spatial.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.arhome.spatial.domain.SpatialModels.Compartment;
import io.arhome.spatial.domain.SpatialModels.FurnitureInstance;
import io.arhome.spatial.domain.SpatialModels.ItemPlacement;
import io.arhome.spatial.domain.SpatialModels.Space;

public interface SpatialRepository {

    Space saveSpace(Space space);

    Optional<Space> findSpace(UUID id);

    FurnitureInstance saveFurniture(FurnitureInstance furniture);

    Optional<FurnitureInstance> findFurniture(UUID id);

    List<FurnitureInstance> findFurnitureBySpace(UUID spaceId);

    Compartment saveCompartment(Compartment compartment);

    Optional<Compartment> findCompartment(UUID id);

    ItemPlacement savePlacement(ItemPlacement placement);

    Optional<ItemPlacement> findPlacement(String itemId);
}
