package com.gamelaunch.frontend.domain.usecase

import com.gamelaunch.frontend.domain.model.EmulatorUpdate

/** Stable identity for one batch of emulator releases, used to persist banner dismissal. */
object EmulatorUpdateNoticePolicy {
    fun signature(updates: List<EmulatorUpdate>): String =
        updates.sortedBy { it.packageName }
            .joinToString(",") { "${it.packageName}:${it.latestVersion}" }

    fun shouldShow(
        notificationsEnabled: Boolean,
        updates: List<EmulatorUpdate>,
        dismissedSignature: String?,
    ): Boolean = notificationsEnabled &&
        updates.isNotEmpty() &&
        signature(updates) != dismissedSignature
}
