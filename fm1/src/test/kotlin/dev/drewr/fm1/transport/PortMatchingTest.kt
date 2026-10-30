package dev.drewr.fm1.transport

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PortMatchingTest {
    @Test fun probesOnlyMatchingPorts() {
        listOf("Felucca Update", "FM-1", "USB-MIDI", "M-VAVE SINCO").forEach { name ->
            assertTrue(PortMatching.isProbeable(PortDescriptor(name, listOf(name))), name)
        }
        listOf("Arturia KeyStep", "IAC Bus").forEach { name ->
            assertFalse(PortMatching.isProbeable(PortDescriptor(name, listOf(name))), name)
        }
    }

    @Test fun probesByUsbIdWhateverTheName() {
        assertTrue(
            PortMatching.isProbeable(
                PortDescriptor("ordinary", listOf("USB Audio"), usbVendorId = 0x4C4A, usbProductId = 0xC755),
            ),
        )
    }
}
