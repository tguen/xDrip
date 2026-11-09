package com.eveningoutpost.dexdrip.utilitymodels;

/**
 * For integration.
 */
public interface Intents {
    String RECEIVER_PERMISSION = "com.eveningoutpost.dexdrip.permissions.RECEIVE_BG_ESTIMATE";

    String ACTION_NEW_BG_ESTIMATE = "com.eveningoutpost.dexdrip.BgEstimate";
    String EXTRA_SENDER = "com.eveningoutpost.dexdrip.Extras.Sender";
    String EXTRA_BG_ESTIMATE = "com.eveningoutpost.dexdrip.Extras.BgEstimate";
    String EXTRA_BG_SLOPE = "com.eveningoutpost.dexdrip.Extras.BgSlope";
    String EXTRA_BG_SLOPE_NAME = "com.eveningoutpost.dexdrip.Extras.BgSlopeName";
    String EXTRA_SENSOR_STARTED_AT = "com.eveningoutpost.dexdrip.Extras.SensorStartedAt";
    String EXTRA_TIMESTAMP = "com.eveningoutpost.dexdrip.Extras.Time";
    String EXTRA_RAW = "com.eveningoutpost.dexdrip.Extras.Raw";
    String EXTRA_NOISE = "com.eveningoutpost.dexdrip.Extras.Noise";
    String EXTRA_NOISE_WARNING = "com.eveningoutpost.dexdrip.Extras.NoiseWarning";
    String EXTRA_NOISE_BLOCK_LEVEL = "com.eveningoutpost.dexdrip.Extras.NoiseBlockLevel";
    String EXTRA_NS_NOISE_LEVEL = "com.eveningoutpost.dexdrip.Extras.NsNoiseLevel";
    String XDRIP_DATA_SOURCE_DESCRIPTION = "com.eveningoutpost.dexdrip.Extras.SourceDesc";
    String XDRIP_DATA_SOURCE_INFO = "com.eveningoutpost.dexdrip.Extras.SourceInfo";
    String XDRIP_VERSION_INFO = "com.eveningoutpost.dexdrip.Extras.VersionInfo";
    String XDRIP_CALIBRATION_INFO = "com.eveningoutpost.dexdrip.Extras.CalibrationInfo";
    String XDRIP_CALIBRATION_PLUGIN = "com.eveningoutpost.dexdrip.Extras.CalibrationPluginInfo";

    String ACTION_REMOTE_CALIBRATION = "com.eveningoutpost.dexdrip.NewCalibration";
    String ACTION_NEW_BG_ESTIMATE_NO_DATA = "com.eveningoutpost.dexdrip.BgEstimateNoData";
    String ACTION_STATUS_UPDATE = "com.eveningoutpost.dexdrip.StatusUpdate";
    String ACTION_SNOOZE = "com.eveningoutpost.dexdrip.Snooze";

    String EXTRA_COLLECTOR_NANOSTATUS = "com.eveningoutpost.dexdrip.Extras.Collector.NanoStatus";

    String EXTRA_DISPLAY_UNITS = "com.eveningoutpost.dexdrip.Extras.Display.Units";

    // Local Broadcasts
    String HOME_STATUS_ACTION = "com.eveningoutpost.dexdrip.HOME_STATUS_ACTION";

}
