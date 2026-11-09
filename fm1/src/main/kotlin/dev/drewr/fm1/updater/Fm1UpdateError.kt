// Ported from FM1Kit 0.6.0 Updater/FM1UpdateError.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.updater

import dev.drewr.fm1.protocol.BuildId
import dev.drewr.fm1.protocol.Fm1Identity

sealed class Fm1UpdateError(
    message: String,
) : IllegalStateException(message) {
    enum class Phase { STAGING, WRITING }

    data object DeviceNotFound : Fm1UpdateError("No FM-1 found. Check the cable, and close any other app using it.")

    class MultipleDevices(
        count: Int,
    ) : Fm1UpdateError("$count FM-1s answered. Connect only the one to update, and try again.")

    class SwitchNotConfirmed(
        device: String,
        packageName: String,
    ) : Fm1UpdateError(
            "Installing $packageName switches the FM-1 from $device to a different firmware, which can erase its projects and samples. Back them up, then confirm the switch.",
        )

    class AlreadyInstalled(
        val identity: String,
    ) : Fm1UpdateError(
            if (Fm1Identity.parse(identity)?.isOfficial == true) {
                "The FM-1 reports $identity, M-VAVE's official firmware, so it isn't reinstalled. Some firmwares, such as TransitFM1, report that identity: if the FM-1 is running one of them, confirm reinstalling the official firmware."
            } else {
                "The FM-1 is already running $identity, so it isn't reinstalled. Choose another firmware."
            },
        )

    class SameIdentityNotConfirmed(
        identity: String,
    ) : Fm1UpdateError(
            "The FM-1 already reports $identity, the same identity as this package. Confirm you mean to reinstall it, or to install another release of the same firmware.",
        )

    data object CapabilityUnavailable : Fm1UpdateError(
        "The FM-1's open firmware didn't say whether it's ready to update, so the update didn't start. Switch it off and on again, and try again.",
    )

    data object DeviceBusy : Fm1UpdateError(
        "The FM-1's open firmware is busy with another update, so this one didn't start. Close any other updater, switch the FM-1 off and on again, and try again.",
    )

    class ArmRefused(
        code: Int?,
    ) : Fm1UpdateError(
            if (code ==
                null
            ) {
                "The FM-1's open firmware didn't answer when asked to prepare for the update, so the update didn't start. Switch it off and on again, and try again."
            } else {
                "The FM-1's open firmware refused this package (code $code), so the update didn't start."
            },
        )

    class BuildMismatch(
        expected: BuildId,
        reported: BuildId,
    ) : Fm1UpdateError(
            "The update finished, but the FM-1 reports build ${reported.hex.take(
                16,
            )} instead of ${expected.hex.take(16)}.",
        )

    class ModelMismatch(
        device: String,
        packageName: String,
    ) : Fm1UpdateError("The device is $device but the package is for $packageName.")

    class NotFirmware(
        product: String,
    ) : Fm1UpdateError("$product installs a bootloader, not firmware, so it can't be installed as firmware.")

    class BootloaderMismatch(
        requests: Int,
    ) : Fm1UpdateError(
            "The FM-1's firmware stopped after $requests requests, while comparing this package's bootloader with the FM-1's, and should carry on as before. Firmwares with Felucca's original update check, such as AMB-1, HYP-1, DUB-1 and Felucca itself, install M-VAVE's packages only when the FM-1 already has the bootloader the package brings. V16 brings a different bootloader from V15, so they refuse V16 on an FM-1 with V15's bootloader, and refuse V15 once the FM-1's bootloader has been changed, by m-bootloader, M-VAVE's open firmware or V16. First install a firmware with Felucca's updated check, such as MIDIMON 0.4 or LOOM-8, which takes V15, or install V15 from a Mac in the chip's USB download mode. After V16, install V15 with its own bootloader there, and start it fresh.",
        )

    class PackageRefused(
        requests: Int,
    ) : Fm1UpdateError(
            "The FM-1's firmware read this package's header and stopped after $requests requests: it refused the package, and should carry on as before. Its screen may show FAILED and a code. Firmwares with Felucca's original update check refuse m-bootloader's installer and update loaders they don't recognise this way, and those based on Felucca 1.6.1 refuse M-VAVE's open firmware and V16 too. TransitFM1 refuses them as well, since it takes only firmware images the size of V15's. First install a firmware with Felucca's updated check, such as MIDIMON 0.4 or LOOM-8, which takes V15, V16 and open firmware, or install it from a Mac in the chip's USB download mode.",
        )

    class Stalled(
        val phase: Phase,
        requests: Int,
    ) : Fm1UpdateError(
            if (phase ==
                Phase.STAGING
            ) {
                "The FM-1 stopped after $requests requests while checking the package, before restarting into its update loader. It should still start normally. If its screen shows an update error of its own, its firmware refused this package: some custom firmwares bring their own update code and accept only certain packages. Replacing such a firmware needs the chip's USB download mode and a tool that writes there, such as M-Flasher; the firmware's instructions say how to start download mode. If the FM-1 doesn't start, and its firmware supports USB recovery mode (holding OCT− while switching it on), start it that way and install again; otherwise recovering it needs a computer."
            } else {
                "The update loader stopped after $requests requests. Keep the FM-1 connected and switched on, and resume the update. Its screen stays dark while it updates: don't switch it off, as it may not start again until the update is finished."
            },
        )

    class Disconnected(
        val phase: Phase,
        requests: Int,
    ) : Fm1UpdateError(
            if (phase ==
                Phase.STAGING
            ) {
                "The FM-1 stopped after $requests requests while checking the package, before restarting into its update loader. It should still start normally. If its screen shows an update error of its own, its firmware refused this package: some custom firmwares bring their own update code and accept only certain packages. Replacing such a firmware needs the chip's USB download mode and a tool that writes there, such as M-Flasher; the firmware's instructions say how to start download mode. If the FM-1 doesn't start, and its firmware supports USB recovery mode (holding OCT− while switching it on), start it that way and install again; otherwise recovering it needs a computer."
            } else {
                "The update loader disconnected after $requests requests. Reconnect the FM-1 and resume the update, without switching it off: its screen stays dark while it updates, and it may not start again until the update is finished."
            },
        )

    data object LoaderNotFound : Fm1UpdateError(
        "The update loader didn't appear. Reconnect the FM-1 and resume the update.",
    )

    data object DeviceDidNotReturn : Fm1UpdateError(
        "The FM-1 didn't come back after writing. Switch it off and on again.",
    )

    class IdentityMismatch(
        expected: String,
        reported: String,
    ) : Fm1UpdateError("The update finished, but the FM-1 reports $reported instead of $expected.")

    class BadRequest(
        address: Long,
        length: Int,
    ) : Fm1UpdateError(
            "The FM-1 asked for data outside the package ($length bytes at 0x${address.toString(
                16,
            )}), so the update was stopped without answering. Check the package is complete.",
        )

    class UnexpectedFinish(
        phase: Phase,
    ) : Fm1UpdateError(
            if (phase ==
                Phase.STAGING
            ) {
                "The FM-1 asked to finish writing while it was still checking the package, so the update was stopped. Switch it off and on again. If it starts in its update loader, resume the update; otherwise install again."
            } else {
                "The update loader asked to finish checking the package while it was writing, so the update was stopped. Keep the FM-1 connected and switched on, and resume the update: its screen stays dark while it updates, and it may not start again until the update is finished."
            },
        )
}
