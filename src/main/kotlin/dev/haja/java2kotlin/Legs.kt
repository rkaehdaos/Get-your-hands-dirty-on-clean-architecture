package dev.haja.java2kotlin

import java.time.Duration

fun List<Leg>.longestLegOver(duration: Duration): Leg? {
    return filter { it.plannedDuration > duration }
        .maxByOrNull { it.plannedDuration }
}