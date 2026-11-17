package com.clipr.app.ui.screens.library

import android.app.Application
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clipr.app.core.model.ExportedShort
import com.clipr.app.core.model.ProjectSummary
import com.clipr.app.data.local.CliprDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LibraryTab(val title: String) {
    SAVED_PROJECTS("Saved Projects"),
    RENDERED_CLIPS("Rendered Clips")
}

data class LibraryUiState(
    val selectedTab: LibraryTab = LibraryTab.SAVED_PROJECTS,
    val savedProjects: List<ProjectSummary> = emptyList(),
    val renderedClips: List<ExportedShort> = emptyList()
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val cliprDao = CliprDatabase.getInstance(appContext).cliprDao()

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.US)
        viewModelScope.launch {
            combine(
                cliprDao.observeAllProjects(),
                cliprDao.observeExportedShorts()
            ) { projects, exports ->
                val mappedProjects = projects.map { p ->
                    ProjectSummary(
                        projectId = p.projectId,
                        title = p.title,
                        sourceFileName = p.sourceFileName,
                        sourceDurationMs = p.sourceDurationMs,
                        resolutionLabel = "${p.widthPx}×${p.heightPx}",
                        clipCount = 2,
                        lastModifiedLabel = dateFormat.format(Date(p.updatedAtEpochMs)),
                        statusLabel = "Saved"
                    )
                }
                val mappedExports = exports.map { e ->
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
                Pair(mappedProjects, mappedExports)
            }.collect { (projects, exports) ->
                _uiState.update {
                    it.copy(
                        savedProjects = projects,
                        renderedClips = exports
                    )
                }
            }
        }
    }

    fun selectTab(tab: LibraryTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            cliprDao.deleteProjectById(projectId)
        }
    }

    fun deleteExportedShort(short: ExportedShort) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { File(short.outputFilePath).delete() }
            cliprDao.deleteExportedShortById(short.exportId)
        }
    }

    fun shareExportedShort(short: ExportedShort) {
        runCatching {
            val file = File(short.outputFilePath)
            if (!file.exists()) return
            val contentUri = FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_TITLE, short.title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(shareIntent, "Share CLIPR Short").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(chooser)
        }
    }
}
