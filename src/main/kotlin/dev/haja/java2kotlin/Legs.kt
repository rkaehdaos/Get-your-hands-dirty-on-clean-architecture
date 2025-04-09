package dev.haja.java2kotlin

import java.time.Duration


fun longestLegOver(
    legs: List<Leg>,
    duration: Duration
): Leg? {
    var result: Leg? = null
    for (leg in legs) {
        if (leg.isLongerThan(duration))
            if (result == null ||
                leg.isLongerThan(result.plannedDuration))
                result = leg
    }
    return result
}

private fun Leg.isLongerThan(duration: Duration) =
    plannedDuration > duration
