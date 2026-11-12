package com.eveningoutpost.dexdrip.models;

/**
 * Created by jamorham on 31/12/15.
 */
import android.content.Context;
import android.provider.BaseColumns;
import android.util.Pair;

import com.activeandroid.Model;
import com.activeandroid.annotation.Column;
import com.activeandroid.annotation.Table;
import com.activeandroid.query.Delete;
import com.activeandroid.query.Select;
import com.activeandroid.util.SQLiteUtils;
import com.eveningoutpost.dexdrip.Home;
import com.eveningoutpost.dexdrip.R;
import com.eveningoutpost.dexdrip.alert.SensorExpiry;
import com.eveningoutpost.dexdrip.g5model.DexSessionKeeper;
import com.eveningoutpost.dexdrip.models.UserError.Log;
import com.eveningoutpost.dexdrip.utilitymodels.Constants;
import com.eveningoutpost.dexdrip.utilitymodels.UndoRedo;
import com.eveningoutpost.dexdrip.xdrip;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.Nullable;
import lombok.val;

import static com.eveningoutpost.dexdrip.models.JoH.msSince;
import static com.eveningoutpost.dexdrip.utilitymodels.Constants.HOUR_IN_MS;
import static com.eveningoutpost.dexdrip.utilitymodels.Constants.MINUTE_IN_MS;

// TODO Switchable Carb models
// TODO Linear array timeline optimization

@Table(name = "Treatments", id = BaseColumns._ID)
public class Treatments extends Model {
    private static final String TAG = "jamorham " + Treatments.class.getSimpleName();

    public static final String SENSOR_START_EVENT_TYPE = "Sensor Start";
    public static final String SENSOR_STOP_EVENT_TYPE = "Sensor Stop";
    private static final String DEFAULT_EVENT_TYPE = "<none>";

    public final static String XDRIP_TAG = "xdrip";

    //public static double activityMultipler = 8.4; // somewhere between 8.2 and 8.8
    private static Treatments lastCarbs;
    private static boolean patched = false;

    @Expose
    @Column(name = "timestamp", index = true)
    public long timestamp;
    @Expose
    @Column(name = "eventType")
    public String eventType;
    @Expose
    @Column(name = "enteredBy")
    public String enteredBy;
    @Expose
    @Column(name = "notes")
    public String notes;
    @Expose
    @Column(name = "uuid", unique = true, onUniqueConflicts = Column.ConflictAction.IGNORE)
    public String uuid;
    @Expose
    @Column(name = "carbs")
    public double carbs;
    @Expose
    @Column(name = "insulin")
    public double insulin;
    @Expose
    @Column(name = "insulinJSON")
    public String insulinJSON;
    @Expose
    @Column(name = "created_at")
    public String created_at;

    private boolean hasInsulinInjections() {
        return false;
    }

    public boolean isBasalOnly() {
        return false;
    }

    public void setInsulinJSON(String json) {
        if ((json == null) || json.isEmpty())
            json = "[]";
        try {
            insulinJSON = json; // set json only if we didn't get exception processing it
        } catch (Exception e) {
            UserError.Log.e(TAG, "Got exception in setInsulinJson: " + e + " for " + json);
        }
    }

    public Treatments()
    {
        eventType = DEFAULT_EVENT_TYPE;
        carbs = 0;
        insulin = 0;
        //setInsulinInjections(null);
    }

    public static synchronized Treatments create(final double carbs, final double insulin, long timestamp) {
        return create(carbs, insulin, timestamp, null);
    }

