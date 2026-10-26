package com.vincentwill.calm.digest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleBuildingTest {
    private fun m(h: Int, min: Int = 0) = h * 60 + min

    @Test
    fun `every 2 hours from 8 to 20 is inclusive of both ends`() {
        assertEquals(
            listOf(m(8), m(10), m(12), m(14), m(16), m(18), m(20)),
            IntervalGenerator.generate(m(8), m(20), 120),
        )
    }

    @Test
    fun `end that is not on the grid is not exceeded`() {
        assertEquals(listOf(m(9), m(12), m(15)), IntervalGenerator.generate(m(9), m(17), 180))
    }

    @Test
    fun `minute intervals`() {
        assertEquals(listOf(m(9), m(9, 45), m(10, 30)), IntervalGenerator.generate(m(9), m(10, 30), 45))
    }

    @Test
    fun `start equals end gives one time`() {
        assertEquals(listOf(m(11)), IntervalGenerator.generate(m(11), m(11), 60))
    }

    @Test
    fun `window crossing midnight wraps and comes back sorted`() {
        assertEquals(listOf(m(0), m(22)), IntervalGenerator.generate(m(22), m(1), 120))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `interval below 15 minutes is rejected`() {
        IntervalGenerator.generate(m(8), m(9), 10)
    }

    @Test
    fun `normalize drops duplicates and sorts`() {
        assertEquals(listOf(m(9), m(11), m(17)), ScheduleValidator.normalize(listOf(m(17), m(9), m(11), m(17), m(9))))
    }

    @Test
    fun `gaps under 15 minutes are reported`() {
        val close = ScheduleValidator.tooClose(listOf(m(11), m(11, 10), m(17), m(17, 15)))
        assertEquals(listOf(m(11) to m(11, 10)), close)
    }

    @Test
    fun `wrap around midnight gap is reported`() {
        assertEquals(listOf(m(23, 55) to m(0, 5)), ScheduleValidator.tooClose(listOf(m(0, 5), m(12), m(23, 55))))
    }

    @Test
    fun `single and empty lists have no warnings`() {
        assertTrue(ScheduleValidator.tooClose(listOf(m(11))).isEmpty())
        assertTrue(ScheduleValidator.tooClose(emptyList()).isEmpty())
    }
}
