package io.arhome.spatial.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.arhome.spatial.domain.SpatialTypes.Quaternion;
import io.arhome.spatial.domain.SpatialTypes.Transform3D;
import io.arhome.spatial.domain.SpatialTypes.Vector3;

class SpatialTypesTest {

    private static final double EPSILON = 1.0e-9;

    @Test
    void rotatesVectorAroundZAxis() {
        double halfAngle = Math.PI / 4;
        Quaternion rotation = new Quaternion(0, 0, Math.sin(halfAngle), Math.cos(halfAngle));

        Vector3 result = rotation.rotate(new Vector3(1, 0, 0));

        assertThat(result.x()).isCloseTo(0, within(EPSILON));
        assertThat(result.y()).isCloseTo(1, within(EPSILON));
        assertThat(result.z()).isCloseTo(0, within(EPSILON));
    }

    @Test
    void composesParentAndChildTransforms() {
        double halfAngle = Math.PI / 4;
        Transform3D parent = new Transform3D(
                new Vector3(10, 0, 0),
                new Quaternion(0, 0, Math.sin(halfAngle), Math.cos(halfAngle)));
        Transform3D child = new Transform3D(new Vector3(2, 0, 0), Quaternion.identity());

        Transform3D result = parent.compose(child);

        assertThat(result.translation().x()).isCloseTo(10, within(EPSILON));
        assertThat(result.translation().y()).isCloseTo(2, within(EPSILON));
        assertThat(result.translation().z()).isCloseTo(0, within(EPSILON));
    }

    private static org.assertj.core.data.Offset<Double> within(double value) {
        return org.assertj.core.data.Offset.offset(value);
    }
}
