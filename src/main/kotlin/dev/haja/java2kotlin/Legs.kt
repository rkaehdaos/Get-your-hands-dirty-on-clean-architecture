package dev.haja.java2kotlin

import java.time.Duration


fun longestLegOver(
    legs: List<Leg>,
    duration: Duration
): Leg? {
    val longestLeg: Leg? = legs.maxByOrNull(Leg::plannedDuration)
    if (longestLeg != null && longestLeg.plannedDuration > duration)
        return longestLeg
    return null
}
