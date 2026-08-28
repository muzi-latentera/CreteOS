package com.gamelaunch.frontend.pocket.data.repository

import com.gamelaunch.frontend.pocket.data.db.dao.GameLaunchPreferenceDao
import com.gamelaunch.frontend.pocket.data.db.dao.LaunchTargetDao
import com.gamelaunch.frontend.pocket.data.db.dao.ManualGameLinkDao
import com.gamelaunch.frontend.pocket.data.db.entity.GameLaunchPreferenceEntity
import com.gamelaunch.frontend.pocket.data.db.entity.LaunchTargetEntity
import com.gamelaunch.frontend.pocket.data.db.entity.ManualGameLinkEntity
import com.gamelaunch.frontend.pocket.domain.LaunchTarget
import com.gamelaunch.frontend.pocket.providers.ProviderId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LaunchTargetRepository @Inject constructor(
    private val launchTargetDao: LaunchTargetDao,
    private val preferenceDao: GameLaunchPreferenceDao,
    private val manualLinkDao: ManualGameLinkDao
) {

    fun getTargetsForGame(hostGameKey: String): Flow<List<LaunchTarget>> =
        launchTargetDao.getTargetsForGame(hostGameKey).map { list -> list.map { it.toDomain() } }

    suspend fun getTargetsForGameOnce(hostGameKey: String): List<LaunchTarget> =
        launchTargetDao.getTargetsForGameOnce(hostGameKey).map { it.toDomain() }

    suspend fun getPreferredTarget(hostGameKey: String): LaunchTarget? =
        launchTargetDao.getPreferredTarget(hostGameKey)?.toDomain()

    suspend fun getTargetsForProvider(provider: ProviderId): List<LaunchTarget> =
        launchTargetDao.getTargetsForProvider(provider.name).map { it.toDomain() }

    suspend fun upsertTarget(target: LaunchTarget): Long =
        launchTargetDao.upsert(target.toEntity())

    suspend fun upsertTargets(targets: List<LaunchTarget>) =
        launchTargetDao.upsertAll(targets.map { it.toEntity() })

    suspend fun setPreferredTarget(hostGameKey: String, targetId: Long) {
        launchTargetDao.clearPreferred(hostGameKey)
        launchTargetDao.setPreferred(targetId)
        preferenceDao.upsert(
            GameLaunchPreferenceEntity(hostGameKey = hostGameKey, preferredTargetId = targetId)
        )
    }

    suspend fun markProviderUnavailable(provider: ProviderId) =
        launchTargetDao.setProviderAvailability(provider.name, false)

    suspend fun markProviderAvailable(provider: ProviderId) =
        launchTargetDao.setProviderAvailability(provider.name, true)

    suspend fun deleteTarget(id: Long) = launchTargetDao.delete(id)

    /** Keep only the launch targets that are valid and installed for this ROM's system. */
    suspend fun retainOnlyTargets(
        hostGameKey: String,
        provider: ProviderId,
        allowedExternalIds: List<String>
    ): Int {
        val removed = launchTargetDao.deleteTargetsExcept(
            hostGameKey,
            provider.name,
            allowedExternalIds
        )
        if (removed > 0) preferenceDao.delete(hostGameKey)
        return removed
    }

    suspend fun countAvailableForProvider(provider: ProviderId): Int =
        launchTargetDao.countAvailableForProvider(provider.name)

    suspend fun addManualLink(link: ManualGameLinkEntity): Long =
        manualLinkDao.insert(link)

    suspend fun findManualLink(provider: ProviderId, externalId: String): ManualGameLinkEntity? =
        manualLinkDao.findLink(provider.name, externalId)

    // ---- mapping helpers ----

    private fun LaunchTargetEntity.toDomain() = LaunchTarget(
        id = id,
        hostGameKey = hostGameKey,
        provider = runCatching { ProviderId.valueOf(provider) }.getOrDefault(ProviderId.GAME_NATIVE),
        externalId = externalId,
        source = source,
        displayName = displayName,
        launchData = launchData,
        isAvailable = isAvailable,
        isPreferred = isPreferred,
        lastSeen = lastSeen,
        createdAt = createdAt
    )

    private fun LaunchTarget.toEntity() = LaunchTargetEntity(
        id = id,
        hostGameKey = hostGameKey,
        provider = provider.name,
        externalId = externalId,
        source = source,
        displayName = displayName,
        launchData = launchData,
        isAvailable = isAvailable,
        isPreferred = isPreferred,
        lastSeen = lastSeen,
        createdAt = createdAt
    )
}