    public static synchronized Treatments create(final double carbs, final double insulinSum, final long timestamp, final String suggested_uuid) {
        final long future_seconds = (timestamp - JoH.tsl()) / 1000;
        // if treatment more than 1 hour in the future
        if (future_seconds > (60 * 60)) {
            JoH.static_toast_long(xdrip.gs(R.string.toast_treatment_more_than_one_hour_in_the_future));
            UserError.Log.e(TAG, "Refusing to create a treatment more than 1 hour in the future!");
            return null;
        }
        // if treatment more than 3 minutes in the future
        if ((future_seconds > (3 * 60)) && (future_seconds < 86400) && ((carbs > 0) || (insulinSum > 0))) {
            final Context context = xdrip.getAppContext();
            JoH.scheduleNotification(context, "Treatment Reminder", "@" + JoH.hourMinuteString(timestamp) + " : "
                    + carbs + " g " + context.getString(R.string.carbs) + " / "
                    + insulinSum + " " + context.getString(R.string.units), (int) future_seconds, 34026);
        }

        // TODO sanity check values
        Log.d(TAG, "Creating treatment: " +
                "Insulin: " + insulinSum + " / " +
                "Carbs: " + carbs +
                (suggested_uuid != null && !suggested_uuid.isEmpty()
                        ? " " + "uuid: " + suggested_uuid
                        : ""));

        if ((carbs == 0) && (insulinSum == 0)) return null;

//        if (timestamp == 0) {
//            timestamp = new Date().getTime();
//        }

        final Treatments treatment = new Treatments();
            treatment.enteredBy = XDRIP_TAG;

        treatment.carbs = carbs;
        treatment.insulin = insulinSum;
        treatment.timestamp = timestamp;
        treatment.created_at = DateUtil.toISOString(timestamp);
        treatment.uuid = suggested_uuid != null ? suggested_uuid : UUID.randomUUID().toString();
        treatment.save();

        pushTreatmentSync(treatment);
        UndoRedo.addUndoTreatment(treatment.uuid);
        return treatment;
    }

    // Note
    public static synchronized Treatments create_note(String note, long timestamp) {
        return create_note(note, timestamp, -1, null);
    }

    public static synchronized Treatments create_note(String note, long timestamp, double position) {
        return create_note(note, timestamp, position, null);
    }

    public static synchronized Treatments create_note(String note, long timestamp, double position, String suggested_uuid) {
        // TODO sanity check values
        Log.d(TAG, "Creating treatment note: " + note);

        if (timestamp == 0) {
            timestamp = new Date().getTime();
        }

        if ((note == null || (note.length() == 0))) {
            Log.i(TAG, "Empty treatment note - not saving");
            return null;
        }

        boolean is_new = false;
        // find treatment

        Treatments treatment = byTimestamp(timestamp, MINUTE_IN_MS * 5);
        // if unknown create
        if (treatment == null) {
            treatment = new Treatments();
            Log.d(TAG, "Creating new treatment entry for note");
            is_new = true;

            treatment.notes = note;
            treatment.timestamp = timestamp;
            treatment.created_at = DateUtil.toISOString(timestamp);
            treatment.uuid = suggested_uuid != null ? suggested_uuid : UUID.randomUUID().toString();

        } else {
            if (treatment.notes == null) treatment.notes = "";
            Log.d(TAG, "Found existing treatment for note: " + treatment.uuid + ((suggested_uuid != null) ? " vs suggested: " + suggested_uuid : "") + " distance:" + Long.toString(timestamp - treatment.timestamp) + " " + treatment.notes);
            if (treatment.notes.contains(note)) {
                Log.d(TAG, "Suggested note update already present - skipping");
                return null;
            }
            // append existing note or treatment
            if (treatment.notes.length() > 0) treatment.notes += " \u2192 ";
            treatment.notes += note;
            Log.d(TAG, "Final notes: " + treatment.notes);
        }
        //    if ((treatment.enteredBy == null) || (!treatment.enteredBy.contains(NightscoutUploader.VIA_NIGHTSCOUT_TAG))) {
        // tag it as from xdrip if it isn't being synced from nightscout right now to allow local updates to nightscout sourced notes
        if (suggested_uuid == null) {
            if (position > 0) {
                treatment.enteredBy = XDRIP_TAG + " pos:" + JoH.qs(position, 2);
            } else {
                treatment.enteredBy = XDRIP_TAG;
            }
        }

        treatment.save();

        pushTreatmentSync(treatment, is_new, suggested_uuid);
        if (is_new) UndoRedo.addUndoTreatment(treatment.uuid);

        return treatment;
    }

