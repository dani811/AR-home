CREATE TABLE spatial_space (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    world_tx DOUBLE PRECISION NOT NULL,
    world_ty DOUBLE PRECISION NOT NULL,
    world_tz DOUBLE PRECISION NOT NULL,
    world_qx DOUBLE PRECISION NOT NULL,
    world_qy DOUBLE PRECISION NOT NULL,
    world_qz DOUBLE PRECISION NOT NULL,
    world_qw DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE furniture_instance (
    id UUID PRIMARY KEY,
    space_id UUID NOT NULL REFERENCES spatial_space(id) ON DELETE CASCADE,
    name VARCHAR(160) NOT NULL,
    category VARCHAR(100) NOT NULL,
    space_tx DOUBLE PRECISION NOT NULL,
    space_ty DOUBLE PRECISION NOT NULL,
    space_tz DOUBLE PRECISION NOT NULL,
    space_qx DOUBLE PRECISION NOT NULL,
    space_qy DOUBLE PRECISION NOT NULL,
    space_qz DOUBLE PRECISION NOT NULL,
    space_qw DOUBLE PRECISION NOT NULL,
    width DOUBLE PRECISION NOT NULL CHECK (width > 0),
    height DOUBLE PRECISION NOT NULL CHECK (height > 0),
    depth DOUBLE PRECISION NOT NULL CHECK (depth > 0),
    recognition_mode VARCHAR(40) NOT NULL,
    confidence DOUBLE PRECISION NOT NULL CHECK (confidence BETWEEN 0 AND 1),
    visual_descriptor TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_furniture_space ON furniture_instance(space_id);

CREATE TABLE furniture_compartment (
    id UUID PRIMARY KEY,
    furniture_id UUID NOT NULL REFERENCES furniture_instance(id) ON DELETE CASCADE,
    parent_compartment_id UUID REFERENCES furniture_compartment(id) ON DELETE CASCADE,
    name VARCHAR(160) NOT NULL,
    type VARCHAR(80) NOT NULL,
    parent_tx DOUBLE PRECISION NOT NULL,
    parent_ty DOUBLE PRECISION NOT NULL,
    parent_tz DOUBLE PRECISION NOT NULL,
    parent_qx DOUBLE PRECISION NOT NULL,
    parent_qy DOUBLE PRECISION NOT NULL,
    parent_qz DOUBLE PRECISION NOT NULL,
    parent_qw DOUBLE PRECISION NOT NULL,
    width DOUBLE PRECISION NOT NULL CHECK (width > 0),
    height DOUBLE PRECISION NOT NULL CHECK (height > 0),
    depth DOUBLE PRECISION NOT NULL CHECK (depth > 0),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT compartment_not_self_parent CHECK (parent_compartment_id IS NULL OR parent_compartment_id <> id)
);

CREATE INDEX idx_compartment_furniture ON furniture_compartment(furniture_id);
CREATE INDEX idx_compartment_parent ON furniture_compartment(parent_compartment_id);

CREATE TABLE item_placement (
    item_id VARCHAR(200) PRIMARY KEY,
    furniture_id UUID NOT NULL REFERENCES furniture_instance(id) ON DELETE CASCADE,
    compartment_id UUID REFERENCES furniture_compartment(id) ON DELETE SET NULL,
    local_tx DOUBLE PRECISION NOT NULL,
    local_ty DOUBLE PRECISION NOT NULL,
    local_tz DOUBLE PRECISION NOT NULL,
    local_qx DOUBLE PRECISION NOT NULL,
    local_qy DOUBLE PRECISION NOT NULL,
    local_qz DOUBLE PRECISION NOT NULL,
    local_qw DOUBLE PRECISION NOT NULL,
    precision VARCHAR(30) NOT NULL,
    placement_method VARCHAR(40) NOT NULL,
    confidence DOUBLE PRECISION NOT NULL CHECK (confidence BETWEEN 0 AND 1),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_placement_furniture ON item_placement(furniture_id);
CREATE INDEX idx_placement_compartment ON item_placement(compartment_id);
