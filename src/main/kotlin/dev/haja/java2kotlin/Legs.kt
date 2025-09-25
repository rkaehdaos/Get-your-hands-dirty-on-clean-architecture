package dev.haja.java2kotlin

import java.time.Duration


/**
 * 코드1: (filter + maxByOrNull)
 * 시간 복잡도: O(n) + O(n) = O(2n) ≈ O(n)
 * 공간 복잡도: O(k) (k는 필터된 요소의 개수)
 * 반복 횟수: 리스트를 2번 순회
 * - filter로 조건을 만족하는 요소들을 찾아 새 리스트 생성
 * - maxByOrNull로 최댓값 찾기
 */
fun List<Leg>.longestLegOver(duration: Duration): Leg? {
    return filter { it.plannedDuration > duration }
        .maxByOrNull { it.plannedDuration }
}


/**
 * 코드2: fold
 * 시간 복잡도: O(n)
 * 공간 복잡도: O(1) (추가 메모리 사용 없음)
 * 반복 횟수: 리스트를 1번만 순회
 */


// 큰 리스트의 경우 filter 없이 한 번의 순회로 처리
fun List<Leg>.longestLegOver2(duration: Duration): Leg? {
    return fold(null as Leg?) { longest, current ->
        when {
            current.plannedDuration <= duration -> longest
            longest == null -> current
            current.plannedDuration > longest.plannedDuration -> current
            else -> longest
        }
    }
}

