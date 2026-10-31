// Ported from FM-SWITCH App/Model/InstallProgress.swift.
package dev.drewr.fmswitch.model.device

data class InstallProgress(
    val fraction: Double,
    val stage: Stage,
) {
    enum class Stage(
        val title: String,
    ) {
        GettingReady("Getting ready"),
        Installing("Installing"),
        Checking("Checking"),
        Restarting("Restarting"),
    }

    constructor(job: FlashJob) : this(position(job).first, position(job).second)

    class Tracker {
        var current = InstallProgress(0.0, Stage.GettingReady)
            private set

        fun update(job: FlashJob) {
            if (job.phase is FlashPhase.Failed) return
            val next = InstallProgress(job)
            if (next.fraction >= current.fraction) {
                current = next
            } else if (next.stage.ordinal > current.stage.ordinal) {
                current = current.copy(stage = next.stage)
            }
        }
    }

    companion object {
        private fun position(job: FlashJob): Pair<Double, Stage> {
            val (within, stage) =
                when (val phase = job.phase) {
                    FlashPhase.Preparing -> 0.0 to Stage.GettingReady
                    is FlashPhase.Staging -> point(0.0, 0.10, phase.requests / 75.0) to Stage.GettingReady
                    FlashPhase.WaitingForLoader -> 0.11 to Stage.GettingReady
                    is FlashPhase.Writing -> point(0.12, 0.85, phase.fraction) to Stage.Installing
                    is FlashPhase.Verifying -> point(0.85, 0.97, phase.fraction) to Stage.Checking
                    FlashPhase.Rebooting -> 0.985 to Stage.Restarting
                    is FlashPhase.Succeeded -> 1.0 to Stage.Restarting
                    is FlashPhase.Failed -> 0.0 to Stage.GettingReady
                }
            return ((job.index + within) / job.steps.size.coerceAtLeast(1)).coerceIn(0.0, 1.0) to stage
        }

        private fun point(
            start: Double,
            end: Double,
            fraction: Double,
        ): Double = start + (end - start) * fraction.coerceIn(0.0, 1.0)
    }
}
