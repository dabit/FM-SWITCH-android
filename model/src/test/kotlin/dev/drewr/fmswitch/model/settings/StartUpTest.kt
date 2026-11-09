package dev.drewr.fmswitch.model.settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StartUpTest {
    @Test fun v16IsAStartUp() {
        assertEquals("Changed by V16", StartUp.V16.label)
        assertEquals("M-VAVE's V16 has been installed on it.", StartUp.V16.detail)
        assertEquals(
            listOf(
                StartUp.MVave,
                StartUp.V16,
                StartUp.MBootloader,
                StartUp.OpenFirmware,
                StartUp.NotSure,
            ),
            StartUp.entries.toList(),
        )
    }

    @Test fun theAnswerIsRemembered() {
        val s = InMemoryAppSettings()
        s.startUp = StartUp.MVave
        assertEquals(StartUp.MVave, s.startUp)
    }

    @Test fun onlyKnownFirmwaresAreFlagged() {
        assertTrue(FeluccaUpdateCode.isKnown("amb"))
        assertTrue(!FeluccaUpdateCode.isKnown("mvave-official"))
        assertTrue(!FeluccaUpdateCode.isKnown(null))
    }

    @Test fun theWarningNamesTheOthersAndHedgesWhenUnsure() {
        val sure = FeluccaUpdateCode.Warning("amb", "AMB-1", StartUp.MBootloader)
        assertTrue(sure.body.startsWith("Once AMB-1"))
        assertTrue(sure.body.contains("DUB-1"))
        val unsure = FeluccaUpdateCode.Warning("dub", "DUB-1", StartUp.NotSure)
        assertTrue(unsure.body.startsWith("If your FM-1's bootloader has been changed"))
    }
}
