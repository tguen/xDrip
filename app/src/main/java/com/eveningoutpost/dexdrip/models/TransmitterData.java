package com.eveningoutpost.dexdrip.models;

import android.provider.BaseColumns;

import com.activeandroid.Model;
import com.activeandroid.annotation.Column;
import com.activeandroid.annotation.Table;
import com.activeandroid.query.Select;
import com.eveningoutpost.dexdrip.utilitymodels.Constants;
import com.google.gson.annotations.Expose;

import java.util.Date;
import java.util.List;
import java.util.UUID;


/**
 * Created by Emma Black on 11/6/14.
 */
@Table(name = "TransmitterData", id = BaseColumns._ID)
public class TransmitterData extends Model {

    @Expose
    @Column(name = "timestamp", index = true)
    public long timestamp;

    // TODO these should be int or long surely
    @Expose
    @Column(name = "raw_data")
    public double raw_data;

    @Expose
    @Column(name = "filtered_data")
    public double filtered_data;

    @Expose
    @Column(name = "sensor_battery_level")
    public int sensor_battery_level;

    @Expose
    @Column(name = "uuid", index = true)
    public String uuid;

    public static synchronized TransmitterData create(int raw_data, int filtered_data, int sensor_battery_level, long timestamp) {
        TransmitterData lastTransmitterData = TransmitterData.last();
        if (lastTransmitterData != null && lastTransmitterData.raw_data == raw_data && Math.abs(lastTransmitterData.timestamp - new Date().getTime()) < (Constants.MINUTE_IN_MS * 2)) { //Stop allowing duplicate data, its bad!
            return null;
        }

        TransmitterData transmitterData = new TransmitterData();
        transmitterData.sensor_battery_level = sensor_battery_level;
        transmitterData.raw_data = raw_data;
        transmitterData.filtered_data = filtered_data;
        transmitterData.timestamp = timestamp;
        transmitterData.uuid = UUID.randomUUID().toString();
        transmitterData.save();
        return transmitterData;
    }

    public static TransmitterData last() {
        return new Select()
                .from(TransmitterData.class)
                .orderBy("_ID desc")
                .executeSingle();
    }

    public static List<TransmitterData> last(int count) {
        return new Select()
                .from(TransmitterData.class)
                .orderBy("_ID desc")
                .limit(count)
                .execute();
    }

    private static double roundRaw(TransmitterData td) {
        return JoH.roundDouble(td.raw_data,3);
    }

    private static double roundFiltered(TransmitterData td) {
        return JoH.roundDouble(td.filtered_data,3);
    }

    public static boolean unchangedRaw() {
        final List<TransmitterData> items = last(3);
        if (items != null && items.size() == 3) {
            return (roundRaw(items.get(0)) == roundRaw(items.get(1))
                    && roundRaw(items.get(0)) == roundRaw(items.get(2))
                    && roundFiltered(items.get(0)) == roundFiltered(items.get(1))
                    && roundFiltered(items.get(0)) == roundFiltered(items.get(2)));
        }
        return false;
    }

}
