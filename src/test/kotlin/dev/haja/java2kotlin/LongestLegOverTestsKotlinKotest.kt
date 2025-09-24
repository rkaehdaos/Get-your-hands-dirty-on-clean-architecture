package dev.haja.java2kotlin

import io.kotest.core.spec.style.BehaviorSpec

class LongestLegOverTestsKotlinKotest: BehaviorSpec({

    Context("a broomstick should be able to be fly and come back on it's own") {
        Given("a broomstick") {
            When("I sit on it") {
                Then("I should be able to fly") {
                    // test code
                }
            }
            When("I throw it away") {
                Then("it should come back") {
                    // test code
                }
            }
        }
    }
})