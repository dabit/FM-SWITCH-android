// Ported from FM-SWITCH App/Model/SwitchingGuide.swift.
package dev.drewr.fmswitch.model.device

import dev.drewr.fm1.pkg.Fm1BootArea
import dev.drewr.fm1.pkg.FwscPackage
import dev.drewr.fm1.pkg.LeavingDestination
import dev.drewr.fm1.pkg.LeavingGuide
import dev.drewr.fm1.pkg.OfficialFirmware
import dev.drewr.fm1.pkg.UpdateCode
import dev.drewr.fm1.pkg.updateCode
import dev.drewr.fm1.protocol.Fm1Identity
import dev.drewr.fmswitch.model.catalogue.CatalogueEntry
import dev.drewr.fmswitch.model.library.FirmwareLibrary
import dev.drewr.fmswitch.model.library.StoredFirmware
import dev.drewr.fmswitch.model.settings.StartUp
import kotlinx.coroutines.CancellationException

object SwitchingGuide {
    fun running(
        identity: Fm1Identity?,
        saved: LeavingGuide?,
        startUp: StartUp?,
    ): LeavingGuide? {
        if (identity == null || identity.isLoader) return null
        if (saved?.identity == identity) return filling(saved, startUp)
        val known =
            when {
                identity == Fm1Identity.officialV15 -> "V15" to Fm1BootArea.V15
                identity == Fm1Identity.officialV16 -> "V16" to Fm1BootArea.V16
                identity.isOpenFirmware -> "M-VAVE's open firmware" to Fm1BootArea.OPEN_FIRMWARE
                else -> return null
            }
        return LeavingGuide(
            known.first,
            identity,
            UpdateCode.M_VAVE,
            if (startUp == StartUp.MBootloader) Fm1BootArea.M_BOOTLOADER else known.second,
        )
    }

    fun guide(
        installing: FwscPackage,
        named: String,
        over: LeavingGuide?,
        startUp: StartUp?,
    ): LeavingGuide? = LeavingGuide.from(installing, named, over?.bootArea ?: startUp?.bootArea, over?.updateCode)

    fun installing(
        packageFile: FwscPackage,
        named: String,
        over: LeavingGuide?,
        startUp: StartUp?,
    ): Pair<LeavingGuide?, StartUp?> {
        val guide = guide(packageFile, named, over, startUp)
        val previous = over?.bootArea ?: startUp?.bootArea
        val bootArea = guide?.bootArea
        return if (bootArea == null || bootArea == previous) {
            guide to null
        } else {
            guide to startUpOf(bootArea)
        }
    }

    fun filling(
        guide: LeavingGuide,
        startUp: StartUp?,
    ): LeavingGuide =
        if (guide.bootArea != null || startUp?.bootArea == null) {
            guide
        } else {
            guide.copy(bootArea = startUp.bootArea)
        }

    fun dependsOnBootArea(updateCode: UpdateCode) =
        updateCode == UpdateCode.FELUCCA_ORIGINAL_CHECK || updateCode == UpdateCode.RECOGNISES_V15

    fun reportsOfficialIdentity(guide: LeavingGuide) =
        guide.identity.isOfficial && guide.updateCode != UpdateCode.M_VAVE
}

fun destinationOf(
    packageFile: FwscPackage,
    official: OfficialFirmware? = OfficialFirmware.official(packageFile),
): LeavingDestination =
    when (official?.identity) {
        Fm1Identity.officialV15 -> {
            LeavingDestination.V15
        }

        Fm1Identity.officialV16 -> {
            LeavingDestination.V16
        }

        else -> {
            if (packageFile.identity.isOpenFirmware) {
                LeavingDestination.OPEN_FIRMWARE
            } else if (packageFile.isFirmware) {
                LeavingDestination.FELUCCA_PLATFORM
            } else {
                LeavingDestination.M_BOOTLOADER
            }
        }
    }

val StartUp.bootArea: Fm1BootArea?
    get() =
        when (this) {
            StartUp.MVave -> Fm1BootArea.V15
            StartUp.V16 -> Fm1BootArea.V16
            StartUp.MBootloader -> Fm1BootArea.M_BOOTLOADER
            StartUp.OpenFirmware -> Fm1BootArea.OPEN_FIRMWARE
            StartUp.NotSure -> null
        }

fun startUpOf(bootArea: Fm1BootArea) =
    when (bootArea) {
        Fm1BootArea.V15 -> StartUp.MVave
        Fm1BootArea.V16 -> StartUp.V16
        Fm1BootArea.M_BOOTLOADER -> StartUp.MBootloader
        Fm1BootArea.OPEN_FIRMWARE -> StartUp.OpenFirmware
    }

object FixedFirmware {
    const val MIDIMON = "midimon"
    val CATALOGUE_IDS = listOf(MIDIMON, "loom")

    sealed class Failure(
        message: String,
    ) : IllegalArgumentException(message) {
        data object NotListed : Failure("MIDIMON isn't in the firmware list right now, so it can't be downloaded.")

        data class NotFixed(
            val name: String,
        ) : Failure("The $name saved doesn't have the update check that installs any firmware.")
    }

    suspend fun find(
        library: FirmwareLibrary,
        entries: List<CatalogueEntry>,
        fetch: suspend (String) -> ByteArray,
    ): StoredFirmware {
        CATALOGUE_IDS.forEach { id ->
            library.items.filter { it.catalogueID == id }.forEach { item ->
                val packageFile =
                    try {
                        library.loadPackage(item)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Throwable) {
                        null
                    }
                if (packageFile?.updateCode == UpdateCode.FELUCCA_FIXED_CHECK) {
                    return item
                }
            }
        }
        val entry = entries.firstOrNull { it.id == MIDIMON } ?: throw Failure.NotListed
        val item = library.download(entry, fetch)
        if (library.loadPackage(item).updateCode != UpdateCode.FELUCCA_FIXED_CHECK) throw Failure.NotFixed(item.name)
        return item
    }
}
