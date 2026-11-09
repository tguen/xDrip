package com.eveningoutpost.dexdrip.ui;

/**
 * Created by jamorham on 20/10/2017.
 *
 * Initial shelf implementation for Home
 *
 */

public class HomeShelfImpl extends BaseShelf {

    public HomeShelfImpl() {
        this.PREFS_PREFIX = "home-shelf-";
        map.put("chart_preview", "Chart Preview");
        map.put("collector_nano_status", "Collector Status");
        map.put("sensor_expiry", "Sensor Expiry");
        defaults.put("chart_preview", true);
        defaults.put("collector_nano_status", true);
        defaults.put("sensor_expiry", true);
        populate();
    }

}
