// Ported from FM-SWITCH App/Catalogue/FirmwareCatalogue.swift.
package dev.drewr.fmswitch.model.catalogue

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant

object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())

    override fun serialize(
        encoder: Encoder,
        value: Instant,
    ) = encoder.encodeString(value.toString())
}

@Serializable
data class MergedCatalogue(
    val formatVersion: Int? = null,
    @Serializable(with = InstantSerializer::class) val builtAt: Instant,
    val hubUpdated: String? = null,
    val entries: List<CatalogueEntry>,
    val problems: List<String> = emptyList(),
) {
    val isReadable get() = (formatVersion ?: CURRENT_FORMAT) <= CURRENT_FORMAT

    companion object {
        const val CURRENT_FORMAT = 1
    }
}

@Serializable
data class CatalogueEntry(
    val id: String,
    val name: String,
    val author: String? = null,
    val kinds: List<String> = emptyList(),
    val what: String? = null,
    val whatSource: DescriptionSource? = null,
    val version: String? = null,
    val date: String? = null,
    val beta: Boolean = false,
    val packageURL: String? = null,
    val packageOrigin: PackageOrigin? = null,
    val product: String? = null,
    val size: Int? = null,
    val sha256: String? = null,
    val loader: String? = null,
    val links: List<CatalogueLink> = emptyList(),
    val repo: String? = null,
    val listing: Int = 0,
    val isOfficial: Boolean = false,
    val builds: List<Build>? = null,
    val build: String? = null,
    val releaseURL: String? = null,
    val otherRelease: OtherRelease? = null,
    val stars: Int? = null,
    val licence: String? = null,
    val sourceBuild: SourceBuild? = null,
) {
    val requiresOfficialFirmware get() = loader.equals("mvave", ignoreCase = true)
    val isDownloadable get() = packageURL != null
    val hasPublishedChecksum get() = sha256 != null
    val needsBuildChoice get() = (builds?.size ?: 0) > 1 && build == null
    val isHardwareTested get() = isOfficial || listing and (Listing.HUB or Listing.EDITOR) != 0
    val isBuiltFromSource get() = sourceBuild?.sha256?.equals(sha256, true) == true

    fun choosingBuild(name: String): CatalogueEntry =
        builds?.firstOrNull { it.name == name }?.let { selected ->
            copy(
                product = if (selected.sha256.equals(sha256, true)) product else null,
                packageURL = selected.url,
                packageOrigin = PackageOrigin.GithubRelease,
                size = selected.size,
                sha256 = selected.sha256?.lowercase(),
                build = name,
            )
        } ?: this

    companion object {
        const val ADDED_FROM_FILE_PREFIX = "file-"

        fun addedFrom(item: dev.drewr.fmswitch.model.library.StoredFirmware) =
            CatalogueEntry(
                id = ADDED_FROM_FILE_PREFIX + item.sha256,
                name = item.name,
                author = item.author,
                version = item.version,
                product = item.product,
                size = item.size,
                sha256 = item.sha256,
            )
    }
}

val CatalogueEntry.isFromFile get() = id.startsWith(CatalogueEntry.ADDED_FROM_FILE_PREFIX)
val CatalogueEntry.listedOn get() =
    if (isFromFile) {
        "None: added from a file"
    } else {
        listOfNotNull(
            "Firmware hub".takeIf { listing and Listing.HUB != 0 },
            "fm1-editor.com".takeIf { listing and Listing.EDITOR != 0 },
            "FM1 Resource Hub".takeIf {
                listing and
                    Listing.RESOURCE_HUB !=
                    0
            },
        ).joinToString(" + ")
    }
val CatalogueEntry.sourceBuildSummary get() =
    sourceBuild
        ?.takeIf {
            isBuiltFromSource
        }?.let { "Yes, by GitHub Actions from ${it.repo} at commit ${it.shortCommit}, signed" }
        ?: "Not known"
val CatalogueEntry.packageSource get() =
    if (isFromFile) {
        "A file you added"
    } else {
        when (packageOrigin) {
            PackageOrigin.Hub -> "Firmware hub mirror"
            PackageOrigin.GithubRelease -> "GitHub release"
            PackageOrigin.PublisherManifest -> "The publisher's site"
            PackageOrigin.ProjectPage -> "The project's page"
            PackageOrigin.Mvave -> "M-VAVE's downloads"
            null -> "None found"
        }
    }

object Listing {
    const val HUB = 1
    const val EDITOR = 2
    const val RESOURCE_HUB = 4
}

@Serializable enum class PackageOrigin {
    @SerialName("hub")
    Hub,

    @SerialName("githubRelease")
    GithubRelease,

    @SerialName("publisherManifest")
    PublisherManifest,

    @SerialName("projectPage")
    ProjectPage,

    @SerialName("mvave")
    Mvave,
}

@Serializable enum class DescriptionSource {
    @SerialName("gitHub")
    GitHub,
}

@Serializable data class CatalogueLink(
    val label: String,
    val url: String,
)

@Serializable data class Build(
    val name: String,
    val url: String,
    val size: Int? = null,
    val sha256: String? = null,
)

@Serializable data class SourceBuild(
    val sha256: String,
    val repo: String,
    val commit: String,
    val run: String? = null,
) {
    val shortCommit get() = commit.take(7)
}

@Serializable data class OtherRelease(
    val version: String? = null,
    val date: String? = null,
    val beta: Boolean = false,
    val packageURL: String? = null,
    val packageOrigin: PackageOrigin? = null,
    val product: String? = null,
    val size: Int? = null,
    val sha256: String? = null,
    val builds: List<Build>? = null,
    val build: String? = null,
    val releaseURL: String? = null,
)
