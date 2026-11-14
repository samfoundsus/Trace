package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.SavedProject

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val imagePath: String,
    val offsetX: Float,
    val offsetY: Float,
    val scale: Float,
    val rotation: Float,
    val opacity: Float,
    val flipHorizontal: Boolean,
    val flipVertical: Boolean,
    val isOutlineEnabled: Boolean,
    val outlineStrength: Float,
    val isGridEnabled: Boolean,
    val gridCellSizeDp: Float,
    val updatedAt: Long
)

fun ProjectEntity.toDomain(): SavedProject = SavedProject(
    id = id,
    name = name,
    imagePath = imagePath,
    offsetX = offsetX,
    offsetY = offsetY,
    scale = scale,
    rotation = rotation,
    opacity = opacity,
    flipHorizontal = flipHorizontal,
    flipVertical = flipVertical,
    isOutlineEnabled = isOutlineEnabled,
    outlineStrength = outlineStrength,
    isGridEnabled = isGridEnabled,
    gridCellSizeDp = gridCellSizeDp,
    updatedAt = updatedAt
)

fun SavedProject.toEntity(): ProjectEntity = ProjectEntity(
    id = id,
    name = name,
    imagePath = imagePath,
    offsetX = offsetX,
    offsetY = offsetY,
    scale = scale,
    rotation = rotation,
    opacity = opacity,
    flipHorizontal = flipHorizontal,
    flipVertical = flipVertical,
    isOutlineEnabled = isOutlineEnabled,
    outlineStrength = outlineStrength,
    isGridEnabled = isGridEnabled,
    gridCellSizeDp = gridCellSizeDp,
    updatedAt = updatedAt
)
