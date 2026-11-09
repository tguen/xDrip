package com.eveningoutpost.dexdrip.importedlibraries.dexcom;

// This code and this particular library are from the NightScout android uploader
// Check them out here: https://github.com/nightscout/android-uploader
// Some of this code may have been modified for use in this project

public class Dex_Constants {

    public final static float MG_DL_TO_MMOL_L = 0.05556f;

    public enum TREND_ARROW_VALUES {
        NONE(0),
        DOUBLE_UP(1,"\u21C8", "DoubleUp", 40d),
        SINGLE_UP(2,"\u2191", "SingleUp", 3.5d),
        UP_45(3,"\u2197", "FortyFiveUp", 2d),
        FLAT(4,"\u2192", "Flat", 1d),
        DOWN_45(5,"\u2198", "FortyFiveDown", -1d),
        SINGLE_DOWN(6,"\u2193", "SingleDown", -2d),
        DOUBLE_DOWN(7,"\u21CA", "DoubleDown", -3.5d),
        NOT_COMPUTABLE(8, "", "NotComputable"),
        OUT_OF_RANGE(9, "", "RateOutOfRange");

        private String arrowSymbol;
        private String trendName;
        private int myID;
        private Double threshold;

        TREND_ARROW_VALUES(int id, String symbol, String name) {
            this.myID = id;
            this.arrowSymbol = symbol;
            this.trendName = name;
        }

        TREND_ARROW_VALUES(int id, String symbol, String name, Double threshold) {
            this.myID = id;
            this.arrowSymbol = symbol;
            this.trendName = name;
            this.threshold = threshold;
        }

        TREND_ARROW_VALUES(int id) {
            this(id,null, null, null);
        }

        public String Symbol() {
            if (arrowSymbol == null) {
                return "\u2194\uFE0E";
            } else {
                return arrowSymbol + "\uFE0E";
            }
        }

        public String trendName() {
            return this.trendName;
        }

        public String friendlyTrendName() {
            if (trendName == null) {
                return name().replace("_", " ");
            } else {
                return trendName;
            }
        }

        public int getID() {
            return myID;
        }

        public static TREND_ARROW_VALUES getTrend(double value) {
            TREND_ARROW_VALUES finalTrend = NONE;
            for (TREND_ARROW_VALUES trend : values()) {
                if (trend.threshold == null)
                    continue;

                if (value > trend.threshold)
                    return finalTrend;
                else
                    finalTrend = trend;
            }
            return finalTrend;
        }

    }

}
