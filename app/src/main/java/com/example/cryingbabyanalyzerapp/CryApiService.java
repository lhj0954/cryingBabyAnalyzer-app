package com.example.cryingbabyanalyzerapp;

/*
* json 응답 받아서 파싱
* */

import com.google.gson.Gson;

import java.io.File;
import java.io.IOException;
import java.util.Map;

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

    public static class PredictResponse {
        public String filename;
        public int sample_rate;
        public float duration_sec;
        public boolean triggered;
        public Float trigger_time_sec;
        public AnalysisWindow analysis_window;
        public Prediction prediction;
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

    private final OkHttpClient client = new OkHttpClient();
    private final Gson gson = new Gson();
    private final String predictUrl;

    public CryApiService(String baseUrl) {
        this.predictUrl = baseUrl + "/predict";
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
}