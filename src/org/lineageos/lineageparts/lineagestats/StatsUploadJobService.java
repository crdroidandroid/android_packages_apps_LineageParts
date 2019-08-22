/*
 * SPDX-FileCopyrightText: 2015 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2017-2023 The LineageOS project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.lineageparts.lineagestats;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.util.ArrayMap;
import android.util.Log;
import org.lineageos.lineageparts.R;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StatsUploadJobService extends JobService {

    private static final String TAG = "crDroidStats";
    private static final boolean DEBUG = true;

    public static final String KEY_JOB_TYPE = "job_type";
    public static final int JOB_TYPE_CRDROID = 1;

    public static final String KEY_UNIQUE_ID = "uniqueId";
    public static final String KEY_DEVICE_NAME = "deviceName";
    public static final String KEY_CR_VERSION = "crVersion";
    public static final String KEY_BUILD_DATE = "buildDate";
    public static final String KEY_ANDROID_VERSION = "androidVersion";
    public static final String KEY_TAG = "tag";
    public static final String KEY_COUNTRY = "country";
    public static final String KEY_CARRIER = "carrier";
    public static final String KEY_CARRIER_ID = "carrierId";
    public static final String KEY_TIMESTAMP = "timeStamp";

    private final Map<JobParameters, StatsUploadTask> mCurrentJobs
            = Collections.synchronizedMap(new ArrayMap<>());

    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        if (DEBUG)
            Log.d(TAG, "onStartJob() called with " + "jobParameters = [" + jobParameters + "]");

        if (!Utilities.isStatsCollectionEnabled(this)) {
            return false;
        }

        final StatsUploadTask uploadTask = new StatsUploadTask(jobParameters);
        mCurrentJobs.put(jobParameters, uploadTask);
        uploadTask.execute();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        if (DEBUG)
            Log.d(TAG, "onStopJob() called with " + "jobParameters = [" + jobParameters + "]");

        final StatsUploadTask cancelledJob;
        cancelledJob = mCurrentJobs.remove(jobParameters);

        if (cancelledJob != null) {
            // cancel the ongoing background task
            cancelledJob.cancel();
            return true; // reschedule
        }

        return false;
    }

    private class StatsUploadTask {

        private final JobParameters mJobParams;
        private boolean mCancelled;

        public StatsUploadTask(JobParameters jobParams) {
            this.mJobParams = jobParams;
        }

        public void execute() {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Handler handler = new Handler(Looper.getMainLooper());
            executor.execute(() -> {
                PersistableBundle extras = mJobParams.getExtras();

                String deviceId = extras.getString(KEY_UNIQUE_ID);
                String deviceName = extras.getString(KEY_DEVICE_NAME);
                String deviceCrVersion = extras.getString(KEY_CR_VERSION);
                String deviceBuildDate = extras.getString(KEY_BUILD_DATE);
                String deviceAndroidVersion = extras.getString(KEY_ANDROID_VERSION);
                String deviceTag = extras.getString(KEY_TAG);
                String deviceCountry = extras.getString(KEY_COUNTRY);
                String deviceCarrier = extras.getString(KEY_CARRIER);
                String deviceCarrierId = extras.getString(KEY_CARRIER_ID);

                boolean success = false;
                int jobType = extras.getInt(KEY_JOB_TYPE, -1);
                if (!mCancelled) {
                    switch (jobType) {
                        case JOB_TYPE_CRDROID:
                            try {
                                JSONObject json = buildStatsRequest(deviceId, deviceName,
                                        deviceCrVersion, deviceBuildDate, deviceAndroidVersion,
                                        deviceTag, deviceCountry, deviceCarrier,
                                        deviceCarrierId);
                                success = uploadToCrdroid(json);
                            } catch (IOException | JSONException e) {
                                Log.e(TAG, "Could not upload stats checkin to community server", e);
                            }
                            break;
                    }
                }
                if (DEBUG)
                    Log.d(TAG, "job id " + mJobParams.getJobId() + ", has finished with success="
                            + success);

                if (!mCancelled) {
                    final boolean finalResult = success;
                    handler.post(() -> {
                        mCurrentJobs.remove(mJobParams);
                        jobFinished(mJobParams, !finalResult);
                    });
                }
            });
        }

        public void cancel() {
            mCancelled = true;
        }
    }

    private JSONObject buildStatsRequest(String deviceId, String deviceName, String deviceCrVersion,
                                         String deviceBuildDate, String deviceAndroidVersion, String deviceTag,
                                         String deviceCountry, String deviceCarrier,
                                         String deviceCarrierId) throws JSONException {
        JSONObject request = new JSONObject();
        request.put("device_hash", deviceId);
        request.put("device_name", deviceName);
        request.put("device_crversion", deviceCrVersion);
        request.put("device_builddate", deviceBuildDate);
        request.put("device_androidversion", deviceAndroidVersion);
        request.put("device_tag", deviceTag);
        request.put("device_country", deviceCountry);
        request.put("device_carrier", deviceCarrier);
        request.put("device_carrier_id", deviceCarrierId);
        return request;
    }

    private boolean uploadToCrdroid(JSONObject json) throws IOException {
        final Uri uri = Uri.parse(getString(R.string.stats_crdroid_url));
        URL url = new URL(uri.toString());
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        try {
            urlConnection.setInstanceFollowRedirects(true);
            urlConnection.setDoOutput(true);
            urlConnection.setDoInput(true);
            urlConnection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

            OutputStream os = urlConnection.getOutputStream();
            if (DEBUG) Log.d(TAG, "Sending stats=" + json.toString());
            os.write(json.toString().getBytes(StandardCharsets.UTF_8));
            os.close();

            final int responseCode = urlConnection.getResponseCode();
            if (DEBUG) Log.d(TAG, "crdroid server response code=" + responseCode);
            final boolean success = responseCode == HttpURLConnection.HTTP_OK;
            if (!success) {
                Log.w(TAG, "failed sending, server returned: " + getResponse(urlConnection));
            }
            return success;
        } finally {
            urlConnection.disconnect();
        }

    }

    private String getResponse(HttpURLConnection httpUrlConnection)
            throws IOException {
        InputStream responseStream = new BufferedInputStream(httpUrlConnection.getErrorStream());

        BufferedReader responseStreamReader = new BufferedReader(
                new InputStreamReader(responseStream));
        String line = "";
        StringBuilder stringBuilder = new StringBuilder();
        while ((line = responseStreamReader.readLine()) != null) {
            stringBuilder.append(line).append("\n");
        }
        responseStreamReader.close();
        responseStream.close();

        return stringBuilder.toString();
    }

}
