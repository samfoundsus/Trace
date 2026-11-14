package com.example.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class SavedProject(
    val id: String,
    val name: String,
    val imagePath: String,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val opacity: Float = 0.55f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val isOutlineEnabled: Boolean = false,
    val outlineStrength: Float = 0.5f,
    val isGridEnabled: Boolean = false,
    val gridCellSizeDp: Float = 48f,
    val updatedAt: Long = System.currentTimeMillis()
)
