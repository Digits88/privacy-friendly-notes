package org.secuso.privacyfriendlynotes;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public class DbContentProvider extends ContentProvider {

    public static final String AUTHORITY = "org.secuso.privacyfriendlynotes.provider";

    private static final UriMatcher URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
    private static final int URI_NOTES = 1;
    private static final int URI_NOTE = 2;
    private static final int URI_CATEGORIES = 3;
    private static final int URI_CATEGORY = 4;
    private static final int URI_NOTIFICATIONS = 5;
    private static final int URI_NOTIFICATION = 6;

    static {
        URI_MATCHER.addURI(AUTHORITY, "notes", URI_NOTES);
        URI_MATCHER.addURI(AUTHORITY, "notes/#", URI_NOTE);
        URI_MATCHER.addURI(AUTHORITY, "categories", URI_CATEGORIES);
        URI_MATCHER.addURI(AUTHORITY, "categories/#", URI_CATEGORY);
        URI_MATCHER.addURI(AUTHORITY, "notifications", URI_NOTIFICATIONS);
        URI_MATCHER.addURI(AUTHORITY, "notifications/#", URI_NOTIFICATION);
    }

    private SQLiteDatabase db;

    @Override
    public boolean onCreate() {
        DbOpenHelper dbHelper = new DbOpenHelper(getContext());
        db = dbHelper.getWritableDatabase();
        return true;
    }

    private String getTableName(@NonNull Uri uri) {
        switch (URI_MATCHER.match(uri)) {
            case URI_NOTES:
            case URI_NOTE:
                return DbContract.NoteEntry.TABLE_NAME;
            case URI_CATEGORIES:
            case URI_CATEGORY:
                return DbContract.CategoryEntry.TABLE_NAME;
            case URI_NOTIFICATIONS:
            case URI_NOTIFICATION:
                return DbContract.NotificationEntry.TABLE_NAME;
            default:
                return null;
        }
    }

    private boolean isSpecificItem(@NonNull Uri uri) {
        switch (URI_MATCHER.match(uri)) {
            case URI_NOTE:
            case URI_CATEGORY:
            case URI_NOTIFICATION:
                return true;
            default:
                return false;
        }
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        String table = getTableName(uri);
        if (table == null) return null;

        SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
        builder.setTables(table);
        if (isSpecificItem(uri)) {
            builder.appendWhere("_id = " + uri.getLastPathSegment());
        }
        return builder.query(db, projection, selection, selectionArgs, null, null, null);
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        String table = getTableName(uri);
        if (table == null) return null;
        return "vnd.android.dir/vnd." + AUTHORITY + "." + table;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        String table = getTableName(uri);
        if (table == null || isSpecificItem(uri)) return null;
        long id = db.insert(table, null, values);
        return ContentUris.withAppendedId(uri, id);
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        String table = getTableName(uri);
        if (table == null) return 0;
        String sel = selection;
        if (isSpecificItem(uri)) {
            if (sel == null || sel.isEmpty()) {
                sel = "_id = " + uri.getLastPathSegment();
            } else {
                sel = sel + "AND _id = " + uri.getLastPathSegment();
            }
        }
        return db.delete(table, sel, selectionArgs);
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        String table = getTableName(uri);
        if (table == null) return 0;
        String sel = selection;
        if (isSpecificItem(uri)) {
            if (sel == null || sel.isEmpty()) {
                sel = "_id = " + uri.getLastPathSegment();
            } else {
                sel = sel + "AND _id = " + uri.getLastPathSegment();
            }
        }
        return db.update(table, values, sel, selectionArgs);
    }
}
