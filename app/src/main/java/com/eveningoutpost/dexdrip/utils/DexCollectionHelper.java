package com.eveningoutpost.dexdrip.utils;

import android.app.Activity;
import android.text.InputType;

import com.eveningoutpost.dexdrip.Home;
import com.eveningoutpost.dexdrip.R;
import com.eveningoutpost.dexdrip.models.UserError;
import com.eveningoutpost.dexdrip.plugin.Dialog;
import com.eveningoutpost.dexdrip.services.Ob1G5CollectionService;
import com.eveningoutpost.dexdrip.utilitymodels.CollectionServiceStarter;
import com.eveningoutpost.dexdrip.utilitymodels.Pref;

import static com.eveningoutpost.dexdrip.services.Ob1G5CollectionService.clearDataWhenTransmitterIdEntered;
import static com.eveningoutpost.dexdrip.ui.dialog.QuickSettingsDialogs.textSettingDialog;


/**
 * Created by jamorham on 02/03/2018.
 */

public class DexCollectionHelper {

    private static final String TAG = DexCollectionHelper.class.getSimpleName();


    public static void assistance(final Activity activity, DexCollectionType type) {

        switch (type) {

            // g6 is currently a pseudo type which enables required g6 settings and then sets g5
            case DexcomG6:
                Ob1G5CollectionService.setG6Defaults();

                DexCollectionType.setDexCollectionType(DexCollectionType.DexcomG5);
                // intentional fall thru

            case DexcomG5:
                final String pref = "dex_txid";
                textSettingDialog(activity,
                        pref, activity.getString(R.string.dexcom_transmitter_id),
                        activity.getString(R.string.enter_your_transmitter_id_exactly),
                        DexCollectionType.isG7()
                        ? InputType.TYPE_CLASS_NUMBER // g7 numbers only
                        : InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                        () -> {
                            // InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS does not seem functional here
                            Pref.setString(pref, Pref.getString(pref, "").toUpperCase());
                            if (!Dialog.askIfNeeded(activity, Pref.getString(pref, ""))) {
                                Home.staticRefreshBGCharts();
                            }

                            clearDataWhenTransmitterIdEntered(Pref.getString(pref, ""));

                            CollectionServiceStarter.restartCollectionServiceBackground();
                            LocationHelper.requestLocationForBluetooth(activity);
                        }, true);
                break;

            default:
                UserError.Log.d(TAG, "Default case in assistance - restarting collection service");
                CollectionServiceStarter.restartCollectionServiceBackground();
                break;
        }
    }

}
