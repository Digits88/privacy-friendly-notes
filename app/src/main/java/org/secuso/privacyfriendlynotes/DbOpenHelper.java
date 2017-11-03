package org.secuso.privacyfriendlynotes;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;

import java.util.Date;
import java.util.UUID;

/**
 * Created by Robin on 11.06.2016.
 */
public class DbOpenHelper extends SQLiteOpenHelper {

    private static final int DATABASE_VERSION = 2;
    private static final String DATABASE_NAME = "allthenotes";
    Context context;

    private static final String NOTES_TABLE_CREATE =
            "CREATE TABLE " + DbContract.NoteEntry.TABLE_NAME + " (" +
                    DbContract.NoteEntry.COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    DbContract.NoteEntry.COLUMN_UUID + " TEXT NOT NULL, " +
                    DbContract.NoteEntry.COLUMN_TIMESTAMP + " INTEGER NOT NULL, " +
                    DbContract.NoteEntry.COLUMN_TYPE + " INTEGER NOT NULL, " +
                    DbContract.NoteEntry.COLUMN_NAME + " TEXT NOT NULL, " +
                    DbContract.NoteEntry.COLUMN_CONTENT + " TEXT NOT NULL, " +
                    DbContract.NoteEntry.COLUMN_CATEGORY + " INTEGER, " +
                    DbContract.NoteEntry.COLUMN_TRASH + " INTEGER NOT NULL DEFAULT 0, " +
                    DbContract.NoteEntry.COLUMN_DELETED + " INTEGER NOT NULL DEFAULT 0);";

    private static final String CATEGORIES_TABLE_CREATE =
            "CREATE TABLE " + DbContract.CategoryEntry.TABLE_NAME + " (" +
                    DbContract.CategoryEntry.COLUMN_ID + " INTEGER PRIMARY KEY, " +
                    DbContract.CategoryEntry.COLUMN_NAME + " TEXT NOT NULL UNIQUE);";

    private static final String NOTIFICATIONS_TABLE_CREATE =
            "CREATE TABLE " + DbContract.NotificationEntry.TABLE_NAME + " (" +
                    DbContract.NotificationEntry.COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    DbContract.NotificationEntry.COLUMN_NOTE + " INTEGER NOT NULL, " +
                    DbContract.NotificationEntry.COLUMN_TIME + " INTEGER NOT NULL);";

    DbOpenHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(NOTES_TABLE_CREATE);
        db.execSQL(CATEGORIES_TABLE_CREATE);
        db.execSQL(NOTIFICATIONS_TABLE_CREATE);
        db.execSQL("INSERT INTO " + DbContract.CategoryEntry.TABLE_NAME + " (" + DbContract.CategoryEntry.COLUMN_NAME + ") VALUES ('" + context.getString(R.string.default_category) + "');");
    }

    private void updateV2(SQLiteDatabase db) {
        // add columns 'uuid', 'timestamp' and 'deleted'
        db.execSQL("ALTER TABLE " + DbContract.NoteEntry.TABLE_NAME +
                   " ADD COLUMN " + DbContract.NoteEntry.COLUMN_UUID + " TEXT;");
        db.execSQL("ALTER TABLE " + DbContract.NoteEntry.TABLE_NAME +
                   " ADD COLUMN " + DbContract.NoteEntry.COLUMN_TIMESTAMP + " INTEGER;");
        db.execSQL("ALTER TABLE " + DbContract.NoteEntry.TABLE_NAME +
                   " ADD COLUMN " + DbContract.NoteEntry.COLUMN_DELETED + " INTEGER NOT NULL DEFAULT 0;");

        // assign UUIDs and timestamps to existing table entries
        String[] COLUMNS = { DbContract.NoteEntry. COLUMN_ID };
        Cursor cursor = db.query(DbContract.NoteEntry.TABLE_NAME, COLUMNS, null, null, null, null, null);
        cursor.moveToFirst();
        long timestamp = new Date().getTime();

        while (!cursor.isAfterLast()) {
            ContentValues values = new ContentValues();
            values.put(DbContract.NoteEntry.COLUMN_UUID, UUID.randomUUID().toString());
            values.put(DbContract.NoteEntry.COLUMN_TIMESTAMP, timestamp);

            String selection = DbContract.NoteEntry.COLUMN_ID + " = ?";
            String[] selectionArgs = { Integer.toString(cursor.getInt(0)) };

            db.update(DbContract.NoteEntry.TABLE_NAME, values, selection, selectionArgs);

            cursor.moveToNext();
        }

        cursor.close();
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 1 || oldVersion > DATABASE_VERSION) {
            db.execSQL("DROP TABLE IF EXISTS " + DbContract.NoteEntry.TABLE_NAME + ";");
            db.execSQL("DROP TABLE IF EXISTS " + DbContract.CategoryEntry.TABLE_NAME + ";");
            db.execSQL("DROP TABLE IF EXISTS " + DbContract.NotificationEntry.TABLE_NAME + ";");
            onCreate(db);
            return;
        }

        if (oldVersion < 2) updateV2(db);
    }
}
