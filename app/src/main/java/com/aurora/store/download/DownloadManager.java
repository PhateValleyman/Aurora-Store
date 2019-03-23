/*
 * Aurora Store
 * Copyright (C) 2019, Rahul Kumar Patel <whyorean@gmail.com>
 *
 * Aurora Store is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * Aurora Store is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Aurora Store.  If not, see <http://www.gnu.org/licenses/>.
 *
 *
 */

package com.aurora.store.download;

import android.content.Context;

import com.aurora.store.Constants;
import com.aurora.store.NotificationProvider;
import com.aurora.store.utility.Util;
import com.tonyodev.fetch2.DefaultFetchNotificationManager;
import com.tonyodev.fetch2.Fetch;
import com.tonyodev.fetch2.FetchConfiguration;
import com.tonyodev.fetch2okhttp.OkHttpDownloader;

public class DownloadManager {

    private Context context;
    private Fetch fetch;

    public DownloadManager(Context context) {
        this.context = context;
        init();
    }

    private void init() {
        FetchConfiguration.Builder fetchConfiguration = new FetchConfiguration.Builder(context);
        fetchConfiguration.setHttpDownloader(new OkHttpDownloader(Util.getDownloadStrategy(context)));
        fetchConfiguration.setNamespace(Constants.TAG);
        if (Util.isNotificationEnabled(context) &&
                Util.getNotificationProvider(context) == NotificationProvider.NATIVE)
            fetchConfiguration.setNotificationManager(new DefaultFetchNotificationManager(context));
        fetchConfiguration.setDownloadConcurrentLimit(Util.getActiveDownloadCount(context));
        fetch = Fetch.Impl.getInstance(fetchConfiguration.build());
        if (Util.isFetchDebugEnabled(context))
            fetch.enableLogging(true);
    }

    public Fetch getFetchInstance() {
        return fetch;
    }
}

