package dev.drewr.fmswitch.model.device

import dev.drewr.fm1.protocol.Fm1Identity
import dev.drewr.fm1.updater.InstallKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BeforeYouStartStateTest {
    private fun state(
        switching: Boolean = false,
        same: Boolean = false,
        untested: Boolean = false,
        saved: Boolean = true,
        accepted: Boolean = true,
    ) = BeforeYouStartState(switching, same, untested, true, true, saved, accepted, "Felucca")

    @Test fun switchingNeedsTheSavedTick() {
        assertFalse(state(switching = true, saved = false).isTicked)
        assertTrue(state(switching = true).isTicked)
    }

    @Test fun untestedNeedsItsTick() {
        assertFalse(state(untested = true, accepted = false).isTicked)
        assertTrue(state(untested = true).isTicked)
    }

    @Test fun confirmationsFollowTheTicks() {
        val startable =
            InstallReadiness(emptyList(), InstallCheckState.Found(InstallKind.UPDATE), Fm1Identity.parse("FM-1_900"))
        val needsStartUp = startable.copy(refusesV15OverOtherBootloaders = true)
        assertTrue(state(switching = true).confirmsSwitchingFirmware)
        assertFalse(state(switching = true, saved = false).confirmsSwitchingFirmware)
        assertTrue(state(same = true).confirmsSameIdentity)
        assertFalse(state().confirmsSameIdentity)
        assertEquals("Install Felucca", state().buttonTitle(startable))
        assertEquals("Tick each one to install", state(switching = true, saved = false).buttonTitle(startable))
        assertTrue(needsStartUp.needsStartUp)
        assertEquals("Choose your FM-1's start-up", state().buttonTitle(needsStartUp))
    }

    @Test fun isEnabledNeedsTheTickAndAStartableReadiness() {
        val startable =
            InstallReadiness(emptyList(), InstallCheckState.Found(InstallKind.UPDATE), Fm1Identity.parse("FM-1_900"))
        assertFalse(state(switching = true, saved = false).isEnabled(startable))
        assertFalse(state().isEnabled(startable.copy(identity = null)))
        assertTrue(state().isEnabled(startable))
    }
}
