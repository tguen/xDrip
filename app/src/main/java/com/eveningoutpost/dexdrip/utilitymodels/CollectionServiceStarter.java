package com.eveningoutpost.dexdrip.utilitymodels;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.preference.PreferenceManager;

import com.eveningoutpost.dexdrip.models.JoH;
import com.eveningoutpost.dexdrip.models.UserError.Log;
import com.eveningoutpost.dexdrip.services.Ob1G5CollectionService;
import com.eveningoutpost.dexdrip.utils.DexCollectionType;
import com.eveningoutpost.dexdrip.xdrip;


/**
 * Created by Emma Black on 12/22/14.
 */

// TODO everything started here must be capable of being foreground if it is started with compat - really everything should be started with compat!

public class CollectionServiceStarter {

    private Context mContext;
    private static final String TAG = CollectionServiceStarter.class.getSimpleName();

    private static final Object lock = new Object();

    private static volatile boolean stopPending;
    private static volatile boolean startPending;


    private static void queueRestart() {
        Log.d(TAG, "queueRestart called");
        if (!operationInProgress()) {
            Log.d(TAG, "Aquiring lock");
            synchronized (lock) {
                if (!operationInProgress()) {
                    if (JoH.ratelimit("collection-queue-restart", 10)) {
                        Log.d(TAG, "before: " + status());
                        stopPending = true;
                        startPending = true;
                        Log.d(TAG, " after: " + status());
                        automata();
                    } else {
                        Log.d(TAG, "Too fast - queueing cooldown task");
                        Inevitable.task("collect-queue-cooldown", 3000, CollectionServiceStarter::queueRestart);
                    }
                } else {
                    Log.d(TAG, "Went busy during lock acquire: " + status());
                }
            }
        } else {
            Log.d(TAG, "Apparent operation already in progress so calling automata instead");
            automata();
        }
    }

    private static boolean operationInProgress() {
        return startPending || stopPending;
    }

    private static String status() {
        return String.format("Stop Pending: %b  Start Pending: %b", stopPending, startPending);
    }

    private static void automata() {
        Inevitable.task("collect-automata", 200, CollectionServiceStarter::processPending);
    }

    private static void processPending() {
        final PowerManager.WakeLock wl = JoH.getWakeLock("collection-processPending", 60000);
        try {
            synchronized (lock) {
                if (operationInProgress()) {
                    // TODO staticify
                    final CollectionServiceStarter starter = new CollectionServiceStarter(xdrip.getAppContext());
                    if (stopPending) {
                        Log.d(TAG, "processPending: Issuing a stop all");
                        starter.stopAll();
                        stopPending = false;
                    }
                    if (startPending) {
                        Log.d(TAG, "processPending: Issuing a start");
                        starter.start();
                        startPending = false;
                    }
                } else {
                    Log.d(TAG, "Called but nothing pending");
                }
            }
        } finally {
            JoH.releaseWakeLock(wl);
        }
    }


    public static boolean isBTG5(Context context) {

        String collection_method = Pref.getString("dex_collection_method", "BluetoothWixel");
        return collection_method.equals("DexcomG5");
    }

    private static boolean isBTG5(String collection_method) {
        return collection_method.equals("DexcomG5");
    }


    private void stopAll() {
        Log.d(TAG, "stop all");
        stopG5Service();
    }

    private void start(Context context, String collection_method) {
        Log.d(TAG, "start called: " + collection_method);
        this.mContext = context;
        xdrip.checkAppContext(context);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.mContext);

        if (isBTG5(collection_method)) {
            Log.d(TAG, "Starting G5 collector");
            startBtG5Service();
        } else {
            if (DexCollectionType.hasBluetooth() || DexCollectionType.isNewerCollector()) {
                Log.d(TAG, "Starting service based on collector lookup");
                startServiceCompat(new Intent(context, DexCollectionType.getCollectorServiceClass()));
            }
        }
        Log.d(TAG, collection_method);
    }

    private void start() {
        start(xdrip.getAppContext(), Pref.getString("dex_collection_method", "BluetoothWixel"));
    }

    // private constructer, use static methods to start
    private CollectionServiceStarter(Context context) {
        if (context == null) context = xdrip.getAppContext();
        this.mContext = context;
    }


    public static void restartCollectionServiceBackground() {
        Log.d(TAG, "restartCollectionServiceBackground Restart no args");
        Inevitable.task("restart-collection-service", 500, CollectionServiceStarter::queueRestart);
    }


    // TODO refactor all calls to this to use the background method above and make this private
    public static void restartCollectionService(Context context) {
        Log.d(TAG, "Restart with context");
        restartCollectionServiceBackground();
    }

    private void startBtG5Service() {
        Log.d(TAG, "starting G5 service");
	    Ob1G5CollectionService.keep_running = true;
	    startServiceCompat(new Intent(this.mContext, Ob1G5CollectionService.class));
    }

    private void stopG5Service() {
        Log.d(TAG, "stopping G5  services");
        Ob1G5CollectionService.keep_running = false; // ensure zombie stays down
        this.mContext.stopService(new Intent(this.mContext, Ob1G5CollectionService.class));
        Ob1G5CollectionService.resetSomeInternalState();
    }

    @SuppressWarnings("ConstantConditions")
    private void startServiceCompat(final Intent intent) {
        try {
            Log.d(TAG, String.format("Starting foreground service: %s", intent.getComponent().getClassName()));
            mContext.startForegroundService(intent);
        } catch (Exception e) {
            // If foreground fails (e.g. app is already in foreground), fall back to standard start
            mContext.startService(intent);
        }
    }

}
