package vn.thanhtaxixanh.bg98g00107

import vn.thanhtaxixanh.bg98g00107.map.MapFrameRatePolicy
import vn.thanhtaxixanh.bg98g00107.map.MapPerformanceMonitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapPerformanceMonitorTest {
    @Test fun reportsFrameRateAndAverageCostOnWindowBoundary() {
        val m = MapPerformanceMonitor(1_000L)
        m.reset(100L)
        m.recordFrame(200L, 2_000_000L)
        m.recordFrame(400L, 4_000_000L)
        m.recordDroppedFrame(450L)
        m.recordFrameLockWaitNs(500_000L)
        m.recordFrameLockWaitNs(1_500_000L)
        m.recordHostDrawNs(8_000_000L)
        m.recordHostDrawNs(12_000_000L)
        m.recordRoadContextQueryNs(6_000_000L, 40)
        m.recordRoadContextQueryNs(10_000_000L, 60)
        m.recordStaleRoadContext()
        m.recordGpsNs(6_000_000L)
        m.recordGpsNs(2_000_000L)
        m.recordStepNs(1_000_000L)
        assertNull(m.snapshotIfDue(1_099L))
        val s = requireNotNull(m.snapshotIfDue(1_100L))
        assertEquals(2, s.deliveredFrames)
        assertEquals(2.0, s.observedFrameFps, 0.001)
        assertEquals(3.0, s.avgCopyMs!!, 0.001)
        assertEquals(4.0, s.maxCopyMs!!, 0.001)
        assertEquals(1, s.droppedFrames)
        assertEquals(2, s.frameLockWaits)
        assertEquals(1.0, s.avgFrameLockWaitMs!!, 0.001)
        assertEquals(1.5, s.maxFrameLockWaitMs!!, 0.001)
        assertEquals(2, s.hostDraws)
        assertEquals(10.0, s.avgHostDrawMs!!, 0.001)
        assertEquals(12.0, s.maxHostDrawMs!!, 0.001)
        assertEquals(2, s.roadContextQueries)
        assertEquals(8.0, s.avgRoadContextMs!!, 0.001)
        assertEquals(10.0, s.maxRoadContextMs!!, 0.001)
        assertEquals(50.0, s.avgRoadCandidates!!, 0.001)
        assertEquals(1, s.staleRoadContexts)
        assertEquals(2, s.gpsUpdates)
        assertEquals(4.0, s.avgGpsMs!!, 0.001)
        assertEquals(6.0, s.maxGpsMs!!, 0.001)
        assertEquals(1.0, s.avgStepMs!!, 0.001)
    }

    @Test fun noActivityIsNotFabricatedAsFpsAndWindowsDoNotLeakSamples() {
        val m = MapPerformanceMonitor(1_000L)
        m.reset(0L)
        val empty = requireNotNull(m.snapshotIfDue(1_000L))
        assertEquals(0.0, empty.observedFrameFps, 0.0)
        assertNull(empty.avgCopyMs)
        assertNull(empty.avgGpsMs)
        assertNull(m.snapshotIfDue(1_001L))
        m.recordFrame(1_100L, 1_000_000L)
        val later = requireNotNull(m.snapshotIfDue(2_000L))
        assertEquals(1, later.deliveredFrames)
        assertEquals(1.0, later.observedFrameFps, 0.001)
    }

    @Test fun initialStyleLoadIsMeasuredOnceEvenAcrossSamplingWindows() {
        val m = MapPerformanceMonitor(100L)
        m.reset(0L)
        m.startMapLoad(10L)
        m.snapshotIfDue(100L)
        assertEquals(140L, m.finishMapLoad(150L))
        assertNull(m.finishMapLoad(160L))
        m.startMapLoad(200L)
        assertNull(m.finishMapLoad(199L))
        assertEquals(20L, m.finishMapLoad(220L))
    }

    @Test fun countersRejectInvalidDurationsAndLogContainsOnlyAggregates() {
        val m = MapPerformanceMonitor(100L)
        m.reset(0L)
        m.recordFrame(20L, -5L)
        m.recordGpsNs(-1L)
        m.recordStepNs(-1L)
        val s = requireNotNull(m.snapshotIfDue(100L))
        assertEquals(0, s.deliveredFrames)
        assertEquals(0, s.gpsUpdates)
        assertEquals(0, s.mapSteps)
        val line = s.logLine(1_048_576L)
        assertTrue(line.contains("java_heap_mb=1.0"))
        assertTrue(line.contains("delivered_frame_fps="))
        assertTrue(line.contains("dropped_frames=0"))
        assertTrue(line.contains("frame_lock_wait_avg_ms="))
        assertTrue(line.contains("host_draw_avg_ms="))
        assertTrue(line.contains("road_ctx_avg_ms="))
        assertFalse(line.contains("latitude"))
        assertFalse(line.contains("longitude"))
    }
    @Test fun adaptiveFrameRatePrefersInteractionThenDrivingThenIdle() {
        assertEquals(33L, MapFrameRatePolicy.delayMs(
            hasFix = true, moving = true, interactive = true))
        assertEquals(42L, MapFrameRatePolicy.delayMs(
            hasFix = true, moving = true, interactive = false))
        assertEquals(80L, MapFrameRatePolicy.delayMs(
            hasFix = true, moving = false, interactive = false))
        assertEquals(120L, MapFrameRatePolicy.delayMs(
            hasFix = false, moving = false, interactive = false))
    }
}
