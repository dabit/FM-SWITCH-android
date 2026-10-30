// Ported from FM1Kit 0.4.5 Updater/FM1Updater.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.transport

object PortMatching {
    private val probeableNameFragments =
        listOf(
            "fm-1",
            "fm1",
            "felucca",
            "ota",
            "composite",
            "usb-midi",
            "simulated",
            "sloop",
            "clipper",
            "sinco",
        )

    fun isProbeable(port: PortDescriptor): Boolean =
        (port.usbVendorId == NORMAL_VENDOR_ID && port.usbProductId == NORMAL_PRODUCT_ID) ||
            (port.usbVendorId == LOADER_VENDOR_ID && port.usbProductId == LOADER_PRODUCT_ID) ||
            port.names.any { name ->
                val lowercased = name.lowercase()
                probeableNameFragments.any(lowercased::contains)
            }

    private const val NORMAL_VENDOR_ID = 0x4C4A
    private const val NORMAL_PRODUCT_ID = 0xC755
    private const val LOADER_VENDOR_ID = 0x4D4A
    private const val LOADER_PRODUCT_ID = 0x4155
}
