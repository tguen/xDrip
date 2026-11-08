package com.eveningoutpost.dexdrip.models;

import android.provider.BaseColumns;

import com.activeandroid.annotation.Column;
import com.activeandroid.annotation.Table;
import com.activeandroid.query.Select;
import com.google.gson.annotations.Expose;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;


/**
 * Created by jamorham on 11/06/2018.
 */

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "APStatus", id = BaseColumns._ID)
public class APStatus extends PlusModel {

    private static boolean patched = false;
    private final static String TAG = APStatus.class.getSimpleName();
    private final static boolean d = false;

    private static final String[] schema = {
            "CREATE TABLE APStatus (_id INTEGER PRIMARY KEY AUTOINCREMENT);",
            "ALTER TABLE APStatus ADD COLUMN timestamp INTEGER;",
            "ALTER TABLE APStatus ADD COLUMN basal_percent INTEGER;",
            "ALTER TABLE APStatus ADD COLUMN basal_absolute REAL default -1;",
            "CREATE UNIQUE INDEX index_APStatus_timestamp on APStatus(timestamp);"};


    @Expose
    @Column(name = "timestamp", unique = true, onUniqueConflicts = Column.ConflictAction.IGNORE)
    public long timestamp;

    @Expose
    @Column(name = "basal_percent")
    public int basal_percent;

    @Expose
    @Column(name = "basal_absolute")
    public double basal_absolute;


    // static methods

    // TODO use persistent store?
    public static APStatus last() {
        try {
            return new Select()
                    .from(APStatus.class)
                    .orderBy("timestamp desc")
                    .executeSingle();
        } catch (android.database.sqlite.SQLiteException e) {
            updateDB();
            return null;
        }
    }


    // create the table ourselves without worrying about model versioning and downgrading
    public static void updateDB() {
        patched = fixUpTable(schema, patched);
    }
}
