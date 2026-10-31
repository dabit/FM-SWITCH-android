// Ported from FM-SWITCH App/Model/DeviceController+States.swift.
package dev.drewr.fmswitch.model.device

import dev.drewr.fm1.updater.Fm1UpdateError
import dev.drewr.fmswitch.model.library.StoredFirmware

data class FlashJob(
    val steps: List<StoredFirmware>,
    val confirmsSwitchingFirmware: Boolean = false,
    val confirmsSameIdentity: Boolean = false,
    val index: Int = 0,
    val phase: FlashPhase = FlashPhase.Preparing,
    val needsAStepFirst: Boolean = false,
    val mightBeRefused: Boolean = false,
) {
    val current: StoredFirmware get() = steps[index]
    val isRunning: Boolean get() = phase !is FlashPhase.Succeeded && phase !is FlashPhase.Failed

    val phaseFraction: Double?
        get() =
            when (val currentPhase = phase) {
                is FlashPhase.Staging -> (currentPhase.requests / 75.0).coerceAtMost(1.0)

                is FlashPhase.Writing -> currentPhase.fraction

                is FlashPhase.Verifying -> currentPhase.fraction

                is FlashPhase.Succeeded -> 1.0

                FlashPhase.Preparing,
                FlashPhase.WaitingForLoader,
                FlashPhase.Rebooting,
                is FlashPhase.Failed,
                -> null
            }

    companion object {
        fun canResume(error: Throwable): Boolean =
            when (error) {
                is Fm1UpdateError.Disconnected -> error.phase == Fm1UpdateError.Phase.WRITING
                is Fm1UpdateError.Stalled -> error.phase == Fm1UpdateError.Phase.WRITING
                Fm1UpdateError.LoaderNotFound -> true
                else -> false
            }

        fun isRefusal(error: Throwable): Boolean =
            error is Fm1UpdateError.BootloaderMismatch || error is Fm1UpdateError.PackageRefused

        fun mightBeRefused(error: Throwable): Boolean =
            error is Fm1UpdateError.Stalled && error.phase == Fm1UpdateError.Phase.STAGING
    }
}
