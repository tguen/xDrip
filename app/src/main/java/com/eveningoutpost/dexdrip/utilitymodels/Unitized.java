package com.eveningoutpost.dexdrip.utilitymodels;

// jamorham
import java.text.DecimalFormat;


public class Unitized {

    // TODO move static BgGraphBuilder methods here

    public static boolean usingMgDl() {
        return Pref.getString("units", "mgdl").equals("mgdl");
    }

    public static double mmolConvert(double mgdl) {
        return mgdl * Constants.MGDL_TO_MMOLL;
    }

    public static double mgdlConvert(double mmol) {
        return mmol * Constants.MMOLL_TO_MGDL;
    }

    public static double unitized(double value, boolean doMgdl) {
        if (doMgdl) {
            return value;
        } else {
            return mmolConvert(value);
        }
    }


    public static String unitized_string_static(double value) {
        return unitized_string(value, Pref.getString("units", "mgdl").equals("mgdl"));
    }

    public static String unitized_string(double value, boolean doMgdl) {
        final DecimalFormat df = new DecimalFormat("#");
        if (value >= 400) {
            return "HIGH";
        } else if (value >= 40) {
            if (doMgdl) {
                df.setMaximumFractionDigits(0);
                return df.format(value);
            } else {
                df.setMaximumFractionDigits(1);
                //next line ensures mmol/l value is XX.x always.  Required by PebbleWatchSync, and probably not a bad idea.
                df.setMinimumFractionDigits(1);
                return df.format(mmolConvert(value));
            }
        } else if (value > 12) {
            return "LOW";
        } else {
            switch ((int) value) {
                case 0:
                    return "??0";
                case 1:
                    return "?SN";
                case 2:
                    return "??2";
                case 3:
                    return "?NA";
                case 5:
                    return "?NC";
                case 6:
                    return "?CD";
                case 9:
                    return "?AD";
                case 12:
                    return "?RF";
                default:
                    return "???";
            }
        }
    }


    public static String unit(boolean doMgdl) {
        if (doMgdl) {
            return "mg/dl";
        } else {
            return "mmol";
        }
    }

}
