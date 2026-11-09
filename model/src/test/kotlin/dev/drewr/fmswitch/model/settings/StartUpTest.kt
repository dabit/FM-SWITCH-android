package dev.drewr.fmswitch.model.settings
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
