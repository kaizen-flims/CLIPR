package com.clipr.app

import android.app.Application
import java.io.File

/**
 * Root Application class for CLIPR.
 * Initializes application-private working directories for proxy transcoding and rendered Shorts.
 */
class CliprApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initializePrivateStorageDirectories()
    }

    private fun initializePrivateStorageDirectories() {
        File(cacheDir, WORK_CACHE_DIR_NAME).mkdirs()
        File(filesDir, EXPORTS_DIR_NAME).mkdirs()
        File(filesDir, PROJECTS_DIR_NAME).mkdirs()
    }

    companion object {
        const val WORK_CACHE_DIR_NAME = "clipr_work"
        const val EXPORTS_DIR_NAME = "exports"
        const val PROJECTS_DIR_NAME = "projects"
    }
}
