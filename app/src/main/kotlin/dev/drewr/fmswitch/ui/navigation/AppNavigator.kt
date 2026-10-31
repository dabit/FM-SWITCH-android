// Ported from FM-SWITCH App/Standard/StandardRootView.swift.
package dev.drewr.fmswitch.ui.navigation

import android.net.Uri

interface AppNavigator {
    fun openFirmwares()

    fun openFirmware(entryId: String)

    fun openMyFirmwares()

    fun openSettings()

    fun openBeforeYouStart()

    fun openOutcome()

    fun back()
}

internal object Routes {
    const val HOME = "home"
    const val FIRMWARES = "firmwares"
    const val FIRMWARE = "firmware/{entryId}"
    const val MY_FIRMWARES = "my-firmwares"
    const val SETTINGS = "settings"
    const val BEFORE_YOU_START = "before-you-start"
    const val OUTCOME = "outcome"

    fun firmware(entryId: String) = "firmware/${Uri.encode(entryId)}"
}
