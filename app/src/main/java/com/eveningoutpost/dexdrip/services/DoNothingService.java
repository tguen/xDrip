package com.eveningoutpost.dexdrip.services;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;

import com.eveningoutpost.dexdrip.models.JoH;
import com.eveningoutpost.dexdrip.models.UserError;
import com.eveningoutpost.dexdrip.utilitymodels.ForegroundServiceStarter;

import androidx.preference.PreferenceManager;
import lombok.val;


public class DoNothingService extends Service {
    private final static String TAG = DoNothingService.class.getSimpleName();
    private DoNothingService dexCollectionService;
    private SharedPreferences prefs;
    private ForegroundServiceStarter foregroundServiceStarter;
    public SharedPreferences.OnSharedPreferenceChangeListener prefListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            if (key.compareTo("run_service_in_foreground") == 0) {
                UserError.Log.d("FOREGROUND", "run_service_in_foreground changed!");
                if (prefs.getBoolean("run_service_in_foreground", false)) {
                    foregroundServiceStarter = new ForegroundServiceStarter(getApplicationContext(), dexCollectionService);
                    foregroundServiceStarter.start();
                    UserError.Log.d(TAG, "Moving to foreground");
                } else {
                    dexCollectionService.stopForeground(true);
                    UserError.Log.d(TAG, "Removing from foreground");
                }
            }
        }
    };

    private static long nextWakeUpTime = -1;
    private static long wake_time_difference = 0;
    private static long max_wake_time_difference = 0;
    private static int wakeUpErrors = 0;
    private static String lastState = "Not running";


    public DoNothingService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        foregroundServiceStarter = new ForegroundServiceStarter(getApplicationContext(), this);
        foregroundServiceStarter.start();
        dexCollectionService = this;
        prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        listenForChangeInSettings();
        UserError.Log.i(TAG, "onCreate: STARTING SERVICE");
    }

    private static final long TOLERABLE_JITTER = 300000;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        val wl = JoH.getWakeLock("donothing-follower", 60000);
        lastState = "Trying to start " + JoH.hourMinuteString();

	    if (nextWakeUpTime > 0) {
            wake_time_difference = JoH.tsl() - nextWakeUpTime;
            if (wake_time_difference > TOLERABLE_JITTER) {
                UserError.Log.e(TAG, "Slow Wake up! time difference in ms: " + wake_time_difference);
                wakeUpErrors = wakeUpErrors + 3;
                max_wake_time_difference = Math.max(max_wake_time_difference, wake_time_difference);

            } else {
                if (wakeUpErrors > 0) wakeUpErrors--;
            }
        }

        {
            stopSelf();
            JoH.releaseWakeLock(wl);
            return START_NOT_STICKY;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        UserError.Log.d(TAG, "onDestroy entered");
        foregroundServiceStarter.stop();
        UserError.Log.i(TAG, "SERVICE STOPPED");
        lastState = "Stopped " + JoH.hourMinuteString();
    }

    public void listenForChangeInSettings() {
        prefs.registerOnSharedPreferenceChangeListener(prefListener);
    }

}
