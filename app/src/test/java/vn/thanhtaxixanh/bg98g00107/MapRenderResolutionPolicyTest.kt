package vn.thanhtaxixanh.bg98g00107

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.thanhtaxixanh.bg98g00107.map.MapRenderResolutionPolicy

class MapRenderResolutionPolicyTest {
    @Test fun smallSurfaceKeepsNativeResolution() {
        val r = MapRenderResolutionPolicy.resolve(800, 600, 260)
        assertEquals(800, r.width)
        assertEquals(600, r.height)
        assertEquals(260, r.dpi)
        assertEquals(1.0, r.scale, 0.0)
    }

    @Test fun mediumSurfaceReducesPixelsConservatively() {
        val r = MapRenderResolutionPolicy.resolve(1280, 720, 260)
        assertEquals(0.90, r.scale, 0.0)
        assertTrue(r.width < 1280)
        assertTrue(r.height < 720)
        assertEquals(234, r.dpi)
        assertEquals(0, r.width % 2)
        assertEquals(0, r.height % 2)
    }

    @Test fun largeSurfaceUsesStrongerButBoundedReduction() {
        val r = MapRenderResolutionPolicy.resolve(1920, 1080, 260)
        assertEquals(0.82, r.scale, 0.0)
        assertTrue(r.width * r.height < 1920 * 1080)
        assertTrue(r.dpi >= 120)
    }

    @Test fun invalidTinyDimensionsAreClamped() {
        val r = MapRenderResolutionPolicy.resolve(1, 2, 260)
        assertTrue(r.width >= 64)
        assertTrue(r.height >= 64)
    }
}
