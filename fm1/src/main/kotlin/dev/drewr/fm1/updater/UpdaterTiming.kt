// Ported from FM1Kit 0.6.0 Updater/UpdaterTiming.swift — MIT, Copyright (c) 2026 Drew Robinson; see fm1/LICENSE-FM1Kit.txt.
package dev.drewr.fm1.updater

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

data class UpdaterTiming(
    val afterUpgradeCommand: Duration = Duration.ZERO,
    val stagingIdleLimit: Duration = 8.seconds,
    val loaderBoot: Duration = 3.seconds,
    val loaderSearch: Duration = 30.seconds,
    val writeIdleLimit: Duration = 60.seconds,
    val firmwareBoot: Duration = 3.seconds,
    val returnSearch: Duration = 40.seconds,
    val responseDelay: Duration = Duration.ZERO,
    val finishSettle: Duration = 600.milliseconds,
    val scanInterval: Duration = 1.seconds,
    val handshakeWait: Duration = 1.seconds,
    val commandReplyWait: Duration = 1500.milliseconds,
    val voiceReplyWait: Duration = 10.seconds,
    val libraryReplyWait: Duration = 1.seconds,
    val handshakeAttempts: Int = 2,
    val fullIdentityWait: Duration = 300.milliseconds,
    val returnCheckAfter: Duration = 5.seconds,
) {
    companion object {
        val standard = UpdaterTiming()
    }
}
