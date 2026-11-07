package com.eveningoutpost.dexdrip.utilitymodels;

/**
 * NanoStatus allows access to a static class based status interface
 *
 * It can push to observable fields, ui elements and remote followers
 *
 * Use of dovetailing means runnables can also be attached
 *
 * This allows us to provide dynamic UI updates behind a layer of abstraction for classes and
 * services which have not yet been instantiated or which maintain a static singleton state
 *
 */
import android.text.SpannableString;
import android.util.Log;

import com.eveningoutpost.dexdrip.BuildConfig;
import com.eveningoutpost.dexdrip.g5model.SensorDays;
import com.eveningoutpost.dexdrip.models.UserError;
import com.eveningoutpost.dexdrip.utils.DexCollectionType;

import java.lang.reflect.Method;
import java.util.HashMap;

import androidx.databinding.ObservableField;
import lombok.Setter;
import lombok.val;


public class NanoStatus {

    private static final String TAG = "NanoStatus";
    private static final HashMap<Class<?>, Method> cache = new HashMap<>();
    private static final boolean D = false;

    private final int freqMs;
    private final SpannableString empty = new SpannableString("");
    private volatile boolean running = false;
    private volatile Thread myThread;
    @Setter
    private Runnable doveTail;
    public final ObservableField<String> watch = new ObservableField<>();
    public final ObservableField<SpannableString> color_watch = new ObservableField<>();

    public static String debugStringStatic = null;
    public String debugString = null;

    private static String lastException = "";

    public NanoStatus(final String parameter, final int freqMs) {
        this.freqMs = freqMs;
        if (freqMs > 0) {
            running = true;
            startRefresh();
        }
        if (debugStringStatic != null) {
            debugString = debugStringStatic + " " + parameter;
        }
    }

    public void setRunning(final boolean state) {
        final boolean oldState = running;
        running = state;
        if (state && !oldState) {
            startRefresh();
        }
    }

    private synchronized void startRefresh() {
        UserError.Log.d(TAG, "startRefresh");
        if (myThread != null) {
            try {
                if (D) UserError.Log.d(TAG, "sending interrupt");
                myThread.interrupt();
            } catch (Exception e) {
                if (D) UserError.Log.d(TAG, " interrupt exception " + e);
            }
        }
        myThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (running && !Thread.interrupted()) {
                    try {
                        Thread.sleep(freqMs);
                    } catch (InterruptedException e) {
                        //
                        if (D) UserError.Log.d(TAG, "Sleep interrupted - quiting");
                        return;
                    }
                    if (D) UserError.Log.d(TAG, "Updating");
                    if (doveTail != null) {
                        doveTail.run();
                    }
                }
                if (D) UserError.Log.d(TAG, "Stopping");
            }
        });
        myThread.setPriority(Thread.NORM_PRIORITY - 1);
        myThread.start();

    }


    public static String nanoStatus(final String module) {
        final SpannableString result = nanoStatusColor(module);
        return result != null ? result.toString() : null;
    }

    public static SpannableString nanoStatusColor(final String module) {
        switch (module) {
            case "collector":
                return collectorNano(DexCollectionType.getCollectorServiceClass());
            case "mtp-configure":
                return collectorNano(getClassByName(".utilitymodels.MtpConfigure"));
            case "sensor-expiry":
            case "s-expiry":
                return SensorDays.get().getSpannable();
            default:
                return new SpannableString("Invalid module type");
        }
    }


    static SpannableString collectorNano(final Class<?> service) {
        if (service != null) {
            if (debugStringStatic != null) {
                return new SpannableString(debugStringStatic + " " + service.getSimpleName());
            }
            try {
                try {
                    return (SpannableString) cache.get(service).invoke(null);
                } catch (NullPointerException e) {
                    final Method method = service.getMethod("nanoStatus");
                    cache.put(service, method);
                    return (SpannableString) method.invoke(null);
                }

            } catch (Exception e) {
              val exceptionString = e + " " + service.getSimpleName();
                if (!exceptionString.equals(lastException)) {
                    Log.d(TAG, "reflection exception: " + exceptionString);
                    lastException = exceptionString;
                }
            }
        }
        return null;
    }


    private static Class<?> getClassByName(final String name) {
        try {
            return Class.forName(BuildConfig.APPLICATION_ID + name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

}
