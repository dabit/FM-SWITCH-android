// Ported from FM-SWITCH App/Model/DeviceSummary.swift.
package dev.drewr.fmswitch.model.device

import dev.drewr.fm1.protocol.Fm1Identity
import dev.drewr.fmswitch.model.catalogue.CatalogueEntry
import dev.drewr.fmswitch.model.history.FlashHistory

data class DeviceSummary(
    val state: State,
    val identity: String?,
) {
    sealed class State {
        data object NotConnected : State()

        data class Running(
            val name: String?,
        ) : State()

        data class Waiting(
            val identity: Fm1Identity,
        ) : State()
    }

    val statusLine: String
        get() =
            when (val current = state) {
                State.NotConnected -> {
                    "Not connected"
                }

                is State.Running -> {
                    current.name?.let { "Connected, running $it" }
                        ?: "Connected, reporting ${identity.orEmpty()}"
                }

                is State.Waiting -> {
                    "Connected, waiting to finish an install"
                }
            }

    val screenTitle: String
        get() =
            when (val current = state) {
                State.NotConnected -> {
                    "HELLO"
                }

                is State.Running -> {
                    current.name
                        ?.replace(Regex("the official firmware \\((V1[56])\\)"), "${'$'}1")
                        ?.uppercase()
                        ?: identity.orEmpty()
                }

                is State.Waiting -> {
                    "UPDATE"
                }
            }

    val screenLine: String
        get() =
            when (state) {
                State.NotConnected -> "Plug me in"
                is State.Running -> "Ready"
                is State.Waiting -> "Waiting…"
            }

    companion object {
        fun resolvedName(
            identity: Fm1Identity,
            lastInstall: Pair<String, String>? = null,
            catalogueMatches: List<Pair<String, String?>> = emptyList(),
        ): String? =
            lastInstall?.let { "${it.first} ${it.second}" }
                ?: if (identity == Fm1Identity.officialV15) {
                    "the official firmware (V15)"
                } else if (identity == Fm1Identity.officialV16) {
                    "the official firmware (V16)"
                } else if (catalogueMatches.size == 1) {
                    catalogueMatches.single().let { (name, version) -> listOfNotNull(name, version).joinToString(" ") }
                } else {
                    null
                }

        /** Names what the FM-1 runs from what this app installed last, or the firmware list. */
        fun from(
            identity: Fm1Identity?,
            history: FlashHistory,
            entries: List<CatalogueEntry>,
        ): DeviceSummary {
            if (identity == null || identity.isLoader) return from(identity)
            val name =
                resolvedName(
                    identity,
                    history.lastInstall(identity.text)?.let { it.name to it.version },
                    entries.filter { it.product == identity.text }.map { it.name to it.version },
                )
            return from(identity, name)
        }

        /**
         * Whether the FM-1 is running this entry, as far as anyone can tell: it reports this
         * firmware's identity, and either this app installed it or no other listed firmware
         * reports the same.
         */
        fun isOnFm1(
            entry: CatalogueEntry,
            identity: Fm1Identity?,
            history: FlashHistory,
            entries: List<CatalogueEntry>,
        ): Boolean {
            val text = identity?.text ?: return false
            if (text != entry.product) return false
            history.lastInstall(text)?.let { return it.name == entry.name }
            return entries.count { it.product == text } == 1
        }

        fun from(
            identity: Fm1Identity?,
            name: String? = null,
        ): DeviceSummary =
            when {
                identity == null -> DeviceSummary(State.NotConnected, null)
                identity.isLoader -> DeviceSummary(State.Waiting(identity), identity.text)
                else -> DeviceSummary(State.Running(name), identity.text)
            }
    }
}
