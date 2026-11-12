package com.eveningoutpost.dexdrip.utilitymodels;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;


/**
 * Created by jamorham on 04/09/2017.
 */

public class InstalledApps {
    private static final String TAG = "InstalledApps";

    public static boolean checkPackageExists(Context context, String packageName) {
        try {
            final PackageManager pm = context.getPackageManager();
            final PackageInfo pi = pm.getPackageInfo(packageName, 0);
            return pi.packageName.equals(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        } catch (Exception e) {
            Log.wtf(TAG, "Exception trying to determine packages! " + e);
            return false;
        }
    }
}
