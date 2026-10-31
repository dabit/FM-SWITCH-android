// Ported from FM-SWITCH App/Model/DeviceSummary.swift.
package dev.drewr.fmswitch.model.device

import dev.drewr.fm1.protocol.Fm1Identity

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
                    current.name?.replace("the official firmware (V15)", "V15")?.uppercase()
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
                } else if (catalogueMatches.size == 1) {
                    catalogueMatches.single().let { (name, version) -> listOfNotNull(name, version).joinToString(" ") }
                } else {
                    null
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
