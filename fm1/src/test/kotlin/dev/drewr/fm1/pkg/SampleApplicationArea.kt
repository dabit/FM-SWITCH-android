// Ported from FM1Kit 0.6.0 Tests/FM1RescueTests/SampleFlash.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.pkg

import dev.drewr.fm1.testing.SamplePackage

internal object SampleApplicationArea {
    fun packageWith(code: ByteArray): FwscPackage = FwscPackage(SamplePackage.firmwareWithCode(code))

    fun packageWithAppRecord(
        code: ByteArray,
        address: Int,
        length: Int,
    ): FwscPackage = FwscPackage(SamplePackage.firmwareWithAppRecord(code, address, length))
}
