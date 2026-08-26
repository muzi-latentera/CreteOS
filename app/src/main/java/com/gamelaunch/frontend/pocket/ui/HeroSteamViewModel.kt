package com.gamelaunch.frontend.pocket.ui

import androidx.lifecycle.ViewModel
import com.gamelaunch.frontend.pocket.data.SteamMetadataDao
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Minimal ViewModel that exposes [SteamMetadataDao] to the home screen hero tile.
 * Needed because composables can't directly receive Hilt-injected DAOs.
 */
@HiltViewModel
class HeroSteamViewModel @Inject constructor(
    val steamMetadataDao: SteamMetadataDao
) : ViewModel()
