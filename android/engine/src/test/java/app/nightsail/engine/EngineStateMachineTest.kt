package app.nightsail.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineStateMachineTest {

    private class Clock(var t: Long = 0)

    private fun machine(now: Long = 0): Pair<EngineStateMachine, Clock> {
        val clock = Clock(now)
        return EngineStateMachine(now = { clock.t }) to clock
    }

    @Test
    fun `arm transitions IDLE to LOCKED`() {
        val (sm, _) = machine()
        assertTrue(sm.arm(LockMode.SLEEP, lockUntilMillis = 60_000, whitelist = setOf("a")))
        assertEquals(EngineState.LOCKED, sm.state)
        assertTrue(sm.isLocked())
    }

    @Test
    fun `expired lock reports isExpired and stops being locked`() {
        val (sm, clock) = machine()
        sm.arm(LockMode.SLEEP, 60_000, emptySet())
        assertFalse(sm.isExpired())
        clock.t = 61_000   // 推进到到期之后
        assertTrue(sm.isExpired())
        assertFalse(sm.isLocked())
    }

    @Test
    fun `zen mode refuses manual disarm`() {
        val (sm, _) = machine()
        sm.arm(LockMode.ZEN, 60_000, emptySet())
        assertFalse(sm.disarm())
        assertEquals(EngineState.LOCKED, sm.state)
    }

    @Test
    fun `sleep mode allows manual disarm to ENDING`() {
        val (sm, _) = machine()
        sm.arm(LockMode.SLEEP, 60_000, emptySet())
        assertTrue(sm.disarm())
        assertEquals(EngineState.ENDING, sm.state)
    }

    @Test
    fun `temporary pass grants then expires`() {
        val (sm, _) = machine()
        sm.arm(LockMode.SLEEP, 60_000, setOf("wechat"))
        sm.grantTemporaryPass("wechat", durationMs = 5000)
        assertTrue(sm.hasTemporaryPass("wechat"))
    }

    @Test
    fun `emergency unlock only once`() {
        val (sm, _) = machine()
        sm.arm(LockMode.SLEEP, 60_000, emptySet())
        assertTrue(sm.requestEmergencyUnlock())
        assertFalse(sm.requestEmergencyUnlock())
    }

    @Test
    fun `acknowledgeEnded returns to IDLE and clears session`() {
        val (sm, _) = machine()
        sm.arm(LockMode.SLEEP, 60_000, emptySet())
        sm.finishNaturally()
        sm.acknowledgeEnded()
        assertEquals(EngineState.IDLE, sm.state)
        assertEquals(null, sm.session)
    }
}
