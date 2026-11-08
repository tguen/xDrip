package com.eveningoutpost.dexdrip;

import android.content.Context;
import android.content.Intent;

import com.eveningoutpost.dexdrip.g5model.FirmwareCapability;
import com.eveningoutpost.dexdrip.models.BgReading;
import com.eveningoutpost.dexdrip.models.Sensor;
import com.eveningoutpost.dexdrip.services.Ob1G5CollectionService;
import com.eveningoutpost.dexdrip.tables.BgReadingTable;
import com.eveningoutpost.dexdrip.utilitymodels.Experience;
import com.eveningoutpost.dexdrip.utils.DexCollectionType;
import com.eveningoutpost.dexdrip.utils.Preferences;

import java.util.ArrayList;
import java.util.List;

import static com.eveningoutpost.dexdrip.services.Ob1G5CollectionService.getTransmitterID;


/**
 * Created by Emma Black on 11/5/14.
 */
public class NavDrawerBuilder {
	public final List<Intent> nav_drawer_intents = new ArrayList<>();
    public final List<String> nav_drawer_options = new ArrayList<>();

    public NavDrawerBuilder(final Context context) {
        final DexCollectionType collector = DexCollectionType.getDexCollectionType();

        addOption(context, R.string.home_screen, Home.class);

//        if (is_active_sensor && last_two_calibrations != null && !last_two_calibrations.isEmpty()) {
//            addOption(context, R.string.calibration_graph, CalibrationGraph.class);
//        }

        addOption(context, R.string.bg_data_table, BgReadingTable.class);
//        addOption(context, R.string.calibration_data_table, CalibrationDataTable.class);

        if (!collector.canNotStartStopOrCal()) {
            // Only if the collector can start/stop sensor and submit calibrations (is not passive)
            if (Sensor.isActive()) {
	            final List<BgReading> last_two_bgReadings = BgReading.latestUnCalculated(2);

	            if (last_two_bgReadings.size() > 1 || Ob1G5CollectionService.isG5WantingCalibration()) {
                    /* isG5WantingInitialCalibration probably never true
                    final List<Calibration> last_two_calibrations = Calibration.latestValid(2);

                    if ((last_two_calibrations.size() > 1) && !Ob1G5CollectionService.isG5WantingInitialCalibration()) { // After two successful initial calibrations
                        // TODO tighten this time limit
                        if (BgReading.last30Minutes().size() >= 2) {
                            long time_now = JoH.tsl();
                            if ((time_now - last_two_calibrations.get(0).timestamp < (1000 * 60 * 60))
                                    && !Ob1G5CollectionService.isG5WantingCalibration()) { // Put steps in place to discourage over calibration
                                addOption(context, R.string.override_calibration, CalibrationOverride.class);
                            } else { // G5, old G6, or Firefly in no-code mode, after initial calibration and long enough after previous calibration
                                addOption(context, R.string.add_calibration, AddCalibration.class);
                            }
                        } else {  // G5, old G6 or Firefly in no-code mode, not long after a calibration
                            addOption(context, R.string.cannot_calibrate_right_now, Home.class);
                        }
                    } else */ {
                        if (BgReading.isDataSuitableForDoubleCalibration() || Ob1G5CollectionService.isG5WantingInitialCalibration()) {
                            if ((FirmwareCapability.isTransmitterRawIncapable(getTransmitterID()) && last_two_bgReadings.size() > 1) || FirmwareCapability.isDeviceAltOrAlt2OrAlt3(getTransmitterID()) ) {
                                //A Firefly G6 after third reading or a G7
                                addOption(context, R.string.add_calibration, AddCalibration.class);
                            } else {
                                // G5 or non-Firefly G6 or Firefly G6 in no-code mode, after warm-up before initial calibration
                                addOption(context, R.string.initial_calibration, DoubleCalibrationActivity.class);
                            }
                        }
                    }
                }
            }
        }

        if (Experience.gotData()) {
            addOption(context, R.string.snooze_alert, SnoozeActivity.class);
            addOption(context, R.string.history, BGHistory.class);
        }

        addOption(context, R.string.level_alerts, AlertList.class);
        addOption(context, R.string.event_logs, EventLogActivity.class);
        addOption(context, R.string.settings, Preferences.class);
    }

    private void addOption(final Context c, int str, Class<?> cls) {
        this.nav_drawer_options.add(c.getString(str));
        this.nav_drawer_intents.add(new Intent(c, cls));
    }
}
