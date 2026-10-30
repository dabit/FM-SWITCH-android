package dev.drewr.fmswitch

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class LicenceAssetsTest {
    @Test
    fun fm1KitNoticeNamesItsAuthorAndLicense() {
        val notice = File("src/main/assets/licences/FM1Kit-LICENSE.txt").readText()

        assertTrue(notice.contains("Drew Robinson"))
        assertTrue(notice.contains("MIT"))
    }

    @Test
    fun mFlasherNoticeNamesItsAuthor() {
        val notice = File("src/main/assets/licences/M-Flasher-LICENSE.txt").readText()

        assertTrue(notice.contains("Vitkauskas"))
    }

    @Test
    fun v15LicenseAssetExists() {
        assertTrue(File("src/main/assets/firmware/FM-1_015-LICENCE.txt").isFile)
    }
}
