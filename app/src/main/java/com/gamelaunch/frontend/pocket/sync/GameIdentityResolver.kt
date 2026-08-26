package com.gamelaunch.frontend.pocket.sync

import android.util.Log
import com.gamelaunch.frontend.domain.repository.GameRepository
import com.gamelaunch.frontend.pocket.data.db.entity.ManualGameLinkEntity
import com.gamelaunch.frontend.pocket.data.repository.LaunchTargetRepository
import com.gamelaunch.frontend.pocket.domain.DiscoveredProviderGame
import com.gamelaunch.frontend.pocket.providers.ProviderId
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves which eOr host game a discovered provider game corresponds to.
 *
 * Priority order (false negatives preferred over false positives):
 *
 * 1. Exact store ID match — romPath derivation from source field FIRST.
 *    Source wins over provider identity:
 *      source=STEAM  → steam:<id>
 *      source=GOG    → steam:GOG:<id>
 *      source=EPIC   → steam:EPIC:<id>
 *      source=AMAZON → steam:AMAZON:<id>
 *    A GameNative GOG game must NOT become steam:<id> merely because
 *    GameNative is in STEAM_PROVIDERS.
 *
 * 2. Previously stored manual link (user-confirmed)
 *
 * 3. Conservative normalised-title match
 *    Exact normalised match only. Only used when exactly ONE eOr game matches.
 *    Never when multiple candidates exist (prefer false negative).
 *
 * 4. Unresolved — returns null. Caller should offer manual linking UI.
 */
@Singleton
class GameIdentityResolver @Inject constructor(
    private val gameRepository: GameRepository,
    private val launchTargetRepository: LaunchTargetRepository
) {

    data class ResolvedIdentity(
        val hostGameKey: String,
        val hostGameId: Long,
        val confidence: Confidence
    )

    enum class Confidence {
        EXACT_STORE_ID,
        MANUAL_LINK,
        TITLE_MATCH,
        UNRESOLVED
    }

    /**
     * Attempt to resolve the eOr host game for [discovered].
     * Returns null when resolution is ambiguous or impossible.
     */
    suspend fun resolve(discovered: DiscoveredProviderGame): ResolvedIdentity? {

        // 1. Exact store ID — SOURCE determines the key format, not the provider
        val storeKey = buildStoreKey(discovered.externalId, discovered.source)
        if (storeKey != null) {
            val game = gameRepository.getGameByRomPath(storeKey)
            if (game != null) {
                Log.d(TAG, "Resolved '${discovered.displayName}' via store ID ($storeKey) → ${game.id}")
                return ResolvedIdentity(storeKey, game.id, Confidence.EXACT_STORE_ID)
            }
        }

        // 2. Manual link
        val manualLink = launchTargetRepository.findManualLink(
            discovered.provider,
            discovered.externalId
        )
        if (manualLink != null) {
            val game = gameRepository.getGameByRomPath(manualLink.hostGameKey)
            if (game != null) {
                Log.d(TAG, "Resolved '${discovered.displayName}' via manual link → ${game.id}")
                return ResolvedIdentity(manualLink.hostGameKey, game.id, Confidence.MANUAL_LINK)
            }
        }

        // 3. Conservative normalised title match
        val normalised = normaliseTitle(discovered.displayName)
        if (normalised.length >= MIN_TITLE_LENGTH) {
            val candidates = gameRepository.getAllGames()
                .first()  // collect once — we don't want to stay subscribed here
                .filter { normaliseTitle(it.title) == normalised }

            when (candidates.size) {
                1 -> {
                    val game = candidates.first()
                    Log.d(TAG, "Resolved '${discovered.displayName}' via title match → ${game.id}")
                    return ResolvedIdentity(game.romPath, game.id, Confidence.TITLE_MATCH)
                }
                0 -> { /* no match */ }
                else -> {
                    Log.d(TAG, "Title '${discovered.displayName}' is ambiguous (${candidates.size} candidates) — not resolving")
                }
            }
        }

        // 4. Unresolved
        Log.d(TAG, "Unresolved: '${discovered.displayName}' ${discovered.provider}/${discovered.externalId}")
        return null
    }

    /**
     * Record a user-confirmed manual link.
     */
    suspend fun recordManualLink(
        provider: ProviderId,
        providerExternalId: String,
        hostGameKey: String
    ) {
        launchTargetRepository.addManualLink(
            ManualGameLinkEntity(
                hostGameKey        = hostGameKey,
                providerName       = provider.name,
                providerExternalId = providerExternalId
            )
        )
        Log.i(TAG, "Manual link: ${provider.name}/$providerExternalId → $hostGameKey")
    }

    /**
     * Build the eOr romPath key from external ID + source.
     * SOURCE determines the format — provider identity is irrelevant here.
     *
     *   STEAM        → steam:<id>          (no source prefix for Steam, matches eOr's format)
     *   GOG          → steam:GOG:<id>
     *   EPIC         → steam:EPIC:<id>
     *   AMAZON       → steam:AMAZON:<id>
     *   CUSTOM_GAME  → steam:CUSTOM_GAME:<id>
     *
     * Returns null for non-integer IDs or unrecognised sources.
     */
    fun buildStoreKey(externalId: String, source: String): String? {
        val id = externalId.toIntOrNull() ?: return null
        return when (source.uppercase()) {
            "STEAM"       -> "steam:$id"
            "GOG"         -> "steam:GOG:$id"
            "EPIC"        -> "steam:EPIC:$id"
            "AMAZON"      -> "steam:AMAZON:$id"
            "CUSTOM_GAME" -> "steam:CUSTOM_GAME:$id"
            else          -> null
        }
    }

    private fun normaliseTitle(title: String): String =
        title.lowercase()
            .replace(Regex("[^a-z0-9 ]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    companion object {
        private const val TAG = "GameIdentityResolver"
        private const val MIN_TITLE_LENGTH = 4
    }
}
