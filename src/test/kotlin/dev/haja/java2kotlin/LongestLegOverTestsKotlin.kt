package dev.haja.java2kotlin

import dev.haja.java2kotlin.Legs.longestLegOver
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
        assertNull(longestLegOver(emptyList(), Duration.ZERO))
    }

    @Test
    fun `is_absent_when_no_legs_long_enough`() {
        assertNull(longestLegOver(legs, oneDay))
    }

    private fun leg(description: String, duration: Duration): Leg {
        val start = ZonedDateTime.ofInstant(
            Instant.ofEpochSecond(ThreadLocalRandom.current().nextInt().toLong()),
            ZoneId.of("UTC"));
        return Leg(description, start, start.plus(duration));
    }
}