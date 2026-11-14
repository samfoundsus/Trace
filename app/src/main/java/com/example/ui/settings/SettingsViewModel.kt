package com.example.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.ProjectRepository
import com.example.data.repository.RecentImagesRepository
import com.example.data.repository.SettingsRepository
import com.example.domain.model.AppSettings
import com.example.domain.model.ThemeMode
import com.example.domain.model.TimeLapseQuality
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository.getInstance(application)
    private val recentImagesRepository = RecentImagesRepository.getInstance(application)
    private val projectRepository = ProjectRepository.getInstance(application)

    val settings: StateFlow<AppSettings> = settingsRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppSettings()
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDynamicColor(enabled)
        }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setKeepScreenOn(enabled)
        }
    }

    fun setDefaultOpacity(opacity: Float) {
        viewModelScope.launch {
            settingsRepository.setDefaultOpacity(opacity)
        }
    }

    fun setHideControlsOnStart(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setHideControlsOnStart(enabled)
        }
    }

    fun setTimeLapseIntervalSeconds(seconds: Int) {
        viewModelScope.launch {
            settingsRepository.setTimeLapseIntervalSeconds(seconds)
        }
    }

    fun setTimeLapseQuality(quality: TimeLapseQuality) {
        viewModelScope.launch {
            settingsRepository.setTimeLapseQuality(quality)
        }
    }

    fun setTimeLapseIncludeOverlay(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setTimeLapseIncludeOverlay(enabled)
        }
    }

    fun clearRecentImages() {
        viewModelScope.launch {
            recentImagesRepository.clearAllRecentImages()
        }
    }

    fun deleteAllProjects() {
        viewModelScope.launch {
            projectRepository.deleteAllProjects()
        }
    }
}
