package com.clipr.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CliprDao {

    @Query("SELECT * FROM projects ORDER BY updatedAtEpochMs DESC")
    fun observeAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE projectId = :projectId LIMIT 1")
    suspend fun getProjectById(projectId: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE projectId = :projectId")
    suspend fun deleteProjectById(projectId: String)

    @Query("SELECT * FROM clip_candidates WHERE projectId = :projectId ORDER BY clipNumber ASC")
    fun observeClipsForProject(projectId: String): Flow<List<ClipCandidateEntity>>

    @Query("SELECT * FROM clip_candidates WHERE projectId = :projectId ORDER BY clipNumber ASC")
    suspend fun getClipsForProject(projectId: String): List<ClipCandidateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertClips(clips: List<ClipCandidateEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertClip(clip: ClipCandidateEntity)

    @Query("DELETE FROM clip_candidates WHERE projectId = :projectId AND isApproved = 0 AND isUserEdited = 0")
    suspend fun deleteUnlockedClipsForProject(projectId: String)

    @Query("SELECT * FROM subtitle_words WHERE clipId = :clipId ORDER BY wordIndex ASC")
    suspend fun getSubtitleWordsForClip(clipId: String): List<SubtitleWordEntity>

    @Query("DELETE FROM subtitle_words WHERE clipId = :clipId")
    suspend fun deleteSubtitleWordsForClip(clipId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubtitleWords(words: List<SubtitleWordEntity>)

    @Transaction
    suspend fun replaceSubtitleWordsForClip(clipId: String, words: List<SubtitleWordEntity>) {
        deleteSubtitleWordsForClip(clipId)
        insertSubtitleWords(words)
    }

    @Query("SELECT * FROM exported_shorts ORDER BY exportedAtEpochMs DESC")
    fun observeExportedShorts(): Flow<List<ExportedShortEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExportedShort(exportedShort: ExportedShortEntity)

    @Query("DELETE FROM exported_shorts WHERE exportId = :exportId")
    suspend fun deleteExportedShortById(exportId: String)
}
