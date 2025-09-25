package dev.haja.java2kotlin

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

class LongestLegOverTestsKotlinKotest : FreeSpec({

    // DSL을 사용한 테스트 데이터 생성
    fun leg(description: String, duration: Duration): Leg {
        val start = ZonedDateTime.ofInstant(
            Instant.ofEpochSecond(Random.nextInt().toLong()),
            ZoneId.of("UTC")
        )
        return Leg(description, start, start.plus(duration))
    }

    // infix 함수를 사용한 더 표현력 있는 테스트 데이터 생성
    infix fun String.lasting(duration: Duration) = leg(this, duration)
    infix fun String.lasting(duration: kotlin.time.Duration) = leg(this, duration.toJavaDuration())

    // 테스트 데이터를 lazy 초기화로 관리
    val testLegs by lazy {
        listOf(
            "one hour" lasting 1.hours,
            "one day" lasting Duration.ofDays(1),
            "two hours" lasting 2.hours
        )
    }

    val oneDay = Duration.ofDays(1)

    "List<Leg>.longestLegOver" - {

        "빈 리스트 처리" - {
            "threshold와 관계없이 항상 null 반환" {
                // Data-driven testing으로 여러 threshold 값 테스트
                val thresholds = listOf(
                    Duration.ZERO,
                    Duration.ofHours(1),
                    Duration.ofDays(1),
                    Duration.ofDays(365)
                )
                thresholds.forEach { threshold ->
                    emptyList<Leg>().longestLegOver(threshold).shouldBeNull()
                }
            }
        }

        "단일 구간 처리" - {
            val singleLeg = listOf("single" lasting 1.hours)

            "구간이 threshold보다 길면 해당 구간 반환" {
                val result = singleLeg.longestLegOver(30.minutes.toJavaDuration())
                result.shouldNotBeNull()
                result.description shouldBe "single"
            }

            "구간이 threshold보다 짧거나 같으면 null 반환" {
                val testData = listOf(
                    "1시간" to 1.hours,
                    "2시간" to 2.hours,
                    "1일" to 24.hours
                )
                testData.forEach { (name, threshold) ->
                    singleLeg.longestLegOver(threshold.toJavaDuration()).shouldBeNull()
                }
            }
        }

        "여러 구간 처리" - {
            "모든 구간이 threshold보다 짧거나 같으면 null 반환" {
                testLegs.longestLegOver(oneDay).shouldBeNull()
            }

            "하나의 구간만 조건을 만족할 때" - {
                "해당 구간을 반환" {
                    val threshold = oneDay.minusMillis(1)
                    val result = testLegs.longestLegOver(threshold)

                    result.shouldNotBeNull()
                    result.description shouldBe "one day"
                }
            }

            "여러 구간이 조건을 만족할 때" - {
                "가장 긴 구간을 반환" {
                    val threshold = Duration.ofMinutes(59)
                    val result = testLegs.longestLegOver(threshold)

                    result.shouldNotBeNull()
                    result.description shouldBe "one day"
                }

                "동일한 길이의 구간이 여러 개일 때" {
                    val duplicateLegs = listOf(
                        "first two hours" lasting 2.hours,
                        "second two hours" lasting 2.hours,
                        "one hour" lasting 1.hours
                    )

                    val result = duplicateLegs.longestLegOver(90.minutes.toJavaDuration())
                    result.shouldNotBeNull()
                    // Kotlin의 maxByOrNull은 첫 번째로 만난 최댓값을 반환
                    result.description shouldBe "first two hours"
                }
            }
        }

        "엣지 케이스" - {
            "구간 길이가 정확히 threshold와 같은 경우" {
                val legs = listOf("exact" lasting 1.hours)
                legs.longestLegOver(1.hours.toJavaDuration()).shouldBeNull()
            }

            "구간 길이가 threshold보다 1밀리초 긴 경우" {
                val legs = listOf("slightly longer" lasting Duration.ofHours(1).plusMillis(1))
                val result = legs.longestLegOver(Duration.ofHours(1))

                result.shouldNotBeNull()
                result.description shouldBe "slightly longer"
            }

            "Duration.ZERO를 threshold로 사용" {
                val legs = listOf(
                    "zero duration" lasting Duration.ZERO,
                    "positive duration" lasting 1.minutes
                )

                val result = legs.longestLegOver(Duration.ZERO)
                result?.description shouldBe "positive duration"
            }
        }

        "불변 조건 (Property-based testing)" - {
            "반환된 구간은 항상 입력 리스트에 포함되어야 함" {
                repeat(10) { // 10번의 랜덤 테스트
                    val legs = List(Random.nextInt(1, 10)) { index ->
                        "leg$index" lasting Duration.ofMinutes(Random.nextLong(1, 1000))
                    }
                    val threshold = Duration.ofMinutes(Random.nextLong(0, 500))

                    val result = legs.longestLegOver(threshold)
                    result?.let {
                        legs.contains(it) shouldBe true
                    }
                }
            }

            "반환된 구간은 항상 threshold보다 길어야 함" {
                repeat(10) { // 10번의 랜덤 테스트
                    val legs = List(Random.nextInt(1, 10)) { index ->
                        "leg$index" lasting Duration.ofHours(Random.nextLong(1, 100))
                    }
                    val threshold = Duration.ofHours(Random.nextLong(0, 50))

                    val result = legs.longestLegOver(threshold)
                    result?.let {
                        it.plannedDuration > threshold
                    } ?: true // null인 경우도 유효함
                    // 검증
                    result?.let {
                        (it.plannedDuration > threshold) shouldBe true
                    }
                }
            }

            "반환된 구간보다 긴 구간은 리스트에 없어야 함" {
                repeat(10) {
                    val legs = List(Random.nextInt(1, 10)) { index ->
                        "leg$index" lasting Duration.ofMinutes(Random.nextLong(1, 1000))
                    }
                    val threshold = Duration.ofMinutes(Random.nextLong(0, 500))

                    val result = legs.longestLegOver(threshold)
                    result?.let { longest ->
                        legs.filter { it.plannedDuration > threshold }
                            .none { it.plannedDuration > longest.plannedDuration } shouldBe true
                    }
                }
            }
        }

        "실제 시나리오" - {
            "항공편 경유 시간 분석" {
                val flights = listOf(
                    "ICN → LAX" lasting Duration.ofHours(11),
                    "LAX → JFK" lasting Duration.ofHours(5),
                    "JFK → LHR" lasting Duration.ofHours(7),
                    "LHR → ICN" lasting Duration.ofHours(11).plusMinutes(30)
                )

                // 10시간 이상의 장거리 비행 찾기
                val longHaul = flights.longestLegOver(Duration.ofHours(10))
                longHaul.shouldNotBeNull()
                longHaul.description shouldBe "LHR → ICN"

                // 12시간 이상의 초장거리 비행 찾기
                val ultraLongHaul = flights.longestLegOver(Duration.ofHours(12))
                ultraLongHaul.shouldBeNull()
            }

            "마라톤 구간별 기록 분석" {
                val segments = listOf(
                    "0-10km" lasting Duration.ofMinutes(45),
                    "10-20km" lasting Duration.ofMinutes(47),
                    "20-30km" lasting Duration.ofMinutes(50),
                    "30-40km" lasting Duration.ofMinutes(55),
                    "40-42.195km" lasting Duration.ofMinutes(12)
                )

                // 50분 이상 걸린 구간 찾기
                val slowestSegment = segments.longestLegOver(Duration.ofMinutes(50))
                slowestSegment?.description shouldBe "30-40km"
            }
        }
    }
})