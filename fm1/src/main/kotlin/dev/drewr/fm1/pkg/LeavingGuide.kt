// Ported from FM1Kit 0.6.0 Package/LeavingGuide.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.pkg

import dev.drewr.fm1.protocol.Fm1Identity

enum class UpdateCode(
    val raw: String,
) {
    M_VAVE("mVave"),
    FELUCCA_ORIGINAL_CHECK("feluccaOriginalCheck"),
    FELUCCA_FIXED_CHECK("feluccaFixedCheck"),
    RECOGNISES_V15("recognisesV15"),
    OWN_LOADER("ownLoader"),
    UNKNOWN("unknown"),
}

enum class Fm1BootArea(
    val raw: String,
) {
    V15("v15"),
    V16("v16"),
    OPEN_FIRMWARE("openFirmware"),
    M_BOOTLOADER("mBootloader"),
}

enum class LeavingDestination {
    V15,
    V16,
    OPEN_FIRMWARE,
    FELUCCA_PLATFORM,
    M_BOOTLOADER,
    ;

    internal val displayName: String
        get() =
            when (this) {
                V15 -> "V15"
                V16 -> "V16"
                OPEN_FIRMWARE -> "M-VAVE's open firmware"
                FELUCCA_PLATFORM -> "another custom firmware"
                M_BOOTLOADER -> "m-bootloader"
            }

    internal val bootArea: Fm1BootArea?
        get() =
            when (this) {
                V15 -> Fm1BootArea.V15

                V16 -> Fm1BootArea.V16

                OPEN_FIRMWARE -> Fm1BootArea.OPEN_FIRMWARE

                FELUCCA_PLATFORM,
                M_BOOTLOADER,
                -> null
            }
}

enum class LeavingRoute {
    DIRECT,
    THROUGH_FIXED_FIRMWARE,
    UNCERTAIN,
}

