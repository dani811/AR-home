package io.arhome.spatial.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import io.arhome.spatial.application.SpatialRepository;
import io.arhome.spatial.domain.SpatialModels.Compartment;
import io.arhome.spatial.domain.SpatialModels.FurnitureInstance;
import io.arhome.spatial.domain.SpatialModels.ItemPlacement;
import io.arhome.spatial.domain.SpatialModels.PlacementMethod;
import io.arhome.spatial.domain.SpatialModels.PositionPrecision;
import io.arhome.spatial.domain.SpatialModels.RecognitionMode;
import io.arhome.spatial.domain.SpatialModels.Space;
import io.arhome.spatial.domain.SpatialTypes.Bounds3D;
import io.arhome.spatial.domain.SpatialTypes.Quaternion;
import io.arhome.spatial.domain.SpatialTypes.Transform3D;
import io.arhome.spatial.domain.SpatialTypes.Vector3;

@Repository
public class JdbcSpatialRepository implements SpatialRepository {

    private static final String SPACE_COLUMNS = """
            id, name,
            world_tx, world_ty, world_tz,
            world_qx, world_qy, world_qz, world_qw,
            created_at, updated_at
            """;

    private static final String FURNITURE_COLUMNS = """
            id, space_id, name, category,
            space_tx, space_ty, space_tz,
            space_qx, space_qy, space_qz, space_qw,
            width, height, depth,
            recognition_mode, confidence, visual_descriptor,
            created_at, updated_at
            """;

    private static final String COMPARTMENT_COLUMNS = """
            id, furniture_id, parent_compartment_id, name, type,
            parent_tx, parent_ty, parent_tz,
            parent_qx, parent_qy, parent_qz, parent_qw,
            width, height, depth,
            created_at, updated_at
            """;

    private static final String PLACEMENT_COLUMNS = """
            item_id, furniture_id, compartment_id,
            local_tx, local_ty, local_tz,
            local_qx, local_qy, local_qz, local_qw,
            precision, placement_method, confidence,
            created_at, updated_at
            """;

    private final JdbcTemplate jdbc;

