package com.eveningoutpost.dexdrip.services;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

import com.eveningoutpost.dexdrip.models.BgReading;
import com.eveningoutpost.dexdrip.models.JoH;
import com.eveningoutpost.dexdrip.models.Treatments;
import com.eveningoutpost.dexdrip.models.UserError;
import com.eveningoutpost.dexdrip.models.UserError.Log;
import com.eveningoutpost.dexdrip.utilitymodels.BgSendQueue;
import com.eveningoutpost.dexdrip.utilitymodels.CalibrationSendQueue;
import com.eveningoutpost.dexdrip.utilitymodels.IncompatibleApps;
import com.eveningoutpost.dexdrip.utilitymodels.Pref;
import com.eveningoutpost.dexdrip.utilitymodels.SettingsValidation;
import com.eveningoutpost.dexdrip.utils.DatabaseUtil;
import com.eveningoutpost.dexdrip.xdrip;


public class DailyIntentService extends IntentService {
    private final static String TAG = DailyIntentService.class.getSimpleName();

    public DailyIntentService() {
        super("DailyIntentService");
    }

    // TODO this used to be an IntentService but that is being depreciated

    @Override
    protected void onHandleIntent(Intent intent) {
        UserError.Log.wtf(TAG, "CALLED VIA INTENT - cancelling");
        cancelSelf();
    }

    // if we have alarm manager hangovers from previous scheduling methodology then cancel it
    private void cancelSelf() {
        try {
            final PendingIntent pi = PendingIntent.getService(xdrip.getAppContext(), 0, new Intent(this, DailyIntentService.class), PendingIntent.FLAG_UPDATE_CURRENT);
            final AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            am.cancel(pi);
        } catch (Exception e) {
            UserError.Log.wtf(TAG, "Crash in cancelSelf() " + e);
        }
    }

    public static synchronized void work() {
        final PowerManager.WakeLock wl = JoH.getWakeLock("DailyIntentService", 120000);
        try {
            UserError.Log.ueh(TAG, "DailyIntent Service work called");
            if (JoH.pratelimit("daily-intent-service", 60000)) {
                Log.i(TAG, "DailyIntentService onHandleIntent Starting");
                Long start = JoH.tsl();

                // @TecMunky -- save database before pruning - allows daily capture of database
                if (Pref.getBooleanDefaultFalse("save_db_ondemand")) {
                    try {
                        String export = DatabaseUtil.saveSql(xdrip.getAppContext(), "daily");
                    } catch (Exception e) {
                        Log.e(TAG, "DailyIntentService exception on Daily Save Database - ", e);
                    }
                }

                // prune old database records
                try {
                    UserError.cleanup();
                } catch (Exception e) {
                    Log.e(TAG, "DailyIntentService exception on UserError ", e);
                }
                try {
                    BgSendQueue.cleanQueue(); // no longer used

                } catch (Exception e) {
                    Log.d(TAG, "DailyIntentService exception on BgSendQueue " + e);
                }
                try {
                    CalibrationSendQueue.cleanQueue();
                } catch (Exception e) {
                    Log.d(TAG, "DailyIntentService exception on CalibrationSendQueue " + e);
                }

                try {
                    final int bg_retention_days = Pref.getStringToInt("retention_days_bg_reading", 180);
                    if (bg_retention_days > 0) {
                        BgReading.cleanup(bg_retention_days);
                        try {
                            Treatments.cleanup(bg_retention_days);
                        } catch (Exception e) {
                            Log.e(TAG, "Exception cleaning up treatment data " + e);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "DailyIntentService exception on BgReadings cleanup ", e);
                }

                try {
                    IncompatibleApps.notifyAboutIncompatibleApps();
                } catch (Exception e) {
                    //
                }

                try {
                    SettingsValidation.notifyAboutInadvisableSettings();
                } catch (Exception e) {
                    Log.e(TAG, "Exception in SettingsValidation: " + e);
                }

                Log.i(TAG, "DailyIntentService onHandleIntent exiting after " + ((JoH.tsl() - start) / 1000) + " seconds");
                //} else {
                // Log.e(TAG, "DailyIntentService exceeding rate limit");
            }
        } finally {
            JoH.releaseWakeLock(wl);
        }
    }

}
