package com.clipr.app.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clipr.app.core.model.ExportedShort
import com.clipr.app.core.model.ProjectSummary
import com.clipr.app.core.model.VideoSourceMetadata
import com.clipr.app.data.local.CliprDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HomeUiState(
    val recentProjects: List<ProjectSummary> = emptyList(),
    val recentExports: List<ExportedShort> = emptyList()
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val cliprDao = CliprDatabase.getInstance(application.applicationContext).cliprDao()
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.US)
        viewModelScope.launch {
            combine(
                cliprDao.observeAllProjects(),
                cliprDao.observeExportedShorts()
            ) { projects, exports ->
                val mappedProjects = projects.take(5).map { p ->
                    ProjectSummary(
                        projectId = p.projectId,
                        title = p.title,
                        sourceFileName = p.sourceFileName,
                        sourceDurationMs = p.sourceDurationMs,
                        resolutionLabel = "${p.widthPx}×${p.heightPx}",
                        clipCount = 2,
                        lastModifiedLabel = dateFormat.format(Date(p.updatedAtEpochMs)),
                        statusLabel = "Active"
                    )
                }
                val mappedExports = exports.take(5).map { e ->
                    ExportedShort(
                        exportId = e.exportId,
                        projectId = e.projectId,
                        title = e.title,
                        outputFilePath = e.outputFilePath,
                        durationMs = e.durationMs,
                        fileSizeMb = e.fileSizeBytes.toFloat() / (1024f * 1024f),
                        exportedAtLabel = dateFormat.format(Date(e.exportedAtEpochMs)),
                        resolution = "${e.widthPx}×${e.heightPx} · 9:16"
                    )
                }
                HomeUiState(
                    recentProjects = mappedProjects,
                    recentExports = mappedExports
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }
}
