package com.eveningoutpost.dexdrip.utils;

import android.app.PendingIntent;
import android.content.Intent;

import com.eveningoutpost.dexdrip.Home;
import com.eveningoutpost.dexdrip.models.JoH;
import com.eveningoutpost.dexdrip.models.UserError;
import com.eveningoutpost.dexdrip.utilitymodels.NotificationChannels;
import com.eveningoutpost.dexdrip.utilitymodels.Pref;
import com.eveningoutpost.dexdrip.xdrip;

import static com.eveningoutpost.dexdrip.models.JoH.cancelNotification;
import static com.eveningoutpost.dexdrip.models.JoH.showNotification;

/**
 * Created by jamorham on 26/01/2017.
 */

public class CheckBridgeBattery {

    private static final String TAG = CheckBridgeBattery.class.getSimpleName();
    private static final int NOTIFICATION_ITEM = 541;
    private static final int repeat_seconds = 1200;
    private static int last_level = -1;
    private static boolean notification_showing = false;
    private static int threshold = 20;


    public static boolean checkBridgeBattery() {

        boolean lowbattery = false;

        if (!Pref.getBooleanDefaultFalse("bridge_battery_alerts")) return false;

        try {
            threshold = Integer.parseInt(Pref.getString("bridge_battery_alert_level", "30"));
        } catch (NumberFormatException e) {
            UserError.Log.e(TAG, "Got error parsing alert level");
        }

        final int this_level = Pref.getInt("bridge_battery", -1);
        UserError.Log.d(TAG, "checkBridgeBattery threshold:" + threshold + " this_level:" + this_level + " last_level:" + last_level);
        if ((this_level > 0) && (threshold > 0)) {
            if ((this_level < threshold) && ((this_level < last_level) || (last_level == -1))) {
                if (JoH.pratelimit("bridge-battery-warning", repeat_seconds)) {
                    notification_showing = true;
                    lowbattery = true;
                    
                    boolean sound = true;
                    boolean vibrate = true;
                    if (Pref.getLong("alerts_disabled_until", 0) > JoH.tsl()) {
                        UserError.Log.d(TAG, "Not playing alert since Notifications are currently disabled!!");
                    	sound = false;
                    	vibrate = false;
                    }
                    
                    final PendingIntent pendingIntent = android.app.PendingIntent.getActivity(xdrip.getAppContext(), 0, new Intent(xdrip.getAppContext(), Home.class), android.app.PendingIntent.FLAG_UPDATE_CURRENT);
                    showNotification("Low bridge battery", "Bridge battery dropped to: " + this_level + "%",
                            pendingIntent, NOTIFICATION_ITEM, NotificationChannels.GENERAL_CHANNEL, sound, vibrate, null, null, null);
                    UserError.Log.uel(TAG, "Bridge battery dropped to: " + this_level + "%");
                }
            } else {
                if (notification_showing) {
                    cancelNotification(NOTIFICATION_ITEM);
                    notification_showing = false;
                }
            }
            last_level = this_level;
        }
        return lowbattery;
    }

}
