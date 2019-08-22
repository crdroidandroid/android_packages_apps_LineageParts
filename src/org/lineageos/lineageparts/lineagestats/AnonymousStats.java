/*
 * Copyright (C) 2015 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.lineageparts.lineagestats;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;

import lineageos.providers.LineageSettings;

import org.lineageos.lineageparts.R;
import org.lineageos.lineageparts.SettingsPreferenceFragment;

public class AnonymousStats extends SettingsPreferenceFragment {

    private static final String PREF_FILE_NAME = "crDroidStats";
    /* package */ static final String ANONYMOUS_OPT_IN = "pref_anonymous_opt_in";
    /* package */ static final String ANONYMOUS_LAST_CHECKED = "pref_anonymous_checked_in";

    /* package */ static final String KEY_LAST_JOB_ID = "last_job_id";
    /* package */ static final int QUEUE_MAX_THRESHOLD = 1000;

    private Context mContext;

    public static SharedPreferences getPreferences(Context context) {
        return context.getSharedPreferences(PREF_FILE_NAME, 0);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.anonymous_stats);

        mContext = getActivity().getApplicationContext();

        SettingsObserver observer = new SettingsObserver(new Handler(Looper.getMainLooper()));
        observer.observe();
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();

            resolver.registerContentObserver(LineageSettings.Secure.getUriFor(
                    LineageSettings.Secure.STATS_COLLECTION), true, this,
                    UserHandle.USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (uri.equals(LineageSettings.Secure.getUriFor(
                                   LineageSettings.Secure.STATS_COLLECTION))) {
                boolean enable = LineageSettings.Secure.getInt(mContext.getContentResolver(),
                    LineageSettings.Secure.STATS_COLLECTION, 1) == 1;
                if (enable) ReportingServiceManager.launchService(mContext, true); 
            }
        }
    }

    public static void updateLastSynced(Context context) {
        getPreferences(context)
                .edit()
                .putLong(ANONYMOUS_LAST_CHECKED,System.currentTimeMillis())
                .commit();
    }

    public static int getLastJobId(Context context) {
        return getPreferences(context).getInt(KEY_LAST_JOB_ID, 0);
    }

    private static void setLastJobId(Context context, int id) {
        getPreferences(context)
                .edit()
                .putInt(KEY_LAST_JOB_ID, id)
                .commit();
    }

    public static int getNextJobId(Context context) {
        int lastId = getLastJobId(context);
        if (lastId >= QUEUE_MAX_THRESHOLD) {
            lastId = 1;
        } else {
            lastId += 1;
        }
        setLastJobId(context, lastId);
        return lastId;
    }
}
