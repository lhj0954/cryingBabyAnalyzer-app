package com.example.cryingbabyanalyzerapp;

/*
* json 응답 받아서 파싱
* */

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CryApiService {

    public interface PredictCallback {
        void onSuccess(PredictResponse response);
        void onFailure(String message);
    }

    public interface RecordsCallback {
        void onSuccess(List<CryRecord> records);
        void onFailure(String message);
    }

    public interface StatsCallback {
        void onSuccess(RecordsStats stats);
        void onFailure(String message);
    }

    public static class PredictResponse {
        public String filename;
        public int sample_rate;
        public float duration_sec;
        public boolean triggered;
        public Float trigger_time_sec;
        public AnalysisWindow analysis_window;
        public Prediction prediction;
        public Integer record_id;
        public String message;
    }

    public static class AnalysisWindow {
        public float start_sec;
        public float end_sec;
        public float duration_sec;
    }

    public static class Prediction {
        public String label;
        public float confidence;
        public Yamnet yamnet;
    }

    public static class Yamnet {
        public float baby_cry_max;
        public float crying_max;
        public float merged_cry_max;
    }

    public static class CryRecord {
        public int id;
        public String created_at;
        public String filename;
        public String saved_filename;
        public String audio_path;
        public String label;
        public Float confidence;
        public Float duration_sec;
        public String message;
    }

    public static class RecordsStats {
        public int total;
        public List<LabelCount> by_label;
        public List<HourCount> by_hour;
    }

    public static class LabelCount {
        public String label;
        public int count;
    }

    public static class HourCount {
        public String hour;
        public int count;
    }

    private final OkHttpClient client = new OkHttpClient();
    private final Gson gson = new Gson();
    private final String predictUrl;
    private final String recordsUrl;
    private final String statsUrl;

    public CryApiService(String baseUrl) {
        this.predictUrl = baseUrl + "/predict";
        this.recordsUrl = baseUrl + "/records";
        this.statsUrl = baseUrl + "/records/stats";
    }

    public void predict(File wavFile, PredictCallback callback) {
        RequestBody fileBody = RequestBody.create(
                wavFile,
                MediaType.parse("audio/wav")
        );

        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", wavFile.getName(), fileBody)
                .build();

        Request request = new Request.Builder()
                .url(predictUrl)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure("API 요청 실패: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onFailure("서버 오류: " + response.code());
                    return;
                }

                String body = response.body() != null ? response.body().string() : "";
                PredictResponse parsed = gson.fromJson(body, PredictResponse.class);
                callback.onSuccess(parsed);
            }
        });
    }

    public void getRecords(RecordsCallback callback) {
        Request request = new Request.Builder()
                .url(recordsUrl + "?limit=100")
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure("기록 조회 실패: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onFailure("서버 오류: " + response.code());
                    return;
                }

                String body = response.body() != null ? response.body().string() : "[]";
                Type type = new TypeToken<List<CryRecord>>() {}.getType();
                List<CryRecord> parsed = gson.fromJson(body, type);
                callback.onSuccess(parsed);
            }
        });
    }

    public void getStats(StatsCallback callback) {
        Request request = new Request.Builder()
                .url(statsUrl)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure("통계 조회 실패: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onFailure("서버 오류: " + response.code());
                    return;
                }

                String body = response.body() != null ? response.body().string() : "{}";
                RecordsStats parsed = gson.fromJson(body, RecordsStats.class);
                callback.onSuccess(parsed);
            }
        });
    }
}
