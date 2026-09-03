package io.arhome.localizer.depth

import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class DepthGeometryTest {
    @Test fun readsPaddedPlanesAndUnsignedLittleEndianDepth() {
        val b = ByteBuffer.wrap(byteArrayOf(99, 99, 0xE8.toByte(), 3, 0x40, 0x9C.toByte(), 9, 9, 0, 0, 0xD0.toByte(), 7))
        b.position(2)
        val packed = DepthGeometry.packedPlane(b, 2, 2, 6, 2, 2)
        assertEquals(listOf(1000, 40000, 0, 2000), (0..3).map { DepthGeometry.millimeters(packed, it) })
    }
    @Test fun alignmentRespectsCropRotationAndRejectsOutside() {
        val transform = floatArrayOf(0f, 1f/480, 0f, -1f/640, 0f, 1f)
        assertEquals(2 * 8 + 2, DepthGeometry.depthPixel(320.0, 120.0, transform, 8, 4))
        assertNull(DepthGeometry.depthPixel(-10.0, 120.0, transform, 8, 4))
        assertNull(DepthGeometry.depthPixel(320.0, 480.0, transform, 8, 4))
    }
    @Test fun opticalDepthProjectsToArCoreCameraAxes() {
        assertArrayEquals(floatArrayOf(1f, -0.5f, -2f),
            DepthGeometry.cameraPoint(570.0, 365.0, 2.0, 500.0, 500.0, 320.0, 240.0), 1e-6f)
    }
    @Test fun rejectsHolesLowConfidenceAndDepthEdges() {
        val depth = ByteArray(18) { if (it % 2 == 0) 0xD0.toByte() else 7 }
        val confidence = ByteArray(9) { 255.toByte() }
        assertEquals(2.0, DepthGeometry.stableDepthMeters(depth, confidence, 3, 3, 4)!!, 1e-9)
        confidence[4] = 0
        assertNull(DepthGeometry.stableDepthMeters(depth, confidence, 3, 3, 4))
        confidence[4] = 255.toByte(); depth[0] = 0xB8.toByte(); depth[1] = 11 // 3m neighbor, 2m center.
        assertNull(DepthGeometry.stableDepthMeters(depth, confidence, 3, 3, 4))
        depth[8] = 0; depth[9] = 0
        assertNull(DepthGeometry.stableDepthMeters(depth, confidence, 3, 3, 4))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsTruncatedPlanes() {
        DepthGeometry.packedPlane(ByteBuffer.wrap(ByteArray(3)), 2, 2, 4, 2, 2)
    }
}
