package com.example

import com.example.data.model.EffortScale
import com.example.domain.RepDayRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Fixes from on-device testing (November 2026). */
class TestingFixesTest {

    @Test
    fun setCaption_showsRepsTogetherWithEffortTarget_evenWhenRepsMatchExercise() {
        // Working set, no Top/Backoff, exercise in "Reps + RPE" mode: reps must not disappear.
        assertEquals(
            "Target 8-10 reps · RPE 8",
            RepDayRules.formatActiveSetTargetCaption("8-10", "8", exerciseRepRange = "8-10", scale = EffortScale.RPE)
        )
    }

    @Test
    fun setCaption_rirScaleShowsRepsAndRir() {
        assertEquals(
            "Target 8-10 reps · 2 RIR",
            RepDayRules.formatActiveSetTargetCaption("8-10", "8", exerciseRepRange = "8-10", scale = EffortScale.RIR)
        )
    }

    @Test
    fun setCaption_repsOnly_hiddenWhenSameAsExercise_shownWhenDifferent() {
        assertNull(RepDayRules.formatActiveSetTargetCaption("8-10", null, exerciseRepRange = "8-10", scale = EffortScale.RPE))
        assertEquals(
            "Target 5 reps",
            RepDayRules.formatActiveSetTargetCaption("5", null, exerciseRepRange = "8-10", scale = EffortScale.RPE)
        )
    }

    @Test
    fun setCaption_effortOnly_saysLiftTo() {
        assertEquals(
            "Lift to RPE 9",
            RepDayRules.formatActiveSetTargetCaption(null, "9", exerciseRepRange = "8-10", scale = EffortScale.RPE)
        )
        assertNull(RepDayRules.formatActiveSetTargetCaption("  ", null, exerciseRepRange = "8-10", scale = EffortScale.RPE))
    }
}
