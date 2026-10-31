// Ported from FM-SWITCH App/Model/FirmwareFilter.swift.
package dev.drewr.fmswitch.model.catalogue

class FirmwareFilter(
    var search: String = "",
) {
    fun matches(entry: CatalogueEntry) =
        search.isEmpty() ||
            listOf(entry.name, entry.what.orEmpty(), entry.author.orEmpty())
                .any { it.contains(search, true) } || entry.kinds.any { it.contains(search, true) }

    fun matching(
        entries: List<CatalogueEntry>,
        showBeta: Boolean,
    ) = entries.filter {
        (showBeta || !it.beta) &&
            matches(it)
    }

    fun visible(
        entries: List<CatalogueEntry>,
        showBeta: Boolean,
        showLinksOnly: Boolean,
        saved: List<String>,
    ) = matching(entries, showBeta).filter { showLinksOnly || it.isDownloadable || it.id in saved }

    companion object {
        fun kinds(entries: List<CatalogueEntry>) =
            entries
                .flatMap { it.kinds.map(String::lowercase).toSet() }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedWith(
                    compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key },
                ).map { it.key }

        fun isKind(
            kind: String,
            entry: CatalogueEntry,
        ) = entry.kinds.any { it.equals(kind, true) }

        fun name(kind: String) =
            mapOf(
                "synth" to "Synths",
                "groove" to "Grooveboxes",
                "drums" to "Drums & samplers",
                "chords" to "Chords",
                "tracker" to "Trackers",
                "dev" to "For devs",
                "other" to "Other",
                "game" to "Games",
            )[kind.lowercase()] ?: kind.replaceFirstChar { it.uppercase() }

        fun scopes(entries: List<CatalogueEntry>): List<FirmwareScope> =
            listOf(FirmwareScope.All) + kinds(entries).map { FirmwareScope.Kind(it) } +
                listOfNotNull(FirmwareScope.BuiltFromSource.takeIf { entries.any { e -> e.isBuiltFromSource } })
    }
}

sealed class FirmwareScope {
    data object All : FirmwareScope()

    data class Kind(
        val kind: String,
    ) : FirmwareScope()

    data object BuiltFromSource : FirmwareScope()

    fun includes(entry: CatalogueEntry) =
        when (this) {
            All -> true
            is Kind -> FirmwareFilter.isKind(kind, entry)
            BuiltFromSource -> entry.isBuiltFromSource
        }
}

enum class FirmwareOrder {
    ByName,
    Updated,
    ;

    fun sorted(entries: List<CatalogueEntry>) =
        if (this == ByName) {
            entries
        } else {
            entries
                .withIndex()
                .sortedWith(
                    compareByDescending<IndexedValue<CatalogueEntry>> {
                        it.value.date.orEmpty()
                    }.thenBy { it.index },
                ).map { it.value }
        }
}
