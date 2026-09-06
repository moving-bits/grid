package com.movingbits.grid.demo;

import android.content.ContentValues;
import android.content.Context;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import androidx.annotation.Nullable;

public class DataStore {

    private static final String LOGTAG = "DatabaseGridSample";
    private static final String DBFILENAME = "databasegrid.sqlite";
    private static final int DBVERSION = 1;

    private static volatile SQLiteDatabase database = null;
    /** The helper owns the opened database; it must not be outlived by it. */
    private static DbHelper helper = null;

    private static class DbHelper extends SQLiteOpenHelper {

        public DbHelper(final @Nullable Context context, final @Nullable String name, final @Nullable SQLiteDatabase.CursorFactory factory, final int version) {
            super(context, name, factory, version);
        }

        public DbHelper(final @Nullable Context context, final @Nullable String name, final @Nullable SQLiteDatabase.CursorFactory factory, final int version, final @Nullable DatabaseErrorHandler errorHandler) {
            super(context, name, factory, version, errorHandler);
        }

        @Override
        public void onCreate(final SQLiteDatabase db) {
            Log.e(LOGTAG, "on create database");
            db.execSQL("CREATE TABLE IF NOT EXISTS cities (" +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "city TEXT," +
                    "country TEXT," +
                    "popestimate INTEGER," +
                    "area INTEGER" +
                    ")");
            db.execSQL("CREATE TABLE IF NOT EXISTS mountains (" +
                    "_ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "mountain TEXT," +
                    "height INTEGER," +
                    "lat TEXT," +
                    "lon TEXT," +
                    "first INTEGER," +
                    "country TEXT" +
                    ")");

            // prefill with some data
            db.beginTransaction();
            try {
                ContentValues vCity = new ContentValues();
                for (City city : DemoData.CITIES) {
                    vCity.clear();
                    vCity.put("city", city.city());
                    vCity.put("country", city.country());
                    vCity.put("popestimate", city.population());
                    vCity.put("area", city.area());
                    db.insert("cities", null, vCity);
                }

                ContentValues vMountain = new ContentValues();
                for (Mountain mountain : DemoData.MOUNTAINS) {
                    vMountain.clear();
                    vMountain.put("mountain", mountain.getMountain());
                    vMountain.put("height", mountain.getHeightAsString());
                    vMountain.put("lat", mountain.getLat());
                    vMountain.put("lon", mountain.getLon());
                    vMountain.put("first", mountain.getFirstAsString());
                    vMountain.put("country", mountain.getCountry());
                    db.insert("mountains", null, vMountain);
                }

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            Log.e(LOGTAG, "finished onCreate");
        }

        @Override
        public void onUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
            Log.e(LOGTAG, "onUpgrade to version " + newVersion);
            try {
                if (db.isReadOnly()) {
                    return;
                }

                // no upgrades yet defined

            } catch (SQLiteException e) {
                Log.e(LOGTAG, "error on database upgrade: " + e.getMessage());
            }
        }
    }

    public static SQLiteDatabase getDatabase(final Context context) {
        synchronized (DataStore.class) {
            if (database != null) {
                return database;
            }
            try {
                // No try-with-resources: closing the helper would close the database along
                // with it, and the caller would get it back unusable.
                helper = new DbHelper(context.getApplicationContext(), DBFILENAME, null, DBVERSION);
                database = helper.getWritableDatabase();
            } catch (SQLiteException e) {
                Log.e(LOGTAG, "error getting database: " + e.getMessage());
            }
            return database;
        }
    }

}
