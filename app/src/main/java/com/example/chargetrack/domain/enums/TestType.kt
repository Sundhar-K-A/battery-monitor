package com.example.chargetrack.domain.enums

enum class TestType {
    /** A controlled, repeatable Standard Test (e.g. 20 → 80 %). Eligible for comparison. */
    STANDARD,
    /** An uncontrolled free-form charging session. Not used in longitudinal comparison. */
    FREE_FORM
}
