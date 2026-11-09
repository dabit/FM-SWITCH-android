// Ported from FM-SWITCH App/Model/DeviceController+States.swift.
package dev.drewr.fmswitch.model.device

import dev.drewr.fm1.updater.Fm1UpdateError
import dev.drewr.fmswitch.model.library.StoredFirmware
import dev.drewr.fmswitch.model.settings.StartUp
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class FlashJob(
    val steps: List<StoredFirmware>,
    val confirmsSwitchingFirmware: Boolean = false,
    val confirmsSameIdentity: Boolean = false,
    val index: Int = 0,
    val phase: FlashPhase = FlashPhase.Preparing,
    val needsAStepFirst: Boolean = false,
    val mightBeRefused: Boolean = false,
    val confirmsReinstallingOfficial: Boolean = false,
    val guide: dev.drewr.fm1.pkg.LeavingGuide? = null,
    val startedOnV16: Boolean = false,
    val writtenFraction: Double = 0.0,
) {
    val current: StoredFirmware get() = steps[index]
    val isRunning: Boolean get() = phase !is FlashPhase.Succeeded && phase !is FlashPhase.Failed

    val phaseFraction: Double?
        get() =
            when (val currentPhase = phase) {
                is FlashPhase.Staging -> (currentPhase.requests / 75.0).coerceAtMost(1.0)

                is FlashPhase.Writing -> currentPhase.fraction

                is FlashPhase.SecondPass -> currentPhase.fraction

                is FlashPhase.Succeeded -> 1.0

                FlashPhase.Preparing,
                FlashPhase.WaitingForLoader,
                FlashPhase.Rebooting,
                is FlashPhase.Failed,
                -> null
            }

    companion object {
        val RETURN_SEARCH: Duration = 20.seconds

        fun canResume(error: Throwable): Boolean =
            when (error) {
                is Fm1UpdateError.Disconnected -> error.phase == Fm1UpdateError.Phase.WRITING
                is Fm1UpdateError.Stalled -> error.phase == Fm1UpdateError.Phase.WRITING
                Fm1UpdateError.LoaderNotFound -> true
                else -> false
            }

        fun isRefusal(error: Throwable): Boolean =
            error is Fm1UpdateError.BootloaderMismatch || error is Fm1UpdateError.PackageRefused

        fun mightHaveFinished(
            error: Throwable,
            written: Double,
            reinstalling: Boolean,
        ): Boolean =
            when (error) {
                Fm1UpdateError.DeviceDidNotReturn -> {
                    true
                }

                is Fm1UpdateError.Stalled -> {
                    error.phase == Fm1UpdateError.Phase.WRITING && written >= 0.9 && !reinstalling
                }

                is Fm1UpdateError.Disconnected -> {
                    error.phase == Fm1UpdateError.Phase.WRITING && written >= 0.9 && !reinstalling
                }

                else -> {
                    false
                }
            }

        fun needsAStepFirst(
            error: Throwable,
            item: StoredFirmware,
            running: dev.drewr.fm1.pkg.LeavingGuide?,
            startUp: StartUp?,
        ): Boolean {
            if (!isRefusal(error)) return false
            if (item.source == StoredFirmware.Source.Official) return true
            return running?.route(dev.drewr.fm1.pkg.LeavingDestination.V15)?.let {
                it != dev.drewr.fm1.pkg.LeavingRoute.DIRECT
            } ?: (startUp != StartUp.MVave)
        }

        fun failureMessage(
            error: Throwable,
            item: StoredFirmware,
            running: String?,
            needsAStepFirst: Boolean,
        ): String {
            if (!isRefusal(error)) return error.message.orEmpty()
            val first = if (needsAStepFirst) "MIDIMON 0.4 or LOOM-8" else "V15"
            return "Your FM-1 is fine. It's still running ${running ?: "the firmware it had"}, which won't take " +
                "${item.name} from here. Install $first first, then ${item.name}."
        }

        fun mightBeRefused(error: Throwable): Boolean =
            error is Fm1UpdateError.Stalled && error.phase == Fm1UpdateError.Phase.STAGING
    }
}
