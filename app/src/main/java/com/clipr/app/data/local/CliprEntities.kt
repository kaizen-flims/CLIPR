package com.clipr.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val projectId: String,
    val title: String,
    val sourceUri: String,
    val sourceFileName: String,
    val sourceDurationMs: Long,
    val widthPx: Int,
    val heightPx: Int,
    val frameRate: Float,
    val videoCodecMime: String,
    val audioCodecMime: String?,
    val layoutPresetId: String,
    val upperSplitRatio: Float,
    val subtitlePresetId: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

@Entity(
    tableName = "clip_candidates",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["projectId"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId")]
)
data class ClipCandidateEntity(
    @PrimaryKey val clipId: String,
    val projectId: String,
    val clipNumber: Int,
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val rationale: String,
    val hookScore: Int,
    val categoryTag: String,
    val suggestedLayoutId: String,
    val isApproved: Boolean,
    val isUserEdited: Boolean
)

@Entity(
    tableName = "subtitle_words",
    foreignKeys = [
        ForeignKey(
            entity = ClipCandidateEntity::class,
            parentColumns = ["clipId"],
            childColumns = ["clipId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("clipId")]
)
data class SubtitleWordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val clipId: String,
    val wordIndex: Int,
    val word: String,
    val relativeStartMs: Long,
    val relativeEndMs: Long,
    val confidence: Float = 0.95f
)

@Entity(tableName = "exported_shorts")
data class ExportedShortEntity(
    @PrimaryKey val exportId: String,
    val projectId: String,
    val clipId: String,
    val title: String,
    val outputFilePath: String,
    val mediaStoreUri: String?,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val widthPx: Int = 1080,
    val heightPx: Int = 1920,
    val exportedAtEpochMs: Long
)
