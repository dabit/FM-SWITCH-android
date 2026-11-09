// Ported from FM1Kit 0.6.0 Package/FwscPackage.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.pkg

import dev.drewr.fm1.protocol.BuildId
import dev.drewr.fm1.protocol.Fm1Identity

data class OfficialFirmware(
    val name: String,
    val size: Int,
    val sha256: String,
    val buildId: BuildId,
    val identity: Fm1Identity,
    val isRedistributable: Boolean,
) {
    fun matches(packageFile: FwscPackage) = size == packageFile.fileBytes.size && sha256 == packageFile.sha256

    companion object {
        val V15 =
            OfficialFirmware(
                "FM-1 V15",
                699_956,
                "db1642b2b6fa5c2cccb11ffd13878068bb28601678d3644049f99dc40e7edb8a",
                BuildId.parse("db1642b2b6fa5c2cccb11ffd13878068bb28601678d3644049f99dc40e7edb8a")!!,
                Fm1Identity.officialV15,
                true,
            )
        val V16 =
            OfficialFirmware(
                "FM-1 V16",
                718_196,
                "d017fb65e70aca97ee29d944470c3515a74581df5c0576bf70de917c7b2b61d5",
                BuildId.parse("d017fb65e70aca97ee29d944470c3515a74581df5c0576bf70de917c7b2b61d5")!!,
                Fm1Identity.officialV16,
                false,
            )
        val OPEN_V12 =
            OfficialFirmware(
                "FM1 Open V12",
                475_700,
                "2dd4f2d2f020851f8797752371704a9a541aa747f4e20419d629de9aa2c66f43",
                BuildId.parse("426b7fa5cf1626bceaa05e8106b9a8b0eaad7f49d8b2c157303f15be347596aa")!!,
                Fm1Identity.of("FM-1", 8_000_012),
                false,
            )
        const val STOCK_LOADER_SHA256 = "ef1f2db89b011fa53e71df363f0122de0ea896618c53fb53e8da676322004541"
        val known = listOf(V15, V16, OPEN_V12)
        val official = listOf(V15, V16)

        fun matching(packageFile: FwscPackage): OfficialFirmware? = known.firstOrNull { it.matches(packageFile) }

        fun official(packageFile: FwscPackage): OfficialFirmware? = matching(packageFile)?.takeIf { it in official }
    }
}
