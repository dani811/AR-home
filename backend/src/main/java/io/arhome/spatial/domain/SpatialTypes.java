package io.arhome.spatial.domain;

import static java.lang.Math.abs;
import static java.lang.Math.sqrt;

import java.util.Objects;

public final class SpatialTypes {

    private static final double EPSILON = 1.0e-9;

    private SpatialTypes() {
    }

    public record Vector3(double x, double y, double z) {

        public static Vector3 zero() {
            return new Vector3(0, 0, 0);
        }

        public Vector3 add(Vector3 other) {
            Objects.requireNonNull(other, "other");
            return new Vector3(x + other.x, y + other.y, z + other.z);
        }

        public Vector3 scale(double factor) {
            return new Vector3(x * factor, y * factor, z * factor);
        }
    }

    public record Quaternion(double x, double y, double z, double w) {

        public Quaternion {
            double norm = sqrt(x * x + y * y + z * z + w * w);
            if (norm < EPSILON) {
                throw new IllegalArgumentException("Quaternion norm must be greater than zero");
            }
            x /= norm;
            y /= norm;
            z /= norm;
            w /= norm;
        }

        public static Quaternion identity() {
            return new Quaternion(0, 0, 0, 1);
        }

        public Quaternion multiply(Quaternion other) {
            Objects.requireNonNull(other, "other");
            return new Quaternion(
                    w * other.x + x * other.w + y * other.z - z * other.y,
                    w * other.y - x * other.z + y * other.w + z * other.x,
                    w * other.z + x * other.y - y * other.x + z * other.w,
                    w * other.w - x * other.x - y * other.y - z * other.z);
        }

        public Quaternion conjugate() {
            return new Quaternion(-x, -y, -z, w);
        }

        public Vector3 rotate(Vector3 vector) {
            Objects.requireNonNull(vector, "vector");

            double dotUv = x * vector.x + y * vector.y + z * vector.z;
            double dotUu = x * x + y * y + z * z;
            double crossX = y * vector.z - z * vector.y;
            double crossY = z * vector.x - x * vector.z;
            double crossZ = x * vector.y - y * vector.x;

            return new Vector3(
                    2 * dotUv * x + (w * w - dotUu) * vector.x + 2 * w * crossX,
                    2 * dotUv * y + (w * w - dotUu) * vector.y + 2 * w * crossY,
                    2 * dotUv * z + (w * w - dotUu) * vector.z + 2 * w * crossZ);
        }

        public boolean approximatelyEquals(Quaternion other, double tolerance) {
            Objects.requireNonNull(other, "other");
            return abs(x - other.x) <= tolerance
                    && abs(y - other.y) <= tolerance
                    && abs(z - other.z) <= tolerance
                    && abs(w - other.w) <= tolerance;
        }
    }

    public record Transform3D(Vector3 translation, Quaternion rotation) {

        public Transform3D {
            Objects.requireNonNull(translation, "translation");
            Objects.requireNonNull(rotation, "rotation");
        }

        public static Transform3D identity() {
            return new Transform3D(Vector3.zero(), Quaternion.identity());
        }

        public Transform3D compose(Transform3D child) {
            Objects.requireNonNull(child, "child");
            Vector3 translatedChild = rotation.rotate(child.translation);
            return new Transform3D(
                    translation.add(translatedChild),
                    rotation.multiply(child.rotation));
        }
    }

    public record Bounds3D(double width, double height, double depth) {

        public Bounds3D {
            if (width <= 0 || height <= 0 || depth <= 0) {
                throw new IllegalArgumentException("All bounds dimensions must be greater than zero");
            }
        }
    }
}
