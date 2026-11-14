package com.example.data.repository

import android.content.Context
import com.example.data.local.ProjectDao
import com.example.data.local.TraceDatabase
import com.example.data.local.toDomain
import com.example.data.local.toEntity
import com.example.domain.model.SavedProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ProjectRepository(
    private val projectDao: ProjectDao,
    private val projectImagesDir: File
) {
    val projects: Flow<List<SavedProject>> = projectDao.observeAllProjects().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun getProjectById(projectId: String): SavedProject? = withContext(Dispatchers.IO) {
        projectDao.getProjectById(projectId)?.toDomain()
    }

    suspend fun nextDefaultProjectName(): String = withContext(Dispatchers.IO) {
        val existingNames = projectDao.getAllProjectsOnce().map { it.name.trim() }.toSet()
        var index = existingNames.size + 1
        while ("Untitled $index" in existingNames) {
            index++
        }
        "Untitled $index"
    }

    /**
     * Saves a project into Room and copies the source image into dedicated app project storage
     * so the project remains intact even if the original gallery image or recent cache is deleted.
     */
    suspend fun saveProject(
        project: SavedProject,
        sourceImagePath: String
    ): SavedProject = withContext(Dispatchers.IO) {
        if (!projectImagesDir.exists()) {
            projectImagesDir.mkdirs()
        }
        val projectId = project.id.ifBlank { UUID.randomUUID().toString() }
        val targetFile = File(projectImagesDir, "project_$projectId.png")

        val sourceFile = File(sourceImagePath)
        if (sourceFile.exists() && sourceFile.canonicalPath != targetFile.canonicalPath) {
            sourceFile.copyTo(targetFile, overwrite = true)
        }

        val finalPath = if (targetFile.exists()) targetFile.absolutePath else sourceImagePath
        val cleanName = project.name.trim().ifBlank { nextDefaultProjectName() }
        val saved = project.copy(
            id = projectId,
            name = cleanName,
            imagePath = finalPath,
            updatedAt = System.currentTimeMillis()
        )
        projectDao.upsertProject(saved.toEntity())
        saved
    }

    /**
     * Auto-saves transform, opacity, flip, outline, and grid changes for an already-saved project.
     */
    suspend fun autoSaveExistingProject(
        projectId: String,
        offsetX: Float,
        offsetY: Float,
        scale: Float,
        rotation: Float,
        opacity: Float,
        flipHorizontal: Boolean,
        flipVertical: Boolean,
        isOutlineEnabled: Boolean,
        outlineStrength: Float,
        isGridEnabled: Boolean,
        gridCellSizeDp: Float,
        currentImagePath: String? = null
    ): SavedProject? = withContext(Dispatchers.IO) {
        if (projectId.isBlank()) return@withContext null
        val existing = projectDao.getProjectById(projectId) ?: return@withContext null

        var resolvedImagePath = existing.imagePath
        if (!currentImagePath.isNullOrBlank() && currentImagePath != existing.imagePath) {
            if (!projectImagesDir.exists()) {
                projectImagesDir.mkdirs()
            }
            val src = File(currentImagePath)
            val dst = File(projectImagesDir, "project_$projectId.png")
            if (src.exists()) {
                if (src.canonicalPath != dst.canonicalPath) {
                    src.copyTo(dst, overwrite = true)
                }
                resolvedImagePath = dst.absolutePath
            }
        }

        val updated = existing.copy(
            imagePath = resolvedImagePath,
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
            updatedAt = System.currentTimeMillis()
        )
        projectDao.upsertProject(updated)
        updated.toDomain()
    }

    suspend fun renameProject(projectId: String, newName: String) = withContext(Dispatchers.IO) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return@withContext
        projectDao.renameProject(projectId, trimmed, System.currentTimeMillis())
    }

    suspend fun duplicateProject(projectId: String): SavedProject? = withContext(Dispatchers.IO) {
        val original = projectDao.getProjectById(projectId)?.toDomain() ?: return@withContext null
        val newId = UUID.randomUUID().toString()
        val copyName = "${original.name} (copy)"
        val duplicated = original.copy(
            id = newId,
            name = copyName,
            updatedAt = System.currentTimeMillis()
        )
        saveProject(duplicated, original.imagePath)
    }

    suspend fun deleteProject(projectId: String) = withContext(Dispatchers.IO) {
        val existing = projectDao.getProjectById(projectId)
        projectDao.deleteProjectById(projectId)
        existing?.let {
            val file = File(it.imagePath)
            if (file.exists() && file.parentFile?.canonicalPath == projectImagesDir.canonicalPath) {
                runCatching { file.delete() }
            }
        }
    }

    suspend fun deleteAllProjects() = withContext(Dispatchers.IO) {
        projectDao.deleteAllProjects()
        projectImagesDir.listFiles()?.forEach { file ->
            runCatching { file.delete() }
        }
    }

    companion object {
        @Volatile
        private var instance: ProjectRepository? = null

        fun getInstance(context: Context): ProjectRepository {
            return instance ?: synchronized(this) {
                instance ?: ProjectRepository(
                    projectDao = TraceDatabase.getInstance(context).projectDao(),
                    projectImagesDir = File(context.applicationContext.filesDir, "project_images")
                ).also { instance = it }
            }
        }
    }
}
