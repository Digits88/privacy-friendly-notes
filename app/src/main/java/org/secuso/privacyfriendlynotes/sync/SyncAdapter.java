package org.secuso.privacyfriendlynotes.sync;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SyncResult;
import android.database.Cursor;
import android.os.Bundle;
import android.util.SparseArray;

import org.secuso.privacyfriendlynotes.DbContract;

import java.util.HashMap;
import java.util.Map;


public class SyncAdapter extends AbstractThreadedSyncAdapter {

    private ContentResolver contentResolver;

    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        contentResolver = context.getContentResolver();
    }

    public SyncAdapter(Context context, boolean autoInitialize, boolean allowParallelSyncs) {
        super(context, autoInitialize, allowParallelSyncs);
        contentResolver = context.getContentResolver();
    }

    private SparseArray<String> getCategories() {
        SparseArray<String> categories = new SparseArray<String>();

        String[] projection = { DbContract.CategoryEntry.COLUMN_ID, DbContract.CategoryEntry.COLUMN_NAME };
        Cursor cursor = contentResolver.query(DbContract.CATEGORIES_URI, projection, null, null, null);
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            categories.put(cursor.getInt(0), cursor.getString(1));
            cursor.moveToNext();
        }
        cursor.close();

        return categories;
    }

    private Map<String, NoteModel> getNotesFromDb(SparseArray<String> categories) {
        Map<String, NoteModel> result = new HashMap<>();

        String[] projection = {
                DbContract.NoteEntry.COLUMN_UUID,
                DbContract.NoteEntry.COLUMN_TIMESTAMP,
                DbContract.NoteEntry.COLUMN_TYPE,
                DbContract.NoteEntry.COLUMN_NAME,
                DbContract.NoteEntry.COLUMN_CONTENT,
                DbContract.NoteEntry.COLUMN_CATEGORY,
                DbContract.NoteEntry.COLUMN_TRASH,
                DbContract.NoteEntry.COLUMN_DELETED
        };

        Cursor cursor = contentResolver.query(DbContract.NOTES_URI, projection, null, null, null);
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            String uuid = cursor.getString(cursor.getColumnIndexOrThrow(DbContract.NoteEntry.COLUMN_UUID));
            NoteModel note = new NoteModel();
            note.timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(DbContract.NoteEntry.COLUMN_TIMESTAMP));
            note.type = cursor.getInt(cursor.getColumnIndexOrThrow(DbContract.NoteEntry.COLUMN_TYPE));
            note.name = cursor.getString(cursor.getColumnIndexOrThrow(DbContract.NoteEntry.COLUMN_NAME));
            note.content = cursor.getString(cursor.getColumnIndexOrThrow(DbContract.NoteEntry.COLUMN_CONTENT));
            int categoryId = cursor.getInt(cursor.getColumnIndexOrThrow(DbContract.NoteEntry.COLUMN_CATEGORY));
            note.category = categories.get(categoryId, "Default");
            note.trash = cursor.getInt(cursor.getColumnIndexOrThrow(DbContract.NoteEntry.COLUMN_TRASH)) == 1;
            note.deleted = cursor.getInt(cursor.getColumnIndexOrThrow(DbContract.NoteEntry.COLUMN_DELETED)) == 1;
            result.put(uuid, note);
            cursor.moveToNext();
        }
        cursor.close();

        return result;
    }

    private int addCategory(String name) {
        ContentValues values = new ContentValues();
        values.put(DbContract.CategoryEntry.COLUMN_NAME, name.trim());
        return (int)ContentUris.parseId(contentResolver.insert(DbContract.CATEGORIES_URI, values));
    }

    private void writeNoteToDb(String uuid, NoteModel note, Map<String, Integer> categories, boolean update) {
        Integer categoryId = categories.get(note.category);
        if (categoryId == null) {
            categoryId = addCategory(note.category);
            categories.put(note.category, categoryId);
        }

        ContentValues values = new ContentValues();
        values.put(DbContract.NoteEntry.COLUMN_TIMESTAMP, note.timestamp);
        values.put(DbContract.NoteEntry.COLUMN_TYPE, note.type);
        values.put(DbContract.NoteEntry.COLUMN_NAME, note.name);
        values.put(DbContract.NoteEntry.COLUMN_CONTENT, note.content);
        values.put(DbContract.NoteEntry.COLUMN_CATEGORY, categoryId);
        values.put(DbContract.NoteEntry.COLUMN_TRASH, note.trash);
        values.put(DbContract.NoteEntry.COLUMN_DELETED, note.deleted);

        if (update) {
            String[] selectionArgs = { uuid };
            contentResolver.update(DbContract.NOTES_URI, values, DbContract.NoteEntry.COLUMN_UUID + " = ?", selectionArgs);
        } else {
            values.put(DbContract.NoteEntry.COLUMN_UUID, uuid);
            contentResolver.insert(DbContract.NOTES_URI, values);
        }
    }

    private Map<String, NoteModel> getNotesFromCloud() {
        // TODO
        return new HashMap<>();
    }

    private void writeNoteToCloud(String uuid, NoteModel note) {
        // TODO
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
        System.out.println("syncing ...");

        SparseArray<String> categories = getCategories();
        Map<String, Integer> categoryIds = new HashMap<>();
        for (int i = 0; i < categories.size(); i++) {
            categoryIds.put(categories.valueAt(i), categories.keyAt(i));
        }

        Map<String, NoteModel> dbNotes = getNotesFromDb(categories);
        Map<String, NoteModel> cloudNotes = getNotesFromCloud();

        for (Map.Entry<String, NoteModel> entry : dbNotes.entrySet()) {
            String uuid = entry.getKey();
            NoteModel dbNote = entry.getValue();
            NoteModel cloudNote = cloudNotes.get(uuid);

            if (cloudNote == null || cloudNote.timestamp < dbNote.timestamp) {
                writeNoteToCloud(uuid, dbNote);
            }
        }

        for (Map.Entry<String, NoteModel> entry : cloudNotes.entrySet()) {
            String uuid = entry.getKey();
            NoteModel cloudNote = entry.getValue();
            NoteModel dbNote = dbNotes.get(uuid);

            if (dbNote == null || dbNote.timestamp < cloudNote.timestamp) {
                boolean update = dbNote != null;
                writeNoteToDb(uuid, dbNote, categoryIds, update);
            }
        }

        System.out.println("sync done.");
    }

}
