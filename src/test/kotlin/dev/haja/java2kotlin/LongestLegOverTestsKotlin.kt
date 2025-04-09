package dev.haja.java2kotlin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.ThreadLocalRandom


class LongestLegOverTestsKotlin {
    private val legs = listOf(
        leg("one hour", Duration.ofHours(1)),
        leg("one day", Duration.ofDays(1)),
        leg("two hours", Duration.ofHours(2))
    )
    private val oneDay = Duration.ofDays(1)

    @Test
    fun `is_absent_when_no_legs`() {
        assertNull(emptyList<Leg>().longestLegOver(Duration.ZERO))
    }

    @Test
    fun `is_absent_when_no_legs_long_enough`() {
        assertNull(legs.longestLegOver(oneDay))
    }

    @Test
    fun `is_longest_leg_when_one_match`() {
        assertEquals(
            "one day",
            legs.longestLegOver(oneDay.minusMillis(1))
            !!.description
        )
    }

    @Test
    fun `is_longest_leg_when_more_than_one_match`() {
        assertEquals(
            "one day",
            legs.longestLegOver(Duration.ofMinutes(59))
            ?.description
        )
    }

    private fun leg(description: String, duration: Duration): Leg {
        val start = ZonedDateTime.ofInstant(
            Instant.ofEpochSecond(ThreadLocalRandom.current().nextInt().toLong()),
            ZoneId.of("UTC"))
        return Leg(description, start, start.plus(duration))
    }
}