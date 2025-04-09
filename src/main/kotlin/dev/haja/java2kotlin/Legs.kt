package dev.haja.java2kotlin

import java.time.Duration

fun List<Leg>.longestLegOver(
    duration: Duration
): Leg? {
    val longestLeg = maxByOrNull(Leg::plannedDuration)
    return when {
        longestLeg == null -> null
        longestLeg.plannedDuration > duration -> longestLeg
        else -> null
    }
}