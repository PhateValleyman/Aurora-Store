package com.aurora.store.data.model

import androidx.annotation.StringRes
import com.aurora.store.R

enum class DownloadStatus(@StringRes val localized: Int) {
    DOWNLOADING(R.string.status_downloading),
    FAILED(R.string.status_failed),
    CANCELLED(R.string.status_cancelled),
    COMPLETED(R.string.status_completed),
    QUEUED(R.string.status_queued),
    UNAVAILABLE(R.string.status_unavailable),
    AWAITING_INSTALL(R.string.action_installing),
    INSTALLING(R.string.action_installing),
    INSTALLED(R.string.title_installed);

    companion object {
        val finished = listOf(FAILED, CANCELLED, COMPLETED, INSTALLED)
        val running = listOf(QUEUED, DOWNLOADING)
        val installing = listOf(AWAITING_INSTALL, INSTALLING)
    }
}
