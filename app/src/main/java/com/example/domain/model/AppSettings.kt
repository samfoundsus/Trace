package com.example.domain.model

import androidx.compose.runtime.Immutable

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class TimeLapseQuality(
    val label: String,
    val shortSidePx: Int,
    val longSidePx: Int,
    val bitrateBps: Int
) {
    QUALITY_720P(
        label = "720p",
        shortSidePx = 720,
        longSidePx = 1280,
        bitrateBps = 4_000_000
    ),
    QUALITY_1080P(
        label = "1080p",
        shortSidePx = 1080,
        longSidePx = 1920,
        bitrateBps = 8_000_000
    )
}

@Immutable
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val dynamicColor: Boolean = true,
    val keepScreenOn: Boolean = true,
    val defaultOpacity: Float = 0.55f,
    val hideControlsOnStart: Boolean = false,
    val hasSeenTraceHint: Boolean = false,
    val timeLapseIntervalSeconds: Int = 2,
    val timeLapseQuality: TimeLapseQuality = TimeLapseQuality.QUALITY_720P,
    val timeLapseIncludeOverlay: Boolean = false,
    val timeLapseCountdownSeconds: Int = 3
) {
    companion object {
        val SUPPORTED_INTERVALS_SECONDS = listOf(1, 2, 5, 10)
        val SUPPORTED_COUNTDOWNS_SECONDS = listOf(0, 3, 5, 10)
    }
}
