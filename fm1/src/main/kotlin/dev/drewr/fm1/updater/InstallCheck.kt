// Ported from FM1Kit 0.6.0 Updater/InstallCheck.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.updater

import dev.drewr.fm1.pkg.FwscPackage
import dev.drewr.fm1.protocol.BuildId
import dev.drewr.fm1.protocol.Fm1Capability
import dev.drewr.fm1.protocol.Fm1Identity

enum class InstallKind { UPDATE, SWITCHING_FIRMWARE, REINSTALL, SAME_IDENTITY }

data class InstallCheck(
    val device: Fm1Identity,
    val capability: Fm1Capability?,
    val kind: InstallKind,
) {
    val canReinstallOfficial: Boolean get() = kind == InstallKind.REINSTALL && capability == null

    companion object {
        fun of(
            device: Fm1Identity,
            capability: Fm1Capability?,
            packageFile: FwscPackage,
            buildId: BuildId?,
        ): InstallCheck {
            val kind =
                when {
                    capability != null && buildId == capability.buildId -> InstallKind.REINSTALL

                    capability != null && packageFile.identity.isOpenFirmware &&
                        capability.family == Fm1Capability.FM1_FAMILY -> InstallKind.UPDATE

                    capability != null -> InstallKind.SWITCHING_FIRMWARE

                    device == packageFile.identity && device.isOfficial -> InstallKind.REINSTALL

                    device == packageFile.identity -> InstallKind.SAME_IDENTITY

                    else -> InstallKind.SWITCHING_FIRMWARE
                }
            return InstallCheck(device, capability, kind)
        }
    }
}

data class InstallOptions(
    val confirmsSwitchingFirmware: Boolean = false,
    val confirmsSameIdentity: Boolean = false,
    val buildId: BuildId? = null,
    val confirmsReinstallingOfficial: Boolean = false,
)
