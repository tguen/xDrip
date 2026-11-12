package com.eveningoutpost.dexdrip;

import com.eveningoutpost.dexdrip.models.BgReading;
import com.eveningoutpost.dexdrip.models.JoH;
import com.eveningoutpost.dexdrip.models.UserError;
import com.eveningoutpost.dexdrip.receiver.InfoContentProvider;
import com.eveningoutpost.dexdrip.services.broadcastservice.BroadcastEntry;
import com.eveningoutpost.dexdrip.ui.LockScreenWallPaper;
import com.eveningoutpost.dexdrip.utilitymodels.Notifications;

import lombok.val;

import static com.eveningoutpost.dexdrip.utilitymodels.Constants.SECOND_IN_MS;


/**
 * Created by jamorham on 01/01/2018.
 * <p>
 * Handle triggering data updates on enabled modules
 */

public class NewDataObserver {

    private static final String TAG = "NewDataObserver";

    private static final long ALLOWED_FUTURE_OFFSET = 20 * SECOND_IN_MS;

    // TODO after restructuring so that the triggering is organized by data type,
    // TODO move appropriate functions in to their responsible classes

    // when we receive new glucose reading we want to propagate
    public static void newBgReading(BgReading bgReading, boolean is_follower) {
        fixFutureTime(bgReading);

        sendToBroadcastService();
        Notifications.start();
        InfoContentProvider.ping("bg");
        LockScreenWallPaper.setIfEnabled();
    }

    private static void fixFutureTime(final BgReading bgReading) {
        if (bgReading == null) return;
        val till = JoH.msTill(bgReading.timestamp);
        if (till > 0) {
            if (till < ALLOWED_FUTURE_OFFSET) {
                UserError.Log.d(TAG, "BgReading is in the future by " + (till / 1000) + "s - timestamp adjusted");
                bgReading.timestamp = JoH.tsl(); // TODO save??
            } else {
                UserError.Log.wtf(TAG, "BgReading is too far in the future the future by " + (till / 1000) + "s - skipping");
                // TODO actually skip??
            }
        }
    }

    private static void sendToBroadcastService() {
        BroadcastEntry.sendLatestBG();
    }
}
