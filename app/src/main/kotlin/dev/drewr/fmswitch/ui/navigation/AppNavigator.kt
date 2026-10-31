// Ported from FM-SWITCH App/Standard/StandardRootView.swift.
package dev.drewr.fmswitch.ui.navigation

import android.net.Uri

interface AppNavigator {
    fun openFirmwares()

    fun openFirmware(entryId: String)

    fun openMyFirmwares()

    fun openSettings()

    /** Before you start for a saved firmware ([StoredFirmware.id]); [requiresOfficial] plans V15 first. */
    fun openBeforeYouStart(
        targetId: String,
        requiresOfficial: Boolean = false,
    )

    fun openOutcome()

    fun back()
}

internal object Routes {
    const val HOME = "home"
    const val FIRMWARES = "firmwares"
    const val FIRMWARE = "firmware/{entryId}"
    const val MY_FIRMWARES = "my-firmwares"
    const val SETTINGS = "settings"
    const val BEFORE_YOU_START = "before-you-start/{targetId}?requiresOfficial={requiresOfficial}"
    const val OUTCOME = "outcome"

    fun firmware(entryId: String) = "firmware/${Uri.encode(entryId)}"

    fun beforeYouStart(
        targetId: String,
        requiresOfficial: Boolean,
    ) = "before-you-start/${Uri.encode(targetId)}?requiresOfficial=$requiresOfficial"
}
