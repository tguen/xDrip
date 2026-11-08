package com.eveningoutpost.dexdrip.profileeditor;

import android.util.Log;

import com.eveningoutpost.dexdrip.BaseAppCompatActivity;
import com.eveningoutpost.dexdrip.Home;
import com.eveningoutpost.dexdrip.importedlibraries.dexcom.Dex_Constants;
import com.eveningoutpost.dexdrip.models.UserError;
import com.eveningoutpost.dexdrip.utilitymodels.Pref;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * Created by jamorham on 21/06/2016.
 */

public class ProfileEditor extends BaseAppCompatActivity {

    private static final String TAG = "jamorhamprofile";

    private static double last_conversion = 0;

    public static final int MINS_PER_DAY = 1440; // 00:00
    private static final int END_OF_DAY = MINS_PER_DAY - 1; // 23:59


    private static double adjustmentFactor = 1;

    // convert between mg/dl or mmol when we change preferences
    public static void convertData(double multiplier) {
        if (last_conversion == multiplier) return; // prevent repeated adjustments
        last_conversion = multiplier;
        final List<ProfileItem> mydata = ProfileEditor.loadData(false);
//        for (ProfileItem item : mydata) {
//            item.carb_ratio = (double) (Math.round(item.carb_ratio * 10)) / 10; // round to nearest .1g
//            item.sensitivity = (double) (Math.round(item.sensitivity * 10 * multiplier)) / 10;
//        }
        final Gson gson = new GsonBuilder()
                .excludeFieldsWithoutExposeAnnotation()
                .serializeSpecialFloatingPointValues()
                .create();
        final String data = gson.toJson(mydata);

        Pref.setString("saved_profile_list_json", data);
        Pref.setString("saved_profile_list_json_working", "");
        UserError.Log.uel(TAG, "Converted Profile data with multiplier: " + ((multiplier == Dex_Constants.MG_DL_TO_MMOL_L) ? " to mmol/l" : "to mg/dl"));
    }

    public static List<ProfileItem> loadData(boolean buttons) {
        final List<ProfileItem> myprofileItemList = new ArrayList<>();
        String data = Pref.getString("saved_profile_list_json_working", "");
        if (data.isEmpty()) {
            data = Pref.getString("saved_profile_list_json", "");

            Log.d(TAG, "Loaded real data");
        } else {
            Log.d(TAG, "Loaded working data");
        }

        ProfileItem[] restored = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create().fromJson(data, ProfileItem[].class);
        if (restored != null) {
            // process adjustment factor
//            for (ProfileItem item : restored) {
//                item.carb_ratio = item.carb_ratio * adjustmentFactor;
//                item.sensitivity = item.sensitivity / adjustmentFactor;
//            }
            Collections.addAll(myprofileItemList, restored);
        }
        if (myprofileItemList.isEmpty()) {
            try {
                Log.d(TAG,"Creating default profile entries: sens default: "+ Pref.getString("profile_insulin_sensitivity_default", "0.1"));
                ProfileItem item = new ProfileItem(0, END_OF_DAY, Double.parseDouble(Pref.getString("profile_carb_ratio_default", "10")),
                        Double.parseDouble(Pref.getString("profile_insulin_sensitivity_default", "0.1")));
                myprofileItemList.add(item);
            } catch (Exception e) {
                Home.toaststatic("Problem with default insulin parameters");
            }
        }
        return myprofileItemList;
    }

}
