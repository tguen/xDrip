package com.eveningoutpost.dexdrip.services;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

import com.eveningoutpost.dexdrip.models.JoH;
import com.eveningoutpost.dexdrip.models.UserError;
import com.eveningoutpost.dexdrip.models.UserError.Log;
import com.eveningoutpost.dexdrip.utilitymodels.SettingsValidation;
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

                // prune old database records

                try {
                    UserError.cleanup();
                } catch (Exception e) {
                    Log.e(TAG, "DailyIntentService exception on UserError ", e);
                }

                try {
                    SettingsValidation.notifyAboutInadvisableSettings();
                } catch (Exception e) {
                    Log.e(TAG, "Exception in SettingsValidation: " + e);
                }

                Log.i(TAG, "DailyIntentService onHandleIntent exiting after " + ((JoH.tsl() - start) / 1000) + " seconds");
            }
        } finally {
            JoH.releaseWakeLock(wl);
        }
    }

}
