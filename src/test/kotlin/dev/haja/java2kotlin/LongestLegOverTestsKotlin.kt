package dev.haja.java2kotlin

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random


class LongestLegOverTestsKotlin {
    private val legs = listOf(
        leg("one hour", Duration.ofHours(1)),
        leg("one day", Duration.ofDays(1)),
        leg("two hours", Duration.ofHours(2))
    )
    private val oneDay = Duration.ofDays(1)

    @Test
    @DisplayName("빈 리스트일 때 null 반환")
    fun `is absent when no legs`() {
        // given
        val emptyLegs = emptyList<Leg>()

        // when
        val result = emptyLegs.longestLegOver(Duration.ZERO)

        // then
        assertThat(result).isNull()
    }

    @Test
    @DisplayName("충분히 긴 구간이 없을 때 null 반환")
    fun `is absent when no legs long enough`() {
        // when
        val result = legs.longestLegOver(oneDay)

        // then
        assertThat(result).isNull()
    }

    @Test
    @DisplayName("하나의 구간만 조건을 만족할 때 해당 구간 반환")
    fun `is longest leg when one match`() {
        // given
        val threshold = oneDay.minusMillis(1)

        // when
        val result = legs.longestLegOver(threshold)

        // then
        assertThat(result).isNotNull()
        assertThat(result?.description).isEqualTo("one day")
    }

    @Test
    @DisplayName("여러 구간이 조건을 만족할 때 가장 긴 구간 반환")
    fun `is longest leg when more than one match`() {
        // given
        val threshold = Duration.ofMinutes(59)

        // when
        val result = legs.longestLegOver(threshold)

        // then
        assertThat(result).isNotNull()
        assertThat(result?.description).isEqualTo("one day")
    }

    private fun leg(description: String, duration: Duration): Leg {
        val start = ZonedDateTime.ofInstant(
            Instant.ofEpochSecond(Random.nextInt().toLong()),
            ZoneId.of("UTC")
        )
        return Leg(description, start, start.plus(duration))
    }
}