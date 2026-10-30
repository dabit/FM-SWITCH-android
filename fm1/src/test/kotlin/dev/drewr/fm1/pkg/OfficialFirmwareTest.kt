package dev.drewr.fm1.pkg

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OfficialFirmwareTest {
    @Test fun bundledV15IsTheOfficialFirmware() {
        val packageFile = FwscPackage(File(requireNotNull(System.getProperty("fm1.v15"))).readBytes())
        assertEquals(699_956, packageFile.fileBytes.size)
        assertEquals(OfficialFirmware.V15.sha256, packageFile.sha256)
        assertTrue(OfficialFirmware.V15.matches(packageFile))
        assertEquals("FM-1_015", packageFile.product)
        assertEquals(OfficialFirmware.STOCK_LOADER_SHA256, packageFile.loaderSha256)
    }
}
