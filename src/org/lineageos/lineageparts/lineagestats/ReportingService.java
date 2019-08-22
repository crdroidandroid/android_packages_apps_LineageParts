/*
 * Copyright (C) 2015 The CyanogenMod Project
 *               2017 The LineageOS Project
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

import android.app.IntentService;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.PersistableBundle;
import android.util.Log;

public class ReportingService extends IntentService {
    /* package */ static final String TAG = "crDroidStats";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    public ReportingService() {
        super(ReportingService.class.getSimpleName());
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        JobScheduler js = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);

        String deviceId = Utilities.getUniqueID(getApplicationContext());
        String deviceName = Utilities.getDevice();
        String deviceCrVersion = Utilities.getModVersion();
        String deviceBuildDate = Utilities.getBuildDate();
        String deviceAndroidVersion = Utilities.getAndroidVersion();
        String deviceTag = Utilities.getTag();
        String deviceCountry = Utilities.getCountryCode(getApplicationContext());
        String deviceCarrier = Utilities.getCarrier(getApplicationContext());
        String deviceCarrierId = Utilities.getCarrierId(getApplicationContext());

        final int crdroidOldJobId = AnonymousStats.getLastJobId(getApplicationContext());
        final int crdroidOrgJobId = AnonymousStats.getNextJobId(getApplicationContext());

        if (DEBUG) Log.d(TAG, "scheduling job id: " + crdroidOrgJobId);

        PersistableBundle crdroidBundle = new PersistableBundle();
        crdroidBundle.putString(StatsUploadJobService.KEY_DEVICE_NAME, deviceName);
        crdroidBundle.putString(StatsUploadJobService.KEY_UNIQUE_ID, deviceId);
        crdroidBundle.putString(StatsUploadJobService.KEY_CR_VERSION, deviceCrVersion);
        crdroidBundle.putString(StatsUploadJobService.KEY_BUILD_DATE, deviceBuildDate);
        crdroidBundle.putString(StatsUploadJobService.KEY_ANDROID_VERSION, deviceAndroidVersion);
        crdroidBundle.putString(StatsUploadJobService.KEY_TAG, deviceTag);
        crdroidBundle.putString(StatsUploadJobService.KEY_COUNTRY, deviceCountry);
        crdroidBundle.putString(StatsUploadJobService.KEY_CARRIER, deviceCarrier);
        crdroidBundle.putString(StatsUploadJobService.KEY_CARRIER_ID, deviceCarrierId);
        crdroidBundle.putLong(StatsUploadJobService.KEY_TIMESTAMP, System.currentTimeMillis());

        // set job types
        crdroidBundle.putInt(StatsUploadJobService.KEY_JOB_TYPE,
                StatsUploadJobService.JOB_TYPE_CRDROID);

        // schedule crdroid stats upload
        js.schedule(new JobInfo.Builder(crdroidOrgJobId, new ComponentName(getPackageName(),
                StatsUploadJobService.class.getName()))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(1000)
                .setExtras(crdroidBundle)
                .setPersisted(true)
                .build());

        // cancel old job in case it didn't run yet
        js.cancel(crdroidOldJobId);

        // reschedule
        AnonymousStats.updateLastSynced(this);
        ReportingServiceManager.setAlarm(this);
    }
}