    static void createForTest(long timestamp, double insulin) {
        fixUpTable();
        val treatment = new Treatments();
        treatment.notes = "test";
        treatment.timestamp = timestamp;
        treatment.created_at = DateUtil.toISOString(timestamp);
        treatment.uuid = UUID.randomUUID().toString();
        treatment.insulin = insulin;
        treatment.save();
    }

    /**
     * Returns a newly created treatment entry in the database for Sensor Start,
     * and pushes the new treatment to followers.
     * @param timestamp is optional, defaults to right now
     * @param notes is optional
     */
    public static synchronized Treatments sensorStart(@Nullable Long timestamp, @Nullable String notes) {
        if (timestamp == null || timestamp == 0) {
            timestamp = new Date().getTime();
        }

        final Treatments treatment = new Treatments();
        treatment.enteredBy = XDRIP_TAG;
        treatment.eventType = SENSOR_START_EVENT_TYPE;
        treatment.created_at = DateUtil.toISOString(timestamp);
        treatment.timestamp = timestamp;
        treatment.uuid = UUID.randomUUID().toString();
        if (notes != null && notes.length() > 0) {
            treatment.notes = notes;
        }
        treatment.save();
        pushTreatmentSync(treatment);
        return treatment;
    }

    /**
     * Returns a newly created treatment entry in the database for Sensor Stop,
     * and pushes the new treatment to followers.
     * @param timestamp is optional, defaults to right now
     * @param notes is optional
     */
    public static synchronized Treatments sensorStop(@Nullable Long timestamp, @Nullable String notes) {
        if (timestamp == null || timestamp == 0) {
            timestamp = new Date().getTime();
        }

        final Treatments treatment = new Treatments();
        treatment.enteredBy = XDRIP_TAG;
        treatment.eventType = SENSOR_STOP_EVENT_TYPE;
        treatment.created_at = DateUtil.toISOString(timestamp);
        treatment.timestamp = timestamp;
        treatment.uuid = UUID.randomUUID().toString();
        if (notes != null && notes.length() > 0) {
            treatment.notes = notes;
        }
        treatment.save();
        pushTreatmentSync(treatment);
        return treatment;
    }

    public static void sensorStartIfNeeded() {

        // Create treatment entry in the database if the sensor was started by another
        // device (e.g. receiver) and not xDrip. If the sensor was started by
        // xDrip, then there will be a Sensor Start treatment already in the db.
        val lastSensorStart = Treatments.lastEventTypeFromXdrip(Treatments.SENSOR_START_EVENT_TYPE);

        // If there isn't an existing sensor start in the xDrip db, or the most recently tracked
        // sensor start was more than 15 minutes ago, then we assume the sensor was actually
        // started from a non-xDrip device and so we track it.
        if (lastSensorStart == null || JoH.msSince(lastSensorStart.timestamp) >= 15 * Constants.MINUTE_IN_MS) {
            UserError.Log.i(TAG, "Creating treatment for Sensor Start initiated by another device");
            Treatments.sensorStart(null, "Started by transmitter");
        } else {
            UserError.Log.i(TAG, "Not creating treatment for Sensor Start because one was created too recently: " + JoH.msSince(lastSensorStart.timestamp) + "ms ago");
        }
    }

