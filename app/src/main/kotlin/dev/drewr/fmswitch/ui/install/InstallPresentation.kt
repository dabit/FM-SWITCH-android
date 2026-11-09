// Ported from FM-SWITCH App/Standard/Install/InstallOutcomeViews.swift and InstallingView.swift.
package dev.drewr.fmswitch.ui.install

import androidx.annotation.StringRes
import dev.drewr.fmswitch.R
import dev.drewr.fmswitch.model.device.InstallCheckState
import dev.drewr.fmswitch.model.settings.StartUp

internal enum class ProblemAction { RESUME, RETRY, MIDIMON, NONE }

internal data class ProblemPresentation(
    @param:StringRes val bodyRes: Int?,
    val usesJobMessage: Boolean,
    val action: ProblemAction,
    val showsClose: Boolean,
)

internal fun problemPresentation(
    canResume: Boolean,
    needsAStepFirst: Boolean,
    mightBeRefused: Boolean,
    midiMonListed: Boolean,
): ProblemPresentation {
    if (canResume) {
        return ProblemPresentation(
            R.string.install_interrupted_body,
            false,
            ProblemAction.RESUME,
            showsClose = false,
        )
    }
    if (needsAStepFirst) {
        return ProblemPresentation(null, true, if (midiMonListed) ProblemAction.MIDIMON else ProblemAction.NONE, true)
    }
    return ProblemPresentation(
        if (mightBeRefused) R.string.install_try_again_might_refuse else R.string.install_try_again,
        false,
        ProblemAction.RETRY,
        showsClose = true,
    )
}

internal fun showsStartUpWarning(
    dependsOnStartUp: Boolean,
    startUp: StartUp?,
): Boolean = dependsOnStartUp && startUp != null && startUp != StartUp.MVave

internal enum class InstallStatusNotice { PLUG_IN, WAITING, CHECKING, FAILED, ALREADY_RUNNING, SAME_IDENTITY, NONE }

internal fun installStatusNotice(
    hasIdentity: Boolean,
    isLoader: Boolean,
    checked: InstallCheckState,
    alreadyInstalled: Boolean,
    sameIdentity: Boolean,
): InstallStatusNotice =
    when {
        !hasIdentity -> InstallStatusNotice.PLUG_IN
        isLoader -> InstallStatusNotice.WAITING
        checked is InstallCheckState.Checking -> InstallStatusNotice.CHECKING
        checked is InstallCheckState.Failed -> InstallStatusNotice.FAILED
        checked is InstallCheckState.Found && alreadyInstalled -> InstallStatusNotice.ALREADY_RUNNING
        checked is InstallCheckState.Found && sameIdentity -> InstallStatusNotice.SAME_IDENTITY
        else -> InstallStatusNotice.NONE
    }

internal fun drawingProgress(
    isRunning: Boolean,
    trackedFraction: Double,
): Float? = if (isRunning) trackedFraction.toFloat() else null
