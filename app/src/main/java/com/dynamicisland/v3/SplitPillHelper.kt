package com.dynamicisland.v3

import com.dynamicisland.model.IslandEvent
import com.dynamicisland.model.IslandState
import com.dynamicisland.service.DynamicIslandServiceV3

/**
 * V3: Split-pill helper — easily show two states side-by-side.
 *
 * Usage:
 *   SplitPillHelper.show(
 *       primary   = IslandState.NowPlaying(...),
 *       secondary = IslandState.StepCounter(...)
 *   )
 */
object SplitPillHelper {

    /** Show a primary state with a secondary in the split-pill. */
    fun show(secondary: IslandState) {
        DynamicIslandServiceV3.sendCriticalEvent(
            IslandEvent.SplitSecondaryState(secondary)
        )
    }

    /** Return to single-activity mode. */
    fun clear() {
        DynamicIslandServiceV3.sendCriticalEvent(IslandEvent.ClearSplit)
    }

    /** Common split combinations. */
    fun musicWithSteps(steps: Int, calories: Int) {
        show(IslandState.StepCounter(steps = steps, calories = calories))
    }

    fun callWithBattery(percentage: Int) {
        show(IslandState.Charging(percentage = percentage, isCharging = true))
    }

    fun navWithWeather(weather: IslandState.Weather) {
        show(weather)
    }
}