    public static void sensorUpdateStartTimeIfNeeded() {
        val lastSensorStart = Treatments.lastEventTypeFromXdrip(Treatments.SENSOR_START_EVENT_TYPE);
        long localStartedAt = lastSensorStart.timestamp; // When the xDrip local session started
        long dexStartedAt = DexSessionKeeper.getStart(); // When the current session on the transmitter started
        if (dexStartedAt > 0 && !(dexStartedAt - localStartedAt < MINUTE_IN_MS * 5)) { // If the start time of the local session is more than 5 minutes older than the one on the transmitter
            Treatments.sensorStart(dexStartedAt, "Start time updated");
        }
    }

    private static void pushTreatmentSync(Treatments treatment) {
        pushTreatmentSync(treatment, true, null); // new entry by default
    }

    private static void pushTreatmentSync(final Treatments treatment, boolean is_new, String suggested_uuid) {
    }

    // This shouldn't be needed but it seems it is
    private static void fixUpTable() {
        if (patched) return;
        String[] patchup = {
                "CREATE TABLE Treatments (_id INTEGER PRIMARY KEY AUTOINCREMENT);",
                "ALTER TABLE Treatments ADD COLUMN timestamp INTEGER;",
                "ALTER TABLE Treatments ADD COLUMN uuid TEXT;",
                "ALTER TABLE Treatments ADD COLUMN eventType TEXT;",
                "ALTER TABLE Treatments ADD COLUMN enteredBy TEXT;",
                "ALTER TABLE Treatments ADD COLUMN notes TEXT;",
                "ALTER TABLE Treatments ADD COLUMN created_at TEXT;",
                "ALTER TABLE Treatments ADD COLUMN insulin REAL;",
                "ALTER TABLE Treatments ADD COLUMN insulinJSON TEXT;",
                "ALTER TABLE Treatments ADD COLUMN carbs REAL;",
                "CREATE INDEX index_Treatments_timestamp on Treatments(timestamp);",
                "CREATE UNIQUE INDEX index_Treatments_uuid on Treatments(uuid);"};

        for (String patch : patchup) {
            try {
                SQLiteUtils.execSql(patch);
                //Log.e(TAG, "Processed patch should not have succeeded!!: " + patch);
            } catch (Exception e) {
                // Log.d(TAG, "Patch: " + patch + " generated exception as it should: " + e.toString());
            }
        }
        patched = true;
    }

    public static Treatments last() {
        fixUpTable();
        return new Select()
                .from(Treatments.class)
                .orderBy("_ID desc")
                .executeSingle();
    }

    public static Treatments lastNotFromXdrip() {
        fixUpTable();
        return new Select()
                .from(Treatments.class)
                .where("enteredBy NOT LIKE '" + XDRIP_TAG + "%'")
                .orderBy("_ID DESC")
                .executeSingle();
    }

    public static Treatments lastEventTypeFromXdrip(final String eventType) {
        fixUpTable();
        return new Select()
                .from(Treatments.class)
                .where("enteredBy LIKE '" + XDRIP_TAG + "%' and eventType = ?", eventType)
                .orderBy("_ID DESC")
                .executeSingle();       // TODO does the where clause order affect optimization ref database indexes?
    }

    public static List<Treatments> latest(int num) {
        try {
            return new Select()
                    .from(Treatments.class)
                    .orderBy("timestamp desc")
                    .limit(num)
                    .execute();
        } catch (android.database.sqlite.SQLiteException e) {
            fixUpTable();
            return null;
        }
    }

    public static Treatments byuuid(String uuid) {
        if (uuid == null) return null;
        return new Select()
                .from(Treatments.class)
                .where("uuid = ?", uuid)
                .orderBy("_ID desc")
                .executeSingle();
    }

    public static Treatments byid(long id) {
        return new Select()
                .from(Treatments.class)
                .where("_ID = ?", id)
                .executeSingle();
    }

    public static Treatments byTimestamp(long timestamp) {
        return byTimestamp(timestamp, 1500);
    }

    public static Treatments byTimestamp(long timestamp, long plus_minus_millis) {
        if (plus_minus_millis > Integer.MAX_VALUE) {
            throw new RuntimeException("Treatment by TimeStamp out of range value: " + plus_minus_millis);
        }
        return byTimestamp(timestamp, (int) plus_minus_millis);
    }