    public JdbcSpatialRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Space saveSpace(Space space) {
        Transform3D t = space.worldTransform();
        jdbc.update("""
                INSERT INTO spatial_space (
                    id, name,
                    world_tx, world_ty, world_tz,
                    world_qx, world_qy, world_qz, world_qw,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                space.id(), space.name(),
                t.translation().x(), t.translation().y(), t.translation().z(),
                t.rotation().x(), t.rotation().y(), t.rotation().z(), t.rotation().w(),
                Timestamp.from(space.createdAt()), Timestamp.from(space.updatedAt()));
        return space;
    }

    @Override
    public Optional<Space> findSpace(UUID id) {
        return optionalQuery("SELECT " + SPACE_COLUMNS + " FROM spatial_space WHERE id = ?", SPACE_MAPPER, id);
    }

    @Override
    public FurnitureInstance saveFurniture(FurnitureInstance furniture) {
        Transform3D t = furniture.spaceTransform();
        Bounds3D b = furniture.bounds();
        jdbc.update("""
                INSERT INTO furniture_instance (
                    id, space_id, name, category,
                    space_tx, space_ty, space_tz,
                    space_qx, space_qy, space_qz, space_qw,
                    width, height, depth,
                    recognition_mode, confidence, visual_descriptor,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                furniture.id(), furniture.spaceId(), furniture.name(), furniture.category(),
                t.translation().x(), t.translation().y(), t.translation().z(),
                t.rotation().x(), t.rotation().y(), t.rotation().z(), t.rotation().w(),
                b.width(), b.height(), b.depth(),
                furniture.recognitionMode().name(), furniture.confidence(), furniture.visualDescriptor(),
                Timestamp.from(furniture.createdAt()), Timestamp.from(furniture.updatedAt()));
        return furniture;
    }

    @Override
    public Optional<FurnitureInstance> findFurniture(UUID id) {
        return optionalQuery(
                "SELECT " + FURNITURE_COLUMNS + " FROM furniture_instance WHERE id = ?",
                FURNITURE_MAPPER,
                id);
    }

    @Override
    public List<FurnitureInstance> findFurnitureBySpace(UUID spaceId) {
        return jdbc.query(
                "SELECT " + FURNITURE_COLUMNS + " FROM furniture_instance WHERE space_id = ?",
                FURNITURE_MAPPER,
                spaceId);
    }

    @Override
    public Compartment saveCompartment(Compartment compartment) {
        Transform3D t = compartment.parentTransform();
        Bounds3D b = compartment.bounds();
        jdbc.update("""
                INSERT INTO furniture_compartment (
                    id, furniture_id, parent_compartment_id, name, type,
                    parent_tx, parent_ty, parent_tz,
                    parent_qx, parent_qy, parent_qz, parent_qw,
                    width, height, depth,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                compartment.id(), compartment.furnitureId(), compartment.parentCompartmentId(),
                compartment.name(), compartment.type(),
                t.translation().x(), t.translation().y(), t.translation().z(),
                t.rotation().x(), t.rotation().y(), t.rotation().z(), t.rotation().w(),
                b.width(), b.height(), b.depth(),
                Timestamp.from(compartment.createdAt()), Timestamp.from(compartment.updatedAt()));
        return compartment;
    }

    @Override
    public Optional<Compartment> findCompartment(UUID id) {
        return optionalQuery(
                "SELECT " + COMPARTMENT_COLUMNS + " FROM furniture_compartment WHERE id = ?",
                COMPARTMENT_MAPPER,
                id);
    }

    @Override
    public ItemPlacement savePlacement(ItemPlacement placement) {
        Transform3D t = placement.localTransform();
        jdbc.update("""
                INSERT INTO item_placement (
                    item_id, furniture_id, compartment_id,
                    local_tx, local_ty, local_tz,
                    local_qx, local_qy, local_qz, local_qw,
                    precision, placement_method, confidence,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (item_id) DO UPDATE SET
                    furniture_id = EXCLUDED.furniture_id,
                    compartment_id = EXCLUDED.compartment_id,
                    local_tx = EXCLUDED.local_tx,
                    local_ty = EXCLUDED.local_ty,
                    local_tz = EXCLUDED.local_tz,
                    local_qx = EXCLUDED.local_qx,
                    local_qy = EXCLUDED.local_qy,
                    local_qz = EXCLUDED.local_qz,
                    local_qw = EXCLUDED.local_qw,
                    precision = EXCLUDED.precision,
                    placement_method = EXCLUDED.placement_method,
                    confidence = EXCLUDED.confidence,
                    updated_at = EXCLUDED.updated_at
                """,
                placement.itemId(), placement.furnitureId(), placement.compartmentId(),
                t.translation().x(), t.translation().y(), t.translation().z(),
                t.rotation().x(), t.rotation().y(), t.rotation().z(), t.rotation().w(),
                placement.precision().name(), placement.method().name(), placement.confidence(),
                Timestamp.from(placement.createdAt()), Timestamp.from(placement.updatedAt()));
        return placement;
    }

    @Override
    public Optional<ItemPlacement> findPlacement(String itemId) {
        return optionalQuery(
                "SELECT " + PLACEMENT_COLUMNS + " FROM item_placement WHERE item_id = ?",
                PLACEMENT_MAPPER,
                itemId);
    }

    private <T> Optional<T> optionalQuery(String sql, RowMapper<T> mapper, Object... args) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, mapper, args));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    private static Transform3D transform(ResultSet rs, String prefix) throws SQLException {
        return new Transform3D(
                new Vector3(
                        rs.getDouble(prefix + "_tx"),
                        rs.getDouble(prefix + "_ty"),
                        rs.getDouble(prefix + "_tz")),
                new Quaternion(
                        rs.getDouble(prefix + "_qx"),
                        rs.getDouble(prefix + "_qy"),
                        rs.getDouble(prefix + "_qz"),
                        rs.getDouble(prefix + "_qw")));
    }

    private static Bounds3D bounds(ResultSet rs) throws SQLException {
        return new Bounds3D(rs.getDouble("width"), rs.getDouble("height"), rs.getDouble("depth"));
    }

    private static final RowMapper<Space> SPACE_MAPPER = (rs, rowNum) -> new Space(
            rs.getObject("id", UUID.class),
            rs.getString("name"),
            transform(rs, "world"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());

    private static final RowMapper<FurnitureInstance> FURNITURE_MAPPER = (rs, rowNum) -> new FurnitureInstance(
            rs.getObject("id", UUID.class),
            rs.getObject("space_id", UUID.class),
            rs.getString("name"),
            rs.getString("category"),
            transform(rs, "space"),
            bounds(rs),
            RecognitionMode.valueOf(rs.getString("recognition_mode")),
            rs.getDouble("confidence"),
            rs.getString("visual_descriptor"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());

    private static final RowMapper<Compartment> COMPARTMENT_MAPPER = (rs, rowNum) -> new Compartment(
            rs.getObject("id", UUID.class),
            rs.getObject("furniture_id", UUID.class),
            rs.getObject("parent_compartment_id", UUID.class),
            rs.getString("name"),
            rs.getString("type"),
            transform(rs, "parent"),
            bounds(rs),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());

    private static final RowMapper<ItemPlacement> PLACEMENT_MAPPER = (rs, rowNum) -> new ItemPlacement(
            rs.getString("item_id"),
            rs.getObject("furniture_id", UUID.class),
            rs.getObject("compartment_id", UUID.class),
            transform(rs, "local"),
            PositionPrecision.valueOf(rs.getString("precision")),
            PlacementMethod.valueOf(rs.getString("placement_method")),
            rs.getDouble("confidence"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());
}
