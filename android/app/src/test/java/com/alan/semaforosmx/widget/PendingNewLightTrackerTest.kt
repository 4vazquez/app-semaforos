package com.alan.semaforosmx.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingNewLightTrackerTest {

    private val tracker = PendingNewLightTracker(timeoutMillis = 1_000L)

    @Test
    fun armOnceThenConsumeReturnsSameLocation() {
        assertTrue(tracker.arm(25.67, -100.31, 12f, nowMillis = 5_000L))
        assertTrue(tracker.isArmed)

        val pending = tracker.consume()
        assertEquals(25.67, pending!!.latitude, 0.0)
        assertEquals(-100.31, pending.longitude, 0.0)
        assertEquals(12f, pending.accuracyMeters)
        assertFalse(tracker.isArmed)
        assertNull(tracker.consume())
    }

    @Test
    fun secondArmIsRejectedWhileArmed() {
        assertTrue(tracker.arm(25.0, -100.0, 10f, nowMillis = 0L))
        assertFalse(tracker.arm(26.0, -101.0, 10f, nowMillis = 100L))
        // El primero sigue armado, intacto.
        assertEquals(25.0, tracker.current!!.latitude, 0.0)
    }

    @Test
    fun cancelClearsWithoutCreating() {
        tracker.arm(25.0, -100.0, 10f, nowMillis = 0L)
        tracker.cancel()
        assertFalse(tracker.isArmed)
        assertNull(tracker.consume())
    }

    @Test
    fun expiryAfterTimeout() {
        tracker.arm(25.0, -100.0, 10f, nowMillis = 10_000L)
        assertFalse(tracker.isExpired(nowMillis = 10_999L))
        assertTrue(tracker.isExpired(nowMillis = 11_000L))
    }

    @Test
    fun notArmedNeverExpires() {
        assertFalse(tracker.isExpired(nowMillis = Long.MAX_VALUE))
    }

    @Test
    fun autoCreateOnMovementWhenArmedAndNotExpired() {
        assertTrue(tracker.arm(25.0, -100.0, 10f, nowMillis = 10_000L))
        assertTrue(tracker.shouldAutoCreateOnMovement(startedAtMillis = 10_500L))
        assertTrue(tracker.shouldAutoCreateOnMovement(startedAtMillis = 10_999L))
    }

    @Test
    fun noAutoCreateOnMovementBeforeArming() {
        assertTrue(tracker.arm(25.0, -100.0, 10f, nowMillis = 10_000L))
        assertFalse(tracker.shouldAutoCreateOnMovement(startedAtMillis = 9_999L))
    }

    @Test
    fun noAutoCreateOnMovementAfterTimeout() {
        assertTrue(tracker.arm(25.0, -100.0, 10f, nowMillis = 10_000L))
        assertFalse(tracker.shouldAutoCreateOnMovement(startedAtMillis = 11_000L))
    }

    @Test
    fun noAutoCreateOnMovementWhenNotArmed() {
        assertFalse(tracker.shouldAutoCreateOnMovement(startedAtMillis = 5_000L))
    }

    @Test
    fun noAutoCreateOnMovementAfterCancelOrConsume() {
        assertTrue(tracker.arm(25.0, -100.0, 10f, nowMillis = 0L))
        tracker.cancel()
        assertFalse(tracker.shouldAutoCreateOnMovement(startedAtMillis = 500L))
        assertTrue(tracker.arm(25.0, -100.0, 10f, nowMillis = 0L))
        tracker.consume()
        assertFalse(tracker.shouldAutoCreateOnMovement(startedAtMillis = 500L))
    }
}