    public static Treatments byTimestamp(long timestamp, int plus_minus_millis) {
        return new Select()
                .from(Treatments.class)
                .where("timestamp <= ? and timestamp >= ?", (timestamp + plus_minus_millis), (timestamp - plus_minus_millis)) // window
                .orderBy("abs(timestamp-" + Long.toString(timestamp) + ") asc")
                .executeSingle();
    }

    public static List<Treatments> listByTimestamp(long timestamp) {
        return new Select()
                .from(Treatments.class)
                .where("timestamp = ?", timestamp)
                .orderBy("timestamp desc")
                .execute();
    }

    public static void delete_all() {
        delete_all(false);
    }

    public static void delete_all(boolean from_interactive) {
        new Delete()
                .from(Treatments.class)
                .execute();
        // not synced with uploader queue - should we?
    }

    public static Treatments delete_last() {
        return delete_last(false);
    }

    public static void delete_by_timestamp(long timestamp) {
        delete_by_timestamp(timestamp, 1500, false);
    }

    public static void delete_by_timestamp(long timestamp, int accuracy, boolean from_interactive) {
        final Treatments t = byTimestamp(timestamp, accuracy); // do we need to alter default accuracy?
        if (t != null) {
            Log.d(TAG, "Deleting treatment closest to: " + JoH.dateTimeText(timestamp) + " matches uuid: " + t.uuid);
            delete_by_uuid(t.uuid, from_interactive);
        } else {
            Log.e(TAG, "Couldn't find a treatment near enough to " + JoH.dateTimeText(timestamp) + " to delete!");
        }
    }

    public static void delete_by_uuid(String uuid) {
        delete_by_uuid(uuid, false);
    }

    public static void delete_by_uuid(String uuid, boolean from_interactive) {
        Treatments thistreat = byuuid(uuid);
        if (thistreat != null) {
            thistreat.delete();
            Home.staticRefreshBGCharts();
        }
    }

    public static Treatments delete_last(boolean from_interactive) {
        Treatments thistreat = last();
        if (thistreat != null) {
            thistreat.delete();
        }
        return null;
    }

    public static void cleanup(final int retention_days) {
        fixUpTable();
        new Delete()
                .from(Treatments.class)
                .where("timestamp < ?", JoH.tsl() - (retention_days * Constants.DAY_IN_MS))
                .execute();
    }

    public static Treatments fromJSON(String json) {
        try {
            return new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create().fromJson(json, Treatments.class);
        } catch (Exception e) {
            Log.d(TAG, "Got exception parsing treatment json: " + e.toString());
            Home.toaststatic("Error on treatment, probably decryption key mismatch");
            return null;
        }
    }

    public static synchronized boolean pushTreatmentFromJson(String json) {
        return pushTreatmentFromJson(json, false);
    }

