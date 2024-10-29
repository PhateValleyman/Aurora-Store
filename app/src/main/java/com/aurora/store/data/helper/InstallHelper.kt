/*
 * Aurora Store
 *  Copyright (C) 2021, Rahul Kumar Patel <whyorean@gmail.com>
 *  Copyright (C) 2023, grrfe <grrfe@420blaze.it>
 *
 *  Aurora Store is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  Aurora Store is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Aurora Store.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.aurora.store.data.helper

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageInstaller.SessionInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.pm.PackageInfoCompat
import com.aurora.extensions.getUpdateOwnerPackageNameCompat
import com.aurora.extensions.isOAndAbove
import com.aurora.extensions.isPAndAbove
import com.aurora.extensions.isSAndAbove
import com.aurora.store.AuroraApp
import com.aurora.store.BuildConfig
import com.aurora.store.data.installer.AMInstaller
import com.aurora.store.data.installer.base.IInstaller
import com.aurora.store.data.installer.NativeInstaller
import com.aurora.store.data.installer.RootInstaller
import com.aurora.store.data.installer.ServiceInstaller
import com.aurora.store.data.installer.SessionInstaller
import com.aurora.store.data.installer.ShizukuInstaller
import com.aurora.store.data.model.DownloadStatus
import com.aurora.store.data.model.Installer
import com.aurora.store.data.model.InstallerInfo
import com.aurora.store.data.room.download.Download
import com.aurora.store.data.room.download.DownloadDao
import com.aurora.store.util.PackageUtil
import com.aurora.store.util.Preferences
import com.aurora.store.util.Preferences.PREFERENCE_INSTALLER_ID
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import rikka.sui.Sui
import java.util.Calendar
import javax.inject.Inject

/**
 * Helper class to install apps
 */
class InstallHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionInstaller: SessionInstaller,
    private val nativeInstaller: NativeInstaller,
    private val rootInstaller: RootInstaller,
    private val serviceInstaller: ServiceInstaller,
    private val amInstaller: AMInstaller,
    private val shizukuInstaller: ShizukuInstaller,
    private val downloadDao: DownloadDao
): PackageInstaller.SessionCallback() {

    companion object {
        const val ACTION_INSTALL_STATUS = "com.aurora.store.data.helper.InstallHelper.INSTALL_STATUS"

        const val EXTRA_PACKAGE_NAME = "com.aurora.store.data.helper.InstallHelper.EXTRA_PACKAGE_NAME"
        const val EXTRA_VERSION_CODE = "com.aurora.store.data.helper.InstallHelper.EXTRA_VERSION_CODE"
        const val EXTRA_DISPLAY_NAME = "com.aurora.store.data.helper.InstallHelper.EXTRA_DISPLAY_NAME"
    }

    val availableInstallersInfo: List<InstallerInfo>
        get() = listOfNotNull(
            SessionInstaller.getInstallerInfo(context),
            NativeInstaller.getInstallerInfo(context),
            if (hasRootAccess) RootInstaller.getInstallerInfo(context) else null,
            if (hasAuroraService) ServiceInstaller.getInstallerInfo(context) else null,
            if (hasAppManager) AMInstaller.getInstallerInfo(context) else null,
            if (isOAndAbove && hasShizukuOrSui) ShizukuInstaller.getInstallerInfo(context) else null
        )

    val hasRootAccess: Boolean
        get() = Shell.getShell().isRoot

    val hasAuroraService: Boolean
        get() {
            return try {
                val packageInfo = PackageUtil.getPackageInfo(
                    context,
                    ServiceInstaller.PRIVILEGED_EXTENSION_PACKAGE_NAME
                )
                val version = PackageInfoCompat.getLongVersionCode(packageInfo)
                packageInfo.applicationInfo!!.enabled && version >= 9
            } catch (exception: Exception) {
                false
            }
        }

    val hasAppManager: Boolean
        get() = PackageUtil.isInstalled(context, AMInstaller.AM_PACKAGE_NAME) or
                PackageUtil.isInstalled(context, AMInstaller.AM_DEBUG_PACKAGE_NAME)

    @get:RequiresApi(Build.VERSION_CODES.O)
    val hasShizukuOrSui: Boolean
        get() = PackageUtil.isInstalled(
            context,
            ShizukuInstaller.SHIZUKU_PACKAGE_NAME
        ) || Sui.isSui()

    @get:RequiresApi(Build.VERSION_CODES.O)
    val hasShizukuPerm: Boolean
        get() = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    private val currentInstaller: Installer
        get() = Installer.entries[Preferences.getInteger(context, PREFERENCE_INSTALLER_ID)]

    private val defaultInstaller: IInstaller
        get() = sessionInstaller

    private val preferredInstaller: IInstaller
        get() = when (currentInstaller) {
            Installer.SESSION -> sessionInstaller
            Installer.NATIVE -> nativeInstaller
            Installer.ROOT -> if (hasRootAccess) rootInstaller else defaultInstaller
            Installer.SERVICE -> if (hasAuroraService) serviceInstaller else defaultInstaller
            Installer.AM -> if (hasAppManager) amInstaller else defaultInstaller
            Installer.SHIZUKU -> {
                if (isOAndAbove && hasShizukuOrSui && hasShizukuPerm) {
                    shizukuInstaller
                } else {
                    defaultInstaller
                }
            }
        }

    private val installerPackageNames = listOfNotNull(
        "com.aurora.store",
        "com.aurora.store.debug",
        "com.aurora.store.nightly",
        ServiceInstaller.PRIVILEGED_EXTENSION_PACKAGE_NAME,
        AMInstaller.AM_PACKAGE_NAME,
        AMInstaller.AM_DEBUG_PACKAGE_NAME
    )

    private val downloads = downloadDao.downloads()
        .stateIn(AuroraApp.scope, SharingStarted.WhileSubscribed(), emptyList())

    fun init() {
        AuroraApp.scope.launch {
            cancelFailedInstalls()
        }.invokeOnCompletion {
            observeInstalls()
        }
    }

    private fun observeInstalls() {
        downloads.onEach { list ->
            list.filter { it.status == DownloadStatus.AWAITING_INSTALL }.forEach {
                preferredInstaller.install(it)
            }
        }.launchIn(AuroraApp.scope)
    }

    override fun onCreated(sessionId: Int) {
        AuroraApp.scope.launch { updateSessionDetails(sessionId, DownloadStatus.INSTALLING) }
    }

    override fun onBadgingChanged(sessionId: Int) {}

    override fun onActiveChanged(sessionId: Int, active: Boolean) {}

    override fun onProgressChanged(sessionId: Int, progress: Float) {
        AuroraApp.scope.launch { updateSessionDetails(sessionId, DownloadStatus.INSTALLING) }
    }

    override fun onFinished(sessionId: Int, success: Boolean) {
        AuroraApp.scope.launch {
            val status = if (success) DownloadStatus.INSTALLED else DownloadStatus.FAILED
            updateSessionDetails(sessionId, status)
        }
    }

    /**
     * Enqueues a download for install
     */
    suspend fun enqueueInstall(download: Download) {
        downloadDao.updateStatus(download.packageName, DownloadStatus.AWAITING_INSTALL)
    }

    /**
     * Prompts user to uninstall the given package
     */
    fun uninstall(packageName: String) {
        val intent = Intent().apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (isPAndAbove) {
                action = Intent.ACTION_DELETE
            } else {
                @Suppress("DEPRECATION")
                action = Intent.ACTION_UNINSTALL_PACKAGE
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
            }
        }
        context.startActivity(intent)
    }

    /**
     * Checks if the given package can be silently installed
     * @param packageName Package to silently install
     * @param targetSdk SDK level targeted by the package
     */
    fun canInstallSilently(packageName: String, targetSdk: Int): Boolean {
        return when (currentInstaller) {
            Installer.SESSION -> {
                // Silent install cannot be done on initial install and below A12
                if (!PackageUtil.isInstalled(context, packageName) || !isSAndAbove) return false

                // We cannot do silent updates if we are not the update owner
                if (context.packageManager.getUpdateOwnerPackageNameCompat(packageName) != BuildConfig.APPLICATION_ID) return false

                // Ensure app being installed satisfies Android's requirement for targetSdk level
                when (Build.VERSION.SDK_INT) {
                    Build.VERSION_CODES.VANILLA_ICE_CREAM -> targetSdk == Build.VERSION_CODES.TIRAMISU
                    Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> targetSdk == Build.VERSION_CODES.S
                    Build.VERSION_CODES.TIRAMISU -> targetSdk == Build.VERSION_CODES.R
                    Build.VERSION_CODES.S -> targetSdk == Build.VERSION_CODES.Q
                    else -> false // Only Android version above 12 can silently update apps
                }
            }
            Installer.NATIVE -> false // Deprecated
            Installer.ROOT -> hasRootAccess
            Installer.SERVICE -> false // Deprecated
            Installer.AM -> false // We cannot check if AppManager has ability to auto-update
            Installer.SHIZUKU -> isOAndAbove && hasShizukuOrSui && hasShizukuPerm
        }
    }

    private suspend fun cancelFailedInstalls() {
        downloadDao.downloads().first().filter { it.sessionId != null }.forEach {
            context.packageManager.packageInstaller.abandonSession(it.sessionId!!)
        }
    }

    private suspend fun updateSessionDetails(sessionId: Int, status: DownloadStatus) {
        val sessionInfo = context.packageManager.packageInstaller.getSessionInfo(sessionId) ?: return

        // We only care about packages installing through our supported installers
        if (!installerPackageNames.contains(sessionInfo.installerPackageName)) return

        downloads.first().find { isValidDownload(it, sessionInfo) }?.let { download ->
            downloadDao.updateStatus(download.packageName, status)
            updateInstallDetails(download, sessionInfo)
        }
    }

    private fun isValidDownload(download: Download, sessionInfo: SessionInfo): Boolean {
        return download.packageName == sessionInfo.appPackageName ||
                download.sharedLibs.any { it.packageName == sessionInfo.appPackageName }
    }

    private suspend fun updateInstallDetails(download: Download, sessionInfo: SessionInfo) {
        val progress = (sessionInfo.progress * 100).toInt()
        val installedAt = if (progress == 100) Calendar.getInstance().timeInMillis else null

        // Update installation details while preserving existing installer name and sessionId
        if (download.sharedLibs.any { it.packageName == sessionInfo.appPackageName }) {
            val lib = download.sharedLibs.find { it.packageName == sessionInfo.appPackageName }!!
            val updatedLib = lib.copy(
                installer = lib.installer ?: currentInstaller,
                sessionId = lib.sessionId ?: sessionInfo.sessionId,
                installProgress = progress,
                installedAt = installedAt
            )

            val updatedLibs = download.sharedLibs.toMutableList().apply {
                add(0, updatedLib)
            }.distinctBy { it.packageName }

            downloadDao.updateSharedLibs(download.packageName, updatedLibs)
        } else {
            downloadDao.updateInstallDetails(
                download.packageName,
                download.installer ?: currentInstaller,
                download.sessionId ?: sessionInfo.sessionId,
                progress,
                installedAt
            )
        }
    }
}
