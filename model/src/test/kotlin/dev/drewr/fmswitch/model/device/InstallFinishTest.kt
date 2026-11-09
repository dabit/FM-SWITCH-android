package dev.drewr.fmswitch.model.device

import dev.drewr.fm1.updater.Fm1UpdateError
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InstallFinishTest {
    @Test fun looksAgainWhenTheFM1DidNotReturn() {
        assertTrue(FlashJob.mightHaveFinished(Fm1UpdateError.DeviceDidNotReturn, 1.0, reinstalling = false))
        assertTrue(FlashJob.mightHaveFinished(Fm1UpdateError.DeviceDidNotReturn, 1.0, reinstalling = true))
    }

    @Test fun looksAgainWhenTheWriteStoppedNearTheEnd() {
        val stalled = Fm1UpdateError.Stalled(Fm1UpdateError.Phase.WRITING, 900)
        val lost = Fm1UpdateError.Disconnected(Fm1UpdateError.Phase.WRITING, 900)
        assertTrue(FlashJob.mightHaveFinished(stalled, 0.97, reinstalling = false))
        assertTrue(FlashJob.mightHaveFinished(lost, 1.0, reinstalling = false))
    }

    @Test fun doesNotLookAgainPartWayThrough() {
        val lost = Fm1UpdateError.Disconnected(Fm1UpdateError.Phase.WRITING, 400)
        assertFalse(FlashJob.mightHaveFinished(lost, 0.45, reinstalling = false))
    }

    @Test fun doesNotLookAgainForAReinstallThatStopped() {
        val stalled = Fm1UpdateError.Stalled(Fm1UpdateError.Phase.WRITING, 900)
        assertFalse(FlashJob.mightHaveFinished(stalled, 0.99, reinstalling = true))
    }

    @Test fun doesNotLookAgainForOtherErrors() {
        val errors =
            listOf(
                Fm1UpdateError.Stalled(Fm1UpdateError.Phase.STAGING, 10),
                Fm1UpdateError.IdentityMismatch("FM-1_7010300", "FM-1_900"),
                Fm1UpdateError.LoaderNotFound,
                Fm1UpdateError.DeviceNotFound,
            )
        errors.forEach { error ->
            assertFalse(FlashJob.mightHaveFinished(error, 1.0, reinstalling = false))
        }
    }
}
