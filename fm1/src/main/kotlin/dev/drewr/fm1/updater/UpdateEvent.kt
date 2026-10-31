// Ported from FM1Kit 0.4.5 Updater/UpdateEvent.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.updater

import dev.drewr.fm1.protocol.Fm1Identity

sealed class UpdateEvent {
    data class Connected(
        val identity: Fm1Identity,
    ) : UpdateEvent()

    data class Staging(
        val requests: Int,
    ) : UpdateEvent()

    data object WaitingForLoader : UpdateEvent()

    data class LoaderFound(
        val identity: Fm1Identity,
    ) : UpdateEvent()

    data class Writing(
        val fraction: Double,
        val requests: Int,
    ) : UpdateEvent()

    data class Checking(
        val fraction: Double,
        val requests: Int,
    ) : UpdateEvent()

    data object Rebooting : UpdateEvent()

    data class Finished(
        val identity: Fm1Identity,
    ) : UpdateEvent()
}
