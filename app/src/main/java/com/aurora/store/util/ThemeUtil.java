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

package com.aurora.store.util;

import android.content.Context;
import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;

import com.aurora.store.R;
import com.aurora.store.manager.LocaleManager;

public class ThemeUtil {

    private int currentTheme;

    public static int getSelectedTheme(AppCompatActivity activity) {
        String theme = Util.getTheme(activity);
        switch (theme) {
            case "light":
                return R.style.AppTheme;
            case "dark":
                return R.style.AppTheme_Dark;
            case "black":
                return R.style.AppTheme_Black;
            case "auto":
                return isSystemThemeLight(activity) ? R.style.AppTheme : R.style.AppTheme_Dark;
            default:
                return R.style.AppTheme;
        }
    }

    public static boolean isLightTheme(Context context) {
        String theme = Util.getTheme(context);
        switch (theme) {
            case "dark":
            case "black":
                return false;
            case "auto":
                return isSystemThemeLight(context);
            default:
                return true;
        }
    }

    public void onCreate(AppCompatActivity activity) {
        new LocaleManager(activity).setLocale();
        currentTheme = getSelectedTheme(activity);
        activity.setTheme(currentTheme);
    }

    public void onResume(AppCompatActivity activity) {
        if (currentTheme != getSelectedTheme(activity)) {
            Intent intent = activity.getIntent();
            activity.finish();
            activity.startActivity(intent);
        }
    }

    private static boolean isSystemThemeLight(Context context) {
        return (context.getResources().getConfiguration().uiMode & 48) == 32;
    }
}
