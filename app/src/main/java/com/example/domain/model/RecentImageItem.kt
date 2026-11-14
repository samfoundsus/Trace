package com.example.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class RecentImageItem(
    val id: String,
    val filePath: String,
    val createdAt: Long,
    val width: Int,
    val height: Int
)