data class LeavingGuide(
    val firmwareName: String,
    val identity: Fm1Identity,
    val updateCode: UpdateCode,
    val bootArea: Fm1BootArea?,
) {
    fun route(to: LeavingDestination): LeavingRoute =
        when (updateCode) {
            UpdateCode.M_VAVE -> {
                if (to == LeavingDestination.M_BOOTLOADER && identity != Fm1Identity.officialV15) {
                    LeavingRoute.UNCERTAIN
                } else {
                    LeavingRoute.DIRECT
                }
            }

            UpdateCode.FELUCCA_FIXED_CHECK -> {
                LeavingRoute.DIRECT
            }

            UpdateCode.FELUCCA_ORIGINAL_CHECK -> {
                routeByBootArea(to)
            }

            UpdateCode.RECOGNISES_V15 -> {
                if (to == LeavingDestination.V15) LeavingRoute.DIRECT else routeByBootArea(to)
            }

            UpdateCode.OWN_LOADER -> {
                when (to) {
                    LeavingDestination.V15,
                    LeavingDestination.FELUCCA_PLATFORM,
                    -> LeavingRoute.DIRECT

                    LeavingDestination.V16,
                    LeavingDestination.OPEN_FIRMWARE,
                    -> LeavingRoute.THROUGH_FIXED_FIRMWARE

                    LeavingDestination.M_BOOTLOADER -> LeavingRoute.UNCERTAIN
                }
            }

            UpdateCode.UNKNOWN -> {
                LeavingRoute.UNCERTAIN
            }
        }

    val warnings: List<String>
        get() =
            buildList {
                val refused =
                    listOf(LeavingDestination.V15, LeavingDestination.V16)
                        .filter { route(it) == LeavingRoute.THROUGH_FIXED_FIRMWARE }
                if (refused.isNotEmpty()) {
                    val names = refused.joinToString(" or ") { it.displayName }
                    add(
                        "Once $firmwareName is installed, it can't install $names straight away. Going back takes " +
                            "two installs: $FIXED_FIRMWARES first, then $names. On a Mac, download mode " +
                            "can do it in one.",
                    )
                }
                if (reportsOfficialIdentity) {
                    add(
                        "$firmwareName reports itself as V15, so apps can't tell them apart. When switching away, " +
                            "say the FM-1 isn't really running V15.",
                    )
                }
            }

    fun instructions(to: LeavingDestination): List<String> {
        val name = to.displayName
        val steps =
            when (route(to)) {
                LeavingRoute.DIRECT -> {
                    mutableListOf("$firmwareName can install $name directly: install it as usual.")
                }

                LeavingRoute.THROUGH_FIXED_FIRMWARE -> {
                    mutableListOf(
                        "$firmwareName can't install $name directly${refusalReason(to)}.",
                        "Install $FIXED_FIRMWARES first: $firmwareName can install either.",
                        "Then install $name from that.",
                    )
                }

                LeavingRoute.UNCERTAIN -> {
                    mutableListOf(
                        "Try installing $name. It isn't known yet whether $firmwareName takes it.",
                        "If it's refused, nothing changes: install $FIXED_FIRMWARES first, then $name from that.",
                    )
                }
            }
        if (reportsOfficialIdentity && to == LeavingDestination.V15) {
            steps += "$firmwareName reports itself as V15: confirm it isn't really V15 when asked."
        }
        if (updateCode == UpdateCode.OWN_LOADER && to.bootArea != null && route(to) == LeavingRoute.DIRECT) {
            steps +=
                "$firmwareName installs with its own loader, so the FM-1 keeps the bootloader it has, " +
                "rather than getting $name's."
        }
        steps += "On a Mac, installing $name in download mode works whatever firmware is running."
        return steps
    }

    private val reportsOfficialIdentity: Boolean get() = identity.isOfficial && updateCode != UpdateCode.M_VAVE

    private fun routeByBootArea(to: LeavingDestination): LeavingRoute =
        when (to) {
            LeavingDestination.FELUCCA_PLATFORM -> {
                LeavingRoute.DIRECT
            }

            LeavingDestination.M_BOOTLOADER -> {
                LeavingRoute.THROUGH_FIXED_FIRMWARE
            }

            LeavingDestination.V15,
            LeavingDestination.V16,
            LeavingDestination.OPEN_FIRMWARE,
            -> {
                if (bootArea != to.bootArea) {
                    if (bootArea == null) LeavingRoute.UNCERTAIN else LeavingRoute.THROUGH_FIXED_FIRMWARE
                } else if (to == LeavingDestination.V15) {
                    LeavingRoute.DIRECT
                } else {
                    LeavingRoute.UNCERTAIN
                }
            }
        }

    private fun refusalReason(to: LeavingDestination): String {
        if (to == LeavingDestination.M_BOOTLOADER) return ": its update check only takes firmwares"
        if (updateCode == UpdateCode.OWN_LOADER) return ": it only takes firmware images the size of V15's"
        if (to != LeavingDestination.V15 || bootArea == null || bootArea == to.bootArea) return ""
        val current =
            when (bootArea) {
                Fm1BootArea.V15 -> "V15's bootloader"
                Fm1BootArea.V16 -> "V16's bootloader"
                Fm1BootArea.OPEN_FIRMWARE -> "M-VAVE's open firmware's bootloader"
                Fm1BootArea.M_BOOTLOADER -> "m-bootloader"
            }
        return ": its update check refuses it while the FM-1 has $current"
    }

    companion object {
        const val FIXED_FIRMWARES = "MIDIMON 0.4 or LOOM-8"

        fun from(
            packageFile: FwscPackage,
            name: String? = null,
            bootArea: Fm1BootArea?,
            installedBy: UpdateCode? = null,
        ): LeavingGuide? {
            val updateCode = packageFile.updateCode ?: return null
            val writesBootArea =
                packageFile.loaderSha256 == OfficialFirmware.STOCK_LOADER_SHA256 &&
                    installedBy != UpdateCode.OWN_LOADER
            return LeavingGuide(
                name ?: packageFile.identity.text,
                packageFile.identity,
                updateCode,
                if (writesBootArea) packageFile.bootAreaBrought else bootArea,
            )
        }
    }
}

val FwscPackage.updateCode: UpdateCode?
    get() {
        if (!isFirmware) return null
        val code = firmwareCode
        if (code == null || !code.containsSequence(FELUCCA_LOADER_MARKER)) {
            return if (identity.isOfficial || identity.isOpenFirmware) UpdateCode.M_VAVE else UpdateCode.UNKNOWN
        }
        if (code.containsSequence(BOOTLOADER_CHECK_MARKER)) return UpdateCode.FELUCCA_FIXED_CHECK
        if (code.containsSequence(OWN_LOADER_MARKER)) return UpdateCode.OWN_LOADER
        return if (code.containsSequence(V15_FLASH_IMAGE_SHA256)) {
            UpdateCode.RECOGNISES_V15
        } else {
            UpdateCode.FELUCCA_ORIGINAL_CHECK
        }
    }

internal val FwscPackage.bootAreaBrought: Fm1BootArea?
    get() =
        when {
            identity == Fm1Identity.officialV15 -> Fm1BootArea.V15
            identity == Fm1Identity.officialV16 -> Fm1BootArea.V16
            identity.isOpenFirmware -> Fm1BootArea.OPEN_FIRMWARE
            else -> null
        }
