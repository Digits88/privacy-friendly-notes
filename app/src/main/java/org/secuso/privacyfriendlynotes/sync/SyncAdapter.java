package org.secuso.privacyfriendlynotes.sync;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.IntentSender;
import android.content.SyncResult;
import android.database.Cursor;
import android.os.Bundle;
import android.util.SparseArray;

import org.secuso.privacyfriendlynotes.DbContract;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.MetadataBuffer;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.query.Filters;
import com.google.android.gms.drive.query.Query;
import com.google.android.gms.drive.query.SearchableField;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.security.spec.KeySpec;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;



public class SyncAdapter extends AbstractThreadedSyncAdapter {

    private ContentResolver contentResolver;
    private GoogleApiClient mGoogleApiClient = null;


    private void createAppBaseFolder() {
        DriveFolder folder = Drive.DriveApi.getRootFolder(mGoogleApiClient);
        Query query = new Query.Builder()
                .addFilter(Filters.eq(SearchableField.TITLE, "SecretsDB"))
                .build();
        MetadataBuffer md = folder.queryChildren(mGoogleApiClient, query).await().getMetadataBuffer();

        if (md.getCount() == 0) {
            Log.e(TAG,"No app dir yet - create it");
            MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                    .setTitle("SecretsDB").build();
            DriveFolder.DriveFolderResult result = Drive.DriveApi.getRootFolder(mGoogleApiClient).createFolder(mGoogleApiClient, changeSet).await();
            if (!result.getStatus().isSuccess()) {
                Log.e(TAG,"Error while trying to create the folder");
                return;
            }
            Log.e(TAG, "Created a folder: " + result.getDriveFolder().getDriveId());
            appFolder = result.getDriveFolder();
        } else {
            for (Metadata m : md) {
                if (m.isFolder()) {
                    appFolder = m.getDriveId().asDriveFolder();
                }
            }

            Log.e(TAG, "Successfully listed files.");
        }
    };

    final static String TAG = "Sync";

    public DriveFolder appFolder = null;

    public Map<String, String> listFiles() {
        DriveApi.MetadataBufferResult metadataBufferResult = appFolder.listChildren(mGoogleApiClient).await();
        Map<String, String> data = new HashMap<String, String>();
        MetadataBuffer mb = metadataBufferResult.getMetadataBuffer();
        for (Metadata m : mb) {
            data.put(m.getTitle(), m.getDriveId().encodeToString());
            Log.e(TAG, "********** file " + m.getTitle());
            Log.e(TAG, m.isEditable()?"edit yes":"edit no");
        }
        //mb.release();
        return data;
    };

    public SecretKey getKey() {
        // Number of PBKDF2 hardening rounds to use. Larger values increase
        // computation time. You should select a value that causes computation
        // to take >100ms.
        final int iterations = 1000;

        // Generate a 256-bit key
        final int outputKeyLength = 256;
        SecretKey secretKey = null;
        try {
            SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            KeySpec keySpec = new PBEKeySpec("supergeheim".toCharArray(), "salt".getBytes(), iterations, outputKeyLength);
            secretKey = secretKeyFactory.generateSecret(keySpec);
        } catch (Exception e) {

        }
        return secretKey;
    }

    public byte[] decrypt(final byte[] dataIn) {
        SecretKey secretKey = getKey();
        byte[] decodedBytes = null;
        try {
            Cipher c = Cipher.getInstance("AES");
            c.init(Cipher.DECRYPT_MODE, secretKey);
            decodedBytes = c.doFinal(dataIn);
        } catch (Exception e) {}
        return decodedBytes;
    };

    public byte[] encrypt(final byte[] dataIn) {
        SecretKey secretKey = getKey();
        byte[] encodedBytes = null;
        try {
            Cipher c = Cipher.getInstance("AES");
            c.init(Cipher.ENCRYPT_MODE, secretKey);
            encodedBytes = c.doFinal(dataIn);
        } catch (Exception e) {
            Log.e("encryptFIN128AES", "AES encryption error");
        }
        return encodedBytes;
    }

    public interface getRemoteObjectResult {
        public void onResult(NoteModel o);
    }

    public interface ReadFileResult {
        public void onResult(byte[] data);
    }

    public NoteModel getRemoteObject(String uuid) {
        byte[] data = readFile(uuid);
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        ObjectInput in = null;
        NoteModel r = null;
        try {
            in = new ObjectInputStream(bis);
            r = (NoteModel) in.readObject();
        } catch (Exception e) {
        }
        return r;

    };

