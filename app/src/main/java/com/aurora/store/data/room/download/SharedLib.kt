package com.aurora.store.data.room.download

import android.os.Parcelable
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.File
import com.aurora.store.data.model.Installer
import kotlinx.parcelize.Parcelize

@Parcelize
data class SharedLib(
    val packageName: String,
    val versionCode: Int,
    var fileList: List<File>,
    val installer: Installer? = null,
    val sessionId: Int? = null,
    val installProgress: Int? = null,
    val installedAt: Long? = null
) : Parcelable {
    companion object {
        fun fromApp(app: App): SharedLib {
            return SharedLib(
                app.packageName,
                app.versionCode,
                app.fileList.filterNot { it.url.isBlank() },
                null,
                null,
                null,
                null
            )
        }
    }
}
