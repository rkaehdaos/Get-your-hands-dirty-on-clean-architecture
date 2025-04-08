package dev.haja.java2kotlin

import java.time.Duration
import java.time.ZonedDateTime

data class Leg(
    val description: String,
    val plannedStart: ZonedDateTime,
    val plannedEnd: ZonedDateTime
) {
    val plannedDuration: Duration get() = Duration.between(plannedStart, plannedEnd)
}