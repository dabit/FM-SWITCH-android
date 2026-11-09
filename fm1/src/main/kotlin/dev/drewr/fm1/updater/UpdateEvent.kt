// Ported from FM1Kit 0.6.0 Updater/UpdateEvent.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
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

    /**
     * The loader is reading the firmware: it has read [fraction] (0 to 1) of the firmware's
     * application area in [requests] reads.
     *
     * M-VAVE's loaders write in this pass. Felucca's only check the package in it, and write in
     * a second pass ([Checking]). Every loader seen reads the whole area, but one that skipped
     * part of it would stop short of 1.
     */
    data class Writing(
        val fraction: Double,
        val requests: Int,
    ) : UpdateEvent()

    /**
     * The loader is reading the firmware a second time: it has re-read [fraction] (0 to 1) of it
     * in [requests] reads. Not every loader does this.
     *
     * Felucca's loader erases and writes the flash in this pass, so it's no safer to interrupt
     * than the first: keep the FM-1 connected and switched on until the update finishes, and its
     * screen stays dark meanwhile.
     */
    data class Checking(
        val fraction: Double,
        val requests: Int,
    ) : UpdateEvent()

    data object Rebooting : UpdateEvent()

    data class Finished(
        val identity: Fm1Identity,
    ) : UpdateEvent()
}
