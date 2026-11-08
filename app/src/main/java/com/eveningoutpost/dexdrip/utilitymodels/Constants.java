package com.eveningoutpost.dexdrip.utilitymodels;

/**
 * Various constants
 */
public class Constants {
    // Molar mass of glucose (C6H12O6) = 180.1559 g/mol
    // Source: NIST Chemistry WebBook, SRD 69 (https://webbook.nist.gov/cgi/cbook.cgi?ID=C50997)
    // Conversion factor: 1 mmol/L = 18.01559 mg/dL
    public static final double MMOLL_TO_MGDL = 18.01559; //
    public static final double MGDL_TO_MMOLL = 1 / MMOLL_TO_MGDL;


    public static final int SECOND_IN_MS = 1_000;
    public static final int MINUTE_IN_MS = 60_000;
    public static final int HOUR_IN_MS = 3_600_000;
    public static final long DAY_IN_MS = 86_400_000;
    public static final long WEEK_IN_MS = DAY_IN_MS * 7;
    public static final long MONTH_IN_MS = DAY_IN_MS * 30;

    public static final double LIBRE_MULTIPLIER = 117.64705; // to match (raw/8.5)*1000

    /* Configuration parameters */

    public static final long STALE_CALIBRATION_CUT_OFF = Constants.MINUTE_IN_MS * 21;

    /* Notification IDs */
    public static final int FINAL_VISIBILITY_ID = 785877617;

    public static final int DEX_COLLECTION_SERVICE_RETRY_ID = 1002;
    public static final int DEX_COLLECTION_SERVICE_FAILOVER_ID = 1003;
    public static final int SYNC_QUEUE_RETRY_ID = 1004;
    public static final int NUMBER_TEXT_TEST_ID = 1005;
    public static final int MISSED_READING_SERVICE_ID = 1006;
    public static final int G5_CALIBRATION_REQUEST = 1007;
    public static final int G5_CALIBRATION_REJECT = 1008;
    public static final int G5_START_REJECT = 1009;
    public static final int G5_SENSOR_ERROR = 1010;
    public static final int G5_SENSOR_FAILED = 1011;
    public static final int G5_SENSOR_STARTED = 1012;
    public static final int G5_SENSOR_RESTARTED = 1013;
    public static final int G6_DEFAULTS_MESSAGE = 1014;

    public static final int ZXING_CAM_REQ_CODE = 49374;
    public static final int ZXING_FILE_REQ_CODE = 49375; // This is created by just incrementing the existing camera scan code from the zxing package
    public static final int SENSORY_EXPIRY_NOTIFICATION_ID = 2003;
    public static final int OUT_OF_RANGE_GLUCOSE_ENTRY_ID = 2004; // Preference setting out of range

    public static final int DEX_BASE_ID = 6400;

    public static final int MAX_READINGS_PER_HOUR = 60; // Upper bound on readings per hour xDrip may receive

    // Sound
    public static final float MIN_ALERT_VOLUME = 0.4f; // The lowest volume xDrip can use for an alert.
    public static final float MIN_ALARM_VOLUME = 0.6f; // The lowest volume xDrip can use for a critical (persistent high or missed reading) alarm.

}