    public static synchronized boolean pushTreatmentFromJson(String json, boolean from_interactive) {
        Log.d(TAG, "converting treatment from json: " + json);
        final Treatments mytreatment = fromJSON(json);
        if (mytreatment != null) {
            if ((mytreatment.carbs == 0) && (mytreatment.insulin == 0)
                    && (mytreatment.notes != null) && (mytreatment.notes.startsWith("AndroidAPS started"))) {
                Log.d(TAG, "Skipping AndroidAPS started message");
                return false;
            }
            if ((mytreatment.eventType != null) && (mytreatment.eventType.equals("Temp Basal"))) {
                // we don't yet parse or process these
                Log.d(TAG, "Skipping Temp Basal msg");
                return false;
            }
            if (mytreatment.timestamp < 1) {
                Log.e(TAG, "Invalid treatment timestamp or 0 or less");
                return false;
            }

            if (mytreatment.uuid == null) {
                try {
                    final JSONObject jsonobj = new JSONObject(json);
                    if (jsonobj.has("_id")) mytreatment.uuid = jsonobj.getString("_id");
                } catch (JSONException e) {
                    //
                }
                if (mytreatment.uuid == null) mytreatment.uuid = UUID.randomUUID().toString();
            }
            // anything received +- 1500 ms is going to be treated as a duplicate
            final Treatments dupe_treatment = byTimestamp(mytreatment.timestamp);
            if (dupe_treatment != null) {
                Log.i(TAG, "Duplicate treatment for: " + mytreatment.timestamp);

                if ((dupe_treatment.insulin == 0) && (mytreatment.insulin > 0)) {
                    dupe_treatment.setInsulinJSON(mytreatment.insulinJSON);
                    dupe_treatment.insulin = mytreatment.insulin;
                    dupe_treatment.save();
                    Home.staticRefreshBGChartsOnIdle();
                }

                if ((dupe_treatment.carbs == 0) && (mytreatment.carbs > 0)) {
                    dupe_treatment.carbs = mytreatment.carbs;
                    dupe_treatment.save();
                    Home.staticRefreshBGChartsOnIdle();
                }

                if ((dupe_treatment.uuid != null) && (mytreatment.uuid != null) && (dupe_treatment.uuid.equals(mytreatment.uuid)) && (mytreatment.notes != null)) {

                    if ((dupe_treatment.notes == null) || (dupe_treatment.notes.length() < mytreatment.notes.length())) {
                        dupe_treatment.notes = mytreatment.notes;
                        fixUpTable();
                        dupe_treatment.save();
                        Log.d(TAG, "Saved updated treatement notes");
                        // should not end up needing to append notes and be from_interactive via undo as these
                        // would be mutually exclusive operations so we don't need to handle that here.
                        Home.staticRefreshBGChartsOnIdle();
                        // TODO review if this is correct place for new notes only
                        evaluateNotesForNotification(mytreatment);
                    }
                }

                return false;
            }
            Log.d(TAG, "Saving pushed treatment: " + mytreatment.uuid);
            if ((mytreatment.enteredBy == null) || (mytreatment.enteredBy.equals(""))) {
                mytreatment.enteredBy = "sync";
            }
            if ((mytreatment.eventType == null) || (mytreatment.eventType.equals(""))) {
                mytreatment.eventType = DEFAULT_EVENT_TYPE; // should have a default
            }
            if ((mytreatment.created_at == null) || (mytreatment.created_at.equals(""))) {
                try {
                    mytreatment.created_at = DateUtil.toISOString(mytreatment.timestamp); // should have a default
                } catch (Exception e) {
                    Log.e(TAG, "Could not convert timestamp to isostring");
                }
            }

            fixUpTable();
            long x = mytreatment.save();
            Log.d(TAG, "Saving treatment result: " + x);
            if (from_interactive) {
                pushTreatmentSync(mytreatment);
            }
            // TODO review if this is correct place for new notes only
            evaluateNotesForNotification(mytreatment);
            Home.staticRefreshBGChartsOnIdle();
            return true;
        } else {
            return false;
        }
    }

    private static void evaluateNotesForNotification(final Treatments mytreatment) {
        try {
            SensorExpiry.evaluateReceivedNote(mytreatment.notes, mytreatment.timestamp);
        } catch (Exception e) {
            UserError.Log.wtf(TAG, "Error evaluating received note: " + e + " " + mytreatment.notes);
        }
    }

    public static List<Treatments> latestForGraph(int number, double startTime) {
        return latestForGraph(number, startTime, JoH.ts());
    }

    public static List<Treatments> latestForGraph(int number, double startTime, double endTime) {
        fixUpTable();
        DecimalFormat df = new DecimalFormat("#");
        df.setMaximumFractionDigits(1); // are there decimal points in the database??
        return new Select()
                .from(Treatments.class)
                .where("timestamp >= ? and timestamp <= ?", df.format(startTime), df.format(endTime))
                .orderBy("timestamp asc")
                .limit(number)
                .execute();
    }