    public void storeObject(String uuid, NoteModel r) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutput out = null;
        try {
            out = new ObjectOutputStream(bos);
            out.writeObject(r);
            out.flush();
            byte[] bytes = bos.toByteArray();
            writeFile(uuid, bytes);
        } catch (Exception e) {Log.e(TAG, e.toString());}
    }

    public byte[] readFile(final String name) {
        Map<String, String> result = listFiles();
        if (result.containsKey(name)) {
            DriveFile f = DriveId.decodeFromString(result.get(name)).asDriveFile();
            DriveApi.DriveContentsResult dcresult = f.open(mGoogleApiClient, DriveFile.MODE_READ_ONLY, null).await();
            Log.e(TAG, "********* on REsult");
            if (!dcresult.getStatus().isSuccess()) {
                Log.e(TAG, "##### timeout");
                // Handle error
                return null;
            }
            DriveContents contents = dcresult.getDriveContents();
            InputStream inputStream = contents.getInputStream();

            byte[] data = new byte[5000];
            int size = 0;
            try {
                size = inputStream.read(data);
            } catch (IOException e) {
                e.printStackTrace();
            }
            byte[] d = new byte[size];
            System.arraycopy(data, 0, d, 0, size);
            byte[] decData = decrypt(d);
            return decData;
        }
        return null;
    };

    public void writeFile(final String name, final byte[] dataIn) {
// encrypt data
        final byte[] data = encrypt(dataIn);
        Map<String, String> result = listFiles();
        if (result.containsKey(name)) {
            DriveFile f = DriveId.decodeFromString(result.get(name)).asDriveFile();
            f.open(mGoogleApiClient, DriveFile.MODE_WRITE_ONLY, null).setResultCallback(
                    (new ResultCallback<DriveApi.DriveContentsResult>() {
                        @Override
                        public void onResult(DriveApi.DriveContentsResult result) {
                            Log.e(TAG, "********* on REsult");
                            if (!result.getStatus().isSuccess()) {
                                Log.e(TAG, "##### timeout");
                                // Handle error
                                return;
                            }
                            DriveContents contents = result.getDriveContents();
                            OutputStream outputStream = contents.getOutputStream();
                            try {
                                outputStream.write(data);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                                    .setLastViewedByMeDate(new Date()).build();
                            contents.commit(mGoogleApiClient, changeSet).setResultCallback(new ResultCallback<Status>() {
                                @Override
                                public void onResult(Status result) {
                                    // Handle the response status
                                }
                            });

                        }
                    }), 5, TimeUnit.SECONDS
            );
        } else {
            // no file yet

            Drive.DriveApi.newDriveContents(mGoogleApiClient)
                    .setResultCallback(new
                                               ResultCallback<DriveApi.DriveContentsResult>() {
                                                   @Override
                                                   public void onResult(final DriveApi.DriveContentsResult result) {
                                                       if (!result.getStatus().isSuccess()) {
                                                           Log.e(TAG,"Error while trying to create new file contents");
                                                           return;
                                                       }
                                                       final DriveContents driveContents = result.getDriveContents();

                                                       // write content to DriveContents
                                                       OutputStream outputStream = driveContents.getOutputStream();
                                                       try {
                                                           outputStream.write(data);

                                                       } catch (IOException e) {
                                                           Log.e(TAG, e.getMessage());
                                                       }

                                                       MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                                                               .setTitle(name)
                                                               .setMimeType("text/plain")
                                                               .build();

                                                       // create a file on root folder
                                                       appFolder.createFile(mGoogleApiClient, changeSet, driveContents)
                                                               .setResultCallback(new ResultCallback<DriveFolder.DriveFileResult>() {
                                                                   @Override
                                                                   public void onResult(@NonNull DriveFolder.DriveFileResult result) {
                                                                       if (!result.getStatus().isSuccess()) {
                                                                           Log.e(TAG, "Error while trying to create the file");
                                                                           return;
                                                                       }
                                                                       Log.e(TAG,"Created a file with content: " + result.getDriveFile().getDriveId());
                                                                   }
                                                               });
                                                   }

                                               });
        }



    };






/*
// onresume
        if (mGoogleApiClient == null) {
        // Create the API client and bind it to an instance variable.
        // We use this instance as the callback for connection and connection
        // failures.
        // Since no account name is passed, the user is prompted to choose.
        mGoogleApiClient = new GoogleApiClient.Builder(this)
        .addApi(Drive.API)
        .addScope(Drive.SCOPE_FILE)
        .addConnectionCallbacks(this)
        .addOnConnectionFailedListener(this)
        .build();
        }
        // Connect the client. Once connected, the camera is launched.
        mGoogleApiClient.connect();


@Override
protected void onPause() {
        if (mGoogleApiClient != null) {
        mGoogleApiClient.disconnect();
        }
        super.onPause();
        }
*/

    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        contentResolver = context.getContentResolver();

        if (mGoogleApiClient == null) {
            // Create the API client and bind it to an instance variable.
            // We use this instance as the callback for connection and connection
            // failures.
            // Since no account name is passed, the user is prompted to choose.
            mGoogleApiClient = new GoogleApiClient.Builder(context)
                    .addApi(Drive.API)
                    .addScope(Drive.SCOPE_FILE)
                    .build();
        }
    }

    public SyncAdapter(Context context, boolean autoInitialize, boolean allowParallelSyncs) {
        super(context, autoInitialize, allowParallelSyncs);
        contentResolver = context.getContentResolver();
    }

    private SparseArray<String> getCategories() {
        SparseArray<String> categories = new SparseArray<String>();

        String[] projection = {DbContract.CategoryEntry.COLUMN_ID, DbContract.CategoryEntry.COLUMN_NAME};
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
        return (int) ContentUris.parseId(contentResolver.insert(DbContract.CATEGORIES_URI, values));
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
            String[] selectionArgs = {uuid};
            contentResolver.update(DbContract.NOTES_URI, values, DbContract.NoteEntry.COLUMN_UUID + " = ?", selectionArgs);
        } else {
            values.put(DbContract.NoteEntry.COLUMN_UUID, uuid);
            contentResolver.insert(DbContract.NOTES_URI, values);
        }
    }

    private Map<String, NoteModel> getNotesFromCloud() {
        Map<String, String> result = listFiles();
        Map<String, NoteModel> results = new HashMap<String, NoteModel>();
        for (String key : result.keySet()) {
            NoteModel n = getRemoteObject(key);
            results.put(key, n);
        }

        return results;
    }

    private void writeNoteToCloud(String uuid, NoteModel note) {
        storeObject(uuid, note);
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
        System.out.println("syncing ...");

        // Connect the client. Once connected, the camera is launched.
        ConnectionResult result = mGoogleApiClient.blockingConnect();
        System.out.printf("blockingConnect() = %d%n", result.getErrorCode());
        createAppBaseFolder();

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


/*

    final private ResultCallback<DriveApi.DriveContentsResult> driveContentsCallback = new
            ResultCallback<DriveApi.DriveContentsResult>() {
                @Override
                public void onResult(DriveApi.DriveContentsResult result) {
                    if (!result.getStatus().isSuccess()) {
                        showMessage("Error while trying to create new file contents");
                        return;
                    }
                    final DriveContents driveContents = result.getDriveContents();

                    // Perform I/O off the UI thread.
                    new Thread() {
                        @Override
                        public void run() {
                            // write content to DriveContents
                            OutputStream outputStream = driveContents.getOutputStream();
                            Writer writer = new OutputStreamWriter(outputStream);
                            try {
                                writer.write("Hello World!");
                                writer.close();
                            } catch (IOException e) {
                                Log.e(TAG, e.getMessage());
                            }

                            MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                                    .setTitle("New file 2")
                                    .setMimeType("text/plain")
                                    .build();

                            // create a file on root folder
                            Drive.DriveApi.getRootFolder(mGoogleApiClient)
                                    .createFile(mGoogleApiClient, changeSet, driveContents)
                                    .setResultCallback(fileCallback);
                        }
                    }.start();
                }
            };

    final private ResultCallback<DriveFolder.DriveFileResult> fileCallback = new
            ResultCallback<DriveFolder.DriveFileResult>() {
                @Override
                public void onResult(DriveFolder.DriveFileResult result) {
                    if (!result.getStatus().isSuccess()) {
                        showMessage("Error while trying to create the file");
                        return;
                    }
                    showMessage("Created a file with content: " + result.getDriveFile().getDriveId());
                }
            };


    public void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
*/