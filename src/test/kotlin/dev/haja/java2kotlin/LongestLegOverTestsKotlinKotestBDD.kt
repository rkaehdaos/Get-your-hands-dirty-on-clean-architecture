package dev.haja.java2kotlin

import io.kotest.core.spec.style.BehaviorSpec
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

class LongestLegOverTestsKotlinKotestBDD : BehaviorSpec({

    // 테스트 데이터 생성을 위한 헬퍼 함수들
    fun leg(description: String, duration: Duration): Leg {
        val start = ZonedDateTime.ofInstant(
            Instant.ofEpochSecond(Random.nextInt().toLong()),
            ZoneId.of("UTC")
        )
        return Leg(description, start, start.plus(duration))
    }

    // DSL을 통한 더 표현력 있는 테스트 데이터 생성
    infix fun String.lasting(duration: Duration) = leg(this, duration)
    infix fun String.lasting(duration: kotlin.time.Duration) = leg(this, duration.toJavaDuration())

    // 자주 사용되는 기준값들
    val oneHour = Duration.ofHours(1)
    val oneDay = Duration.ofDays(1)
    val thirtyMinutes = 30.minutes.toJavaDuration()
    val ninetyMinutes = 90.minutes.toJavaDuration()

    Given("구간이 전혀 없는 상황") {
        val emptyLegList = emptyList<Leg>()

        When("임의의 임계값으로 가장 긴 구간을 찾으려 할 때") {
            Then("항상 null을 반환해야 함") {
                emptyLegList.longestLegOver(Duration.ZERO).shouldBeNull()
                emptyLegList.longestLegOver(oneHour).shouldBeNull()
                emptyLegList.longestLegOver(oneDay).shouldBeNull()
                emptyLegList.longestLegOver(Duration.ofDays(365)).shouldBeNull()
            }
        }
    }

    Given("단 하나의 구간만 존재하는 상황") {
        val singleLeg = "하나의 구간" lasting 1.hours

        When("구간이 임계값보다 긴 경우") {
            val threshold = thirtyMinutes

            Then("해당 구간을 반환해야 함") {
                val result = listOf(singleLeg).longestLegOver(threshold)
                result.shouldNotBeNull()
                result.description shouldBe "하나의 구간"
                result.plannedDuration shouldBe 1.hours.toJavaDuration()
            }
        }

        When("구간이 임계값보다 짧거나 같은 경우") {
            Then("null을 반환해야 함") {
                listOf(singleLeg).longestLegOver(oneHour).shouldBeNull()
                listOf(singleLeg).longestLegOver(2.hours.toJavaDuration()).shouldBeNull()
                listOf(singleLeg).longestLegOver(oneDay).shouldBeNull()
            }
        }
    }

    Given("여러 구간이 존재하는 상황") {
        val multipleLegs = listOf(
            "짧은 구간" lasting 1.hours,
            "긴 구간" lasting oneDay,
            "보통 구간" lasting 2.hours
        )

        When("모든 구간이 임계값 이하인 경우") {
            val highThreshold = oneDay

            Then("null을 반환해야 함") {
                multipleLegs.longestLegOver(highThreshold).shouldBeNull()
            }
        }

        When("하나의 구간만 조건을 만족하는 경우") {
            val threshold = oneDay.minusMillis(1)

            Then("해당 구간을 반환해야 함") {
                val result = multipleLegs.longestLegOver(threshold)
                result.shouldNotBeNull()
                result.description shouldBe "긴 구간"
            }
        }

        When("여러 구간이 조건을 만족하는 경우") {
            val lowThreshold = Duration.ofMinutes(59)

            Then("가장 긴 구간을 반환해야 함") {
                val result = multipleLegs.longestLegOver(lowThreshold)
                result.shouldNotBeNull()
                result.description shouldBe "긴 구간"
            }
        }

        When("동일한 길이의 구간이 여러 개인 경우") {
            val duplicateLegs = listOf(
                "첫 번째 2시간" lasting 2.hours,
                "두 번째 2시간" lasting 2.hours,
                "1시간" lasting 1.hours
            )

            Then("첫 번째로 발견된 가장 긴 구간을 반환해야 함") {
                val result = duplicateLegs.longestLegOver(ninetyMinutes)
                result.shouldNotBeNull()
                result.description shouldBe "첫 번째 2시간"
            }
        }
    }

    Given("경계값 테스트 상황") {
        When("구간 길이가 정확히 임계값과 같은 경우") {
            val legs = listOf("정확히 1시간" lasting 1.hours)

            Then("null을 반환해야 함 (threshold는 초과해야 함)") {
                legs.longestLegOver(oneHour).shouldBeNull()
            }
        }

        When("구간 길이가 임계값보다 1밀리초 긴 경우") {
            val legs = listOf("1밀리초 더 긴" lasting oneHour.plusMillis(1))

            Then("해당 구간을 반환해야 함") {
                val result = legs.longestLegOver(oneHour)
                result.shouldNotBeNull()
                result.description shouldBe "1밀리초 더 긴"
            }
        }

        When("Duration.ZERO를 임계값으로 사용하는 경우") {
            val legs = listOf(
                "0초 구간" lasting Duration.ZERO,
                "양수 구간" lasting 1.minutes
            )

            Then("0보다 큰 구간만 반환해야 함") {
                val result = legs.longestLegOver(Duration.ZERO)
                result.shouldNotBeNull()
                result.description shouldBe "양수 구간"
            }
        }
    }

    Given("속성 기반 테스트 - 불변 조건 검증") {
        When("반환된 구간이 존재하는 경우") {
            Then("해당 구간은 원본 리스트에 포함되어야 함") {
                repeat(10) {
                    val legs = List(Random.nextInt(1, 10)) { index ->
                        "무작위 구간$index" lasting Duration.ofMinutes(Random.nextLong(1, 1000))
                    }
                    val threshold = Duration.ofMinutes(Random.nextLong(0, 500))

                    val result = legs.longestLegOver(threshold)
                    if (result != null) {
                        legs.contains(result) shouldBe true
                    }
                }
            }
        }

        When("반환된 구간이 존재하는 경우") {
            Then("해당 구간은 임계값보다 길어야 함") {
                repeat(10) {
                    val legs = List(Random.nextInt(1, 10)) { index ->
                        "무작위 구간$index" lasting Duration.ofHours(Random.nextLong(1, 100))
                    }
                    val threshold = Duration.ofHours(Random.nextLong(0, 50))

                    val result = legs.longestLegOver(threshold)
                    if (result != null) {
                        (result.plannedDuration > threshold) shouldBe true
                    }
                }
            }
        }

        When("반환된 구간이 존재하는 경우") {
            Then("해당 구간보다 긴 구간은 조건을 만족하는 구간 중에 없어야 함") {
                repeat(10) {
                    val legs = List(Random.nextInt(1, 10)) { index ->
                        "무작위 구간$index" lasting Duration.ofMinutes(Random.nextLong(1, 1000))
                    }
                    val threshold = Duration.ofMinutes(Random.nextLong(0, 500))

                    val result = legs.longestLegOver(threshold)
                    if (result != null) {
                        val qualifiedLegs = legs.filter { it.plannedDuration > threshold }
                        qualifiedLegs.none { it.plannedDuration > result.plannedDuration } shouldBe true
                    }
                }
            }
        }
    }

    Given("실제 비즈니스 시나리오") {
        When("국제 항공편 경유 시간을 분석하는 경우") {
            val internationalFlights = listOf(
                "ICN → LAX" lasting Duration.ofHours(11),
                "LAX → JFK" lasting Duration.ofHours(5),
                "JFK → LHR" lasting Duration.ofHours(7),
                "LHR → ICN" lasting Duration.ofHours(11).plusMinutes(30)
            )

            Then("10시간 이상의 장거리 비행을 찾을 수 있어야 함") {
                val longHaulThreshold = Duration.ofHours(10)
                val result = internationalFlights.longestLegOver(longHaulThreshold)

                result.shouldNotBeNull()
                result.description shouldBe "LHR → ICN"
                result.plannedDuration shouldBe Duration.ofHours(11).plusMinutes(30)
            }

            Then("12시간 이상의 초장거리 비행은 찾을 수 없어야 함") {
                val ultraLongHaulThreshold = Duration.ofHours(12)
                val result = internationalFlights.longestLegOver(ultraLongHaulThreshold)

                result.shouldBeNull()
            }
        }

        When("마라톤 구간별 기록을 분석하는 경우") {
            val marathonSegments = listOf(
                "0-10km" lasting Duration.ofMinutes(45),
                "10-20km" lasting Duration.ofMinutes(47),
                "20-30km" lasting Duration.ofMinutes(50),
                "30-40km" lasting Duration.ofMinutes(55),
                "40-42.195km" lasting Duration.ofMinutes(12)
            )

            Then("50분 이상 걸린 가장 느린 구간을 찾을 수 있어야 함") {
                val slowThreshold = Duration.ofMinutes(50)
                val result = marathonSegments.longestLegOver(slowThreshold)

                result.shouldNotBeNull()
                result.description shouldBe "30-40km"
                result.plannedDuration shouldBe Duration.ofMinutes(55)
            }

            Then("60분 이상 걸린 구간은 없어야 함") {
                val verySlow = Duration.ofMinutes(60)
                val result = marathonSegments.longestLegOver(verySlow)

                result.shouldBeNull()
            }
        }

        When("일일 업무 스케줄을 분석하는 경우") {
            val dailyTasks = listOf(
                "회의 준비" lasting Duration.ofMinutes(30),
                "핵심 개발 작업" lasting Duration.ofHours(4),
                "코드 리뷰" lasting Duration.ofHours(1).plusMinutes(30),
                "문서 작성" lasting Duration.ofHours(2),
                "팀 미팅" lasting Duration.ofHours(1)
            )

            Then("3시간 이상의 집중 작업을 찾을 수 있어야 함") {
                val focusTimeThreshold = Duration.ofHours(3)
                val result = dailyTasks.longestLegOver(focusTimeThreshold)

                result.shouldNotBeNull()
                result.description shouldBe "핵심 개발 작업"
                result.plannedDuration shouldBe Duration.ofHours(4)
            }
        }
    }
})