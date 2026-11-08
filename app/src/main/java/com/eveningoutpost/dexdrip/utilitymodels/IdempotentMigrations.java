package com.eveningoutpost.dexdrip.utilitymodels;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.eveningoutpost.dexdrip.R;
import com.eveningoutpost.dexdrip.SnoozeActivity;
import com.eveningoutpost.dexdrip.models.APStatus;
import com.eveningoutpost.dexdrip.models.AlertType;
import com.eveningoutpost.dexdrip.models.BgReading;
import com.eveningoutpost.dexdrip.models.JoH;
import com.eveningoutpost.dexdrip.models.Prediction;
import com.eveningoutpost.dexdrip.models.UserNotification;
import com.eveningoutpost.dexdrip.utils.Preferences;

import androidx.preference.PreferenceManager;

import static com.eveningoutpost.dexdrip.utils.Preferences.MAX_GLUCOSE_INPUT;
import static com.eveningoutpost.dexdrip.utils.Preferences.MIN_GLUCOSE_INPUT;


/**
 * Created by Emma Black on 4/15/15.
 */
public class IdempotentMigrations {
    private final Context mContext;
    private final SharedPreferences prefs;

    private final static String TAG = IdempotentMigrations.class.getSimpleName();

    public IdempotentMigrations(Context context) {
        this.mContext = context;
        this.prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
    }

    public void performAll() {
        migrateBGAlerts();
        BgReading.updateDB();
        APStatus.updateDB();
        Prediction.updateDB();
        AlertType.fixUpTable();
        UserNotification.updateDB();
        JoH.clearCache();
        legacySettingsFix();
        settingsFix();
        prefSettingRangeVerification();
    }

    private void migrateBGAlerts() {
        // Migrate away from old style notifications to Tzachis new Alert system
       // AlertType.CreateStaticAlerts(); // jamorham weird problem auto-calibrations
        if(prefs.getBoolean("bg_notifications", true)){
            double highMark = Double.parseDouble(prefs.getString("highValue", "170"))+54; // make default alert not too fatiguing
            double lowMark = Double.parseDouble(prefs.getString("lowValue", "70"));

            boolean doMgdl = (prefs.getString("units", "mgdl").compareTo("mgdl") == 0);

            if(!doMgdl) {
                highMark = highMark * Constants.MMOLL_TO_MGDL;
                lowMark = lowMark * Constants.MMOLL_TO_MGDL;
            }
            boolean bg_sound_in_silent = prefs.getBoolean("bg_sound_in_silent", true);
            String bg_notification_sound = prefs.getString("bg_notification_sound", "default");

            int bg_high_snooze = Integer.parseInt(prefs.getString("bg_snooze",  Integer.toString(SnoozeActivity.getDefaultSnooze(true))));
            int bg_low_snooze = Integer.parseInt(prefs.getString("bg_snooze",  Integer.toString(SnoozeActivity.getDefaultSnooze(false))));


            AlertType.add_alert(null, mContext.getString(R.string.high_alert), true, highMark, true, 1, bg_notification_sound, 0, 0, bg_sound_in_silent, true, bg_high_snooze, true, true);
            AlertType.add_alert(null, mContext.getString(R.string.low_alert), false, lowMark, true, 1, bg_notification_sound, 0, 0, bg_sound_in_silent, true, bg_low_snooze, true, true);
            prefs.edit().putBoolean("bg_notifications", false).apply();
        }
    }

    // Force legacy settings to be at their recommended values
    private static void legacySettingsFix() {
        Pref.setBoolean("ob1_initiate_bonding_flag", true);
        Pref.setBoolean("store_sensor_location", false);
        Pref.setBoolean("using_g6", true);
        Pref.setBoolean("tidepool_new_auth", true);
        Pref.setBoolean("bridge_battery_alerts", false); // Disable Parakeet
        Pref.setString("bridge_battery_alert_level", "30");
        Pref.setBoolean("parakeet_status_alerts", false);
        Pref.setBoolean("parakeet_charge_silent", false);
        Pref.setBoolean("g5_bluetooth_watchdog", true);
        Pref.setBoolean("bluetooth_frequent_reset", false);
        Pref.setBoolean("use_transmiter_pl_bluetooth", false);
        Pref.setBoolean("use_rfduino_bluetooth", false);
        Pref.setBoolean("use_notification_channels", true);
        Pref.setBoolean("run_service_in_foreground", true);
        Pref.setBoolean("notification_channels_grouping", false);
        Pref.setBoolean("use_number_icon_large", false);
        Pref.setBoolean("number_icon_large_arrow", false);
        Pref.setString("bg_notification_sound", "default"); // We need this along with a detector in the playFile method to handle the default mp3 option with SDK 26.

    }

    // Required adjustments/conversions to settings after an update
    private static void settingsFix() {
        final int MAX_BT_WDG = 20; // Bluetooth watchdog timer default (max)

        try { // Use the closest list value for the Bluetooth watchdog timer
            int oldValue = JoH.parseIntWithDefault(Pref.getString("bluetooth_watchdog_timer", Integer.toString(MAX_BT_WDG)), 10, MAX_BT_WDG);
            int roundedValue = Math.round(oldValue / 5.0f) * 5; // Round to the nearest multiple of 5

            // Clamp to valid range (just in case)
            if (roundedValue < 5) roundedValue = 5;
            if (roundedValue > MAX_BT_WDG) roundedValue = MAX_BT_WDG;

            Pref.setString("bluetooth_watchdog_timer", Integer.toString(roundedValue));
        } catch (ClassCastException e) {
            Log.e(TAG, "Converting bluetooth_watchdog_timer to list failed");
        }

    }

    // Correct preference setting values if the values are out of range.
    // Include new preference settings here that represent glucose values.
    private static void prefSettingRangeVerification() {
        Preferences.applyPrefSettingRange("persistent_high_threshold", "170", MIN_GLUCOSE_INPUT, MAX_GLUCOSE_INPUT);
        Preferences.applyPrefSettingRange("forecast_low_threshold", "70", MIN_GLUCOSE_INPUT, MAX_GLUCOSE_INPUT);
    }

}
