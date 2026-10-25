package com.example.cryingbabyanalyzerapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class LocalCryRecordStore extends SQLiteOpenHelper {

    private static final String DB_NAME = "cry_records_local.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE = "cry_records";

    private final Context appContext;

    public LocalCryRecordStore(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
        this.appContext = context.getApplicationContext();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE " + TABLE + " (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "created_at TEXT NOT NULL," +
                        "filename TEXT NOT NULL," +
                        "saved_filename TEXT NOT NULL," +
                        "audio_path TEXT NOT NULL," +
                        "label TEXT," +
                        "confidence REAL," +
                        "duration_sec REAL," +
                        "message TEXT," +
                        "feedback_correct INTEGER," +
                        "actual_reason TEXT," +
                        "caregiver_action TEXT," +
                        "feedback_created_at TEXT" +
                        ")"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 현재 로컬 DB 최초 버전. 추후 스키마 변경 시 마이그레이션을 추가합니다.
    }

    public synchronized int insertRecord(
            File sourceWav,
            String label,
            Float confidence,
            Float durationSec,
            String message
    ) throws Exception {
        if (sourceWav == null || !sourceWav.exists()) {
            throw new IllegalArgumentException("저장할 음성 파일이 없습니다.");
        }

        File audioDir = new File(appContext.getFilesDir(), "cry_records");
        if (!audioDir.exists() && !audioDir.mkdirs()) {
            throw new IllegalStateException("울음 기록 폴더를 만들 수 없습니다.");
        }

        String timestamp = new SimpleDateFormat(
                "yyyyMMdd_HHmmss",
                Locale.KOREA
        ).format(new Date());

        String savedFilename = timestamp
                + "_"
                + UUID.randomUUID().toString().substring(0, 8)
                + ".wav";

        File savedFile = new File(audioDir, savedFilename);
        copyFile(sourceWav, savedFile);

        String createdAt = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.KOREA
        ).format(new Date());

        ContentValues values = new ContentValues();
        values.put("created_at", createdAt);
        values.put("filename", sourceWav.getName());
        values.put("saved_filename", savedFilename);
        values.put("audio_path", savedFile.getAbsolutePath());
        values.put("label", label);

        if (confidence != null) {
            values.put("confidence", confidence);
        }

        if (durationSec != null) {
            values.put("duration_sec", durationSec);
        }

        values.put("message", message);

        long id = getWritableDatabase().insertOrThrow(
                TABLE,
                null,
                values
        );

        return (int) id;
    }

    public synchronized List<CryApiService.CryRecord> listRecords() {
        List<CryApiService.CryRecord> records = new ArrayList<>();

        Cursor cursor = getReadableDatabase().query(
                TABLE,
                null,
                null,
                null,
                null,
                null,
                "created_at DESC, id DESC",
                "500"
        );

        try {
            while (cursor.moveToNext()) {
                records.add(fromCursor(cursor));
            }
        } finally {
            cursor.close();
        }

        return records;
    }

    public synchronized CryApiService.CryRecord getRecord(int recordId) {
        Cursor cursor = getReadableDatabase().query(
                TABLE,
                null,
                "id = ?",
                new String[]{String.valueOf(recordId)},
                null,
                null,
                null,
                "1"
        );

        try {
            if (cursor.moveToFirst()) {
                return fromCursor(cursor);
            }
            return null;
        } finally {
            cursor.close();
        }
    }

    public synchronized boolean updateFeedback(
            int recordId,
            boolean correct,
            String actualReason,
            String caregiverAction
    ) {
        ContentValues values = new ContentValues();
        values.put("feedback_correct", correct ? 1 : 0);

        if (actualReason == null || actualReason.trim().isEmpty()) {
            values.putNull("actual_reason");
        } else {
            values.put("actual_reason", actualReason);
        }

        if (caregiverAction == null || caregiverAction.trim().isEmpty()) {
            values.putNull("caregiver_action");
        } else {
            values.put("caregiver_action", caregiverAction);
        }

        values.put(
                "feedback_created_at",
                new SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss",
                        Locale.KOREA
                ).format(new Date())
        );

        int updated = getWritableDatabase().update(
                TABLE,
                values,
                "id = ?",
                new String[]{String.valueOf(recordId)}
        );

        return updated > 0;
    }

    private CryApiService.CryRecord fromCursor(Cursor cursor) {
        CryApiService.CryRecord record = new CryApiService.CryRecord();

        record.id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
        record.created_at = cursor.getString(cursor.getColumnIndexOrThrow("created_at"));
        record.filename = cursor.getString(cursor.getColumnIndexOrThrow("filename"));
        record.saved_filename = cursor.getString(cursor.getColumnIndexOrThrow("saved_filename"));
        record.audio_path = cursor.getString(cursor.getColumnIndexOrThrow("audio_path"));
        record.label = getNullableString(cursor, "label");
        record.confidence = getNullableFloat(cursor, "confidence");
        record.duration_sec = getNullableFloat(cursor, "duration_sec");
        record.message = getNullableString(cursor, "message");

        int feedbackIndex = cursor.getColumnIndexOrThrow("feedback_correct");
        if (cursor.isNull(feedbackIndex)) {
            record.feedback_correct = null;
        } else {
            record.feedback_correct = cursor.getInt(feedbackIndex) == 1;
        }

        record.actual_reason = getNullableString(cursor, "actual_reason");
        record.caregiver_action = getNullableString(cursor, "caregiver_action");
        record.feedback_created_at = getNullableString(cursor, "feedback_created_at");

        return record;
    }

    private String getNullableString(Cursor cursor, String column) {
        int index = cursor.getColumnIndexOrThrow(column);
        return cursor.isNull(index) ? null : cursor.getString(index);
    }

    private Float getNullableFloat(Cursor cursor, String column) {
        int index = cursor.getColumnIndexOrThrow(column);
        return cursor.isNull(index) ? null : cursor.getFloat(index);
    }

    private void copyFile(File source, File destination) throws Exception {
        byte[] buffer = new byte[8192];

        try (
                FileInputStream input = new FileInputStream(source);
                FileOutputStream output = new FileOutputStream(destination)
        ) {
            int read;
            while ((read = input.read(buffer)) > 0) {
                output.write(buffer, 0, read);
            }
            output.flush();
        }
    }
}