    public static List<Treatments> latestForGraph(final int number, final long startTime, final long endTime) {
        fixUpTable();
        return new Select()
                .from(Treatments.class)
                .where("timestamp >= ? and timestamp <= ?", startTime, endTime)
                .orderBy("timestamp asc")
                .limit(number)
                .execute();
    }

    public static long getTimeStampWithOffset(double offset) {
        //  optimisation instead of creating a new date each time?
        return (long) (new Date().getTime() - offset);
    }

    // when using multiple insulins
    private static Pair<Double, Double> calculateIobActivityFromTreatmentAtTime(final Treatments treatment, final long time, final boolean useBasal) {

        double iobContrib = 0, activityContrib = 0;
        if (treatment.insulin > 0) {
           // Log.d(TAG,"NEW TYPE insulin: "+treatment.insulin+ " "+treatment.insulinJSON);
            // translate a legacy entry to be bolus insulin

            if (iobContrib < 0) iobContrib = 0;
            if (activityContrib < 0) activityContrib = 0;
        }
        return new Pair<>(iobContrib, activityContrib);
    }

    public String getBestShortText() {
        if (!eventType.equals(DEFAULT_EVENT_TYPE)) {
            return eventType;
        } else {
                return noteHasContent() ? notes : "Treatment";
        }
    }

    public String toJSON() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("uuid", uuid);
            jsonObject.put("insulin", insulin);
            jsonObject.put("insulinJSON", insulinJSON);
            jsonObject.put("carbs", carbs);
            jsonObject.put("timestamp", timestamp);
            jsonObject.put("notes", notes);
            jsonObject.put("enteredBy", enteredBy);
            return jsonObject.toString();
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return "";
        }
    }

    private static final double MAX_SMB_UNITS = 0.3;
    private static final double MAX_OPENAPS_SMB_UNITS = 0.4;

    public boolean likelySMB() {
        return (carbs == 0 && insulin > 0
                && ((insulin <= MAX_SMB_UNITS && (notes == null || notes.length() == 0)) || (enteredBy != null && enteredBy.startsWith("openaps:") && insulin <= MAX_OPENAPS_SMB_UNITS)));
    }

    public boolean wasCreatedRecently() {
        return msSince(DateUtil.tolerantFromISODateString(created_at).getTime()) < HOUR_IN_MS * 12;
    }

    public boolean noteOnly() {
        return carbs == 0 && insulin == 0 && noteHasContent();
    }

    public boolean hasContent() {
        return insulin != 0 || carbs != 0 || noteHasContent() || !isEventTypeDefault();
    }

    public boolean noteHasContent() {
        return notes != null && notes.length() > 0;
    }

    public boolean isEventTypeDefault() {
        return eventType == null || eventType.equalsIgnoreCase(DEFAULT_EVENT_TYPE);
    }

    public static boolean matchUUID(final List<Treatments> treatments, final String uuid) {
        for (final Treatments treatment : treatments) {
            if (treatment.uuid.equalsIgnoreCase(uuid)) return true;
        }
        return false;
    }

    public String toS() {
        Gson gson = new GsonBuilder()
                .excludeFieldsWithoutExposeAnnotation()
                .serializeSpecialFloatingPointValues()
                .create();
        return gson.toJson(this);
    }

    public boolean isPenSyncedDose() {
        return notes != null && notes.startsWith("PEN");
    }

    public String getPenSerial() {
        if (isPenSyncedDose()) {
            final Pattern penPattern = Pattern.compile(".*PEN ([A-Z0-9]+).*", Pattern.DOTALL);
            final Matcher m = penPattern.matcher(notes);
            if (m.matches()) {
                return m.group(1);
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    public boolean isPrimingDose() {
        return notes != null && notes.startsWith("Priming");
    }
}
