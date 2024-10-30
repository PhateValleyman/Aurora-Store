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

package com.aurora.store

import android.app.Application
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.aurora.extensions.isPAndAbove
import com.aurora.extensions.setAppTheme
import com.aurora.store.data.event.EventFlow
import com.aurora.store.data.helper.DownloadHelper
import com.aurora.store.data.helper.InstallHelper
import com.aurora.store.data.helper.UpdateHelper
import com.aurora.store.data.receiver.PackageManagerReceiver
import com.aurora.store.util.CommonUtil
import com.aurora.store.util.NotificationUtil
import com.aurora.store.util.PackageUtil
import com.aurora.store.util.Preferences
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import okhttp3.OkHttpClient
import org.lsposed.hiddenapibypass.HiddenApiBypass
import javax.inject.Inject

@HiltAndroidApp
class AuroraApp : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var downloadHelper: DownloadHelper

    @Inject
    lateinit var updateHelper: UpdateHelper

    @Inject
    lateinit var installHelper: InstallHelper

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .setWorkerFactory(workerFactory)
            .build()

    companion object {
        // Alternative to GlobalScope
        var scope = MainScope()
            private set

        val enqueuedInstalls: MutableSet<String> = mutableSetOf()
        val events = EventFlow()
    }

    override fun onCreate() {
        super.onCreate()
        // Set the app theme
        val themeStyle = Preferences.getInteger(this, Preferences.PREFERENCE_THEME_STYLE)
        setAppTheme(themeStyle)

        // Apply dynamic colors to activities
        DynamicColors.applyToActivitiesIfAvailable(this)

        // Required for Shizuku installer
        if (isPAndAbove) HiddenApiBypass.addHiddenApiExemptions("I", "L")

        //Create Notification Channels
        NotificationUtil.createNotificationChannel(this)

        // Initialize Download and Update helpers to observe and trigger downloads
        downloadHelper.init()
        updateHelper.init()
        installHelper.init()

        //Register broadcast receiver for package install/uninstall
        ContextCompat.registerReceiver(
            this,
            object : PackageManagerReceiver() {},
            PackageUtil.getFilter(),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        CommonUtil.cleanupInstallationSessions(applicationContext)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        scope.cancel("onLowMemory() called by system")
        scope = MainScope()
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader(this).newBuilder()
            .okHttpClient(okHttpClient)
            .build()
    }
}
