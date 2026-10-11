package com.gadgetvala.lookaway.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BreakTimerTest {
    private val c = BreakConfig(workMs = 20 * MIN, breakMs = 20 * SEC)

    @Test
    fun `full cycle warns, breaks, then starts the next work period`() {
        var t = BreakTimer().work(0, c)
        assertEquals(20 * MIN - 20 * SEC, t.nextWakeAt(c))

        val (warned, warn) = t.evaluate(20 * MIN - 20 * SEC, c)
        assertEquals(Event.WARN, warn)
        t = warned
        assertEquals(20 * MIN, t.nextWakeAt(c))

        val (onBreak, started) = t.evaluate(20 * MIN, c)
        assertEquals(Event.BREAK_STARTED, started)
        assertEquals(Phase.BREAK, onBreak.phase)

        val (working, finished) = onBreak.evaluate(20 * MIN + 20 * SEC, c)
        assertEquals(Event.BREAK_FINISHED, finished)
        assertEquals(Phase.WORKING, working.phase)
        assertEquals(40 * MIN + 20 * SEC, working.endsAt)
    }

    @Test
    fun `work time freezes while the screen is off`() {
        val t = BreakTimer().work(0, c).screenOff(5 * MIN)
        assertNull(t.nextWakeAt(c))
        assertNull(t.evaluate(60 * MIN, c).second) // never breaks with the screen off

        val back = t.screenOn(5 * MIN + 10 * SEC, c) // off for less than a break
        assertEquals(15 * MIN, back.timeLeft(5 * MIN + 10 * SEC))
    }

    @Test
    fun `by default the countdown continues after the screen comes back, however long it was off`() {
        val t = BreakTimer().work(0, c).screenOff(15 * MIN).screenOn(75 * MIN, c)
        assertEquals(5 * MIN, t.timeLeft(75 * MIN))
        assertEquals(80 * MIN - 20 * SEC, t.nextWakeAt(c)) // running again, not frozen
    }

    @Test
    fun `reset mode restarts only after the screen was off for a whole break`() {
        val reset = c.copy(resetAfterScreenOff = true)
        val t = BreakTimer().work(0, reset).screenOff(15 * MIN)
        assertEquals(20 * MIN, t.screenOn(15 * MIN + 20 * SEC, reset).timeLeft(15 * MIN + 20 * SEC))
        assertEquals(5 * MIN, t.screenOn(15 * MIN + 5 * SEC, reset).timeLeft(15 * MIN + 5 * SEC))
    }

    @Test
    fun `lock screen keeps the timer frozen and screen-off calls are idempotent`() {
        val off = BreakTimer().work(0, c).screenOff(5 * MIN)
        assertEquals(off, off.screenOff(6 * MIN)) // repeated "still locked" polls don't move the off time
        assertEquals(15 * MIN, off.screenOn(7 * MIN, c).screenOff(7 * MIN).remaining) // instant re-lock loses nothing
    }

    @Test
    fun `pause ends into a fresh work period`() {
        val t = BreakTimer().work(0, c).pause(MIN, 30 * MIN)
        assertEquals(31 * MIN, t.nextWakeAt(c))
        val (resumed, event) = t.evaluate(31 * MIN, c)
        assertEquals(Event.PAUSE_ENDED, event)
        assertEquals(20 * MIN, resumed.timeLeft(31 * MIN))
    }

    @Test
    fun `started while the screen is off keeps a full cycle`() {
        val t = BreakTimer().screenOff(0).work(MIN, c).screenOn(10 * MIN, c)
        assertEquals(20 * MIN, t.timeLeft(10 * MIN))
    }

    private companion object {
        const val SEC = 1_000L
        const val MIN = 60 * SEC
    }
}
