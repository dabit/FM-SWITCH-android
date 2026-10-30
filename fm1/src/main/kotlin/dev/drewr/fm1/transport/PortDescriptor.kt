// Ported from FM1Kit 0.4.5 Transport/MIDITransport.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.transport

data class PortDescriptor(
    val id: String,
    val names: List<String>,
    val serialNumber: String? = null,
    val usbVendorId: Int? = null,
    val usbProductId: Int? = null,
)
