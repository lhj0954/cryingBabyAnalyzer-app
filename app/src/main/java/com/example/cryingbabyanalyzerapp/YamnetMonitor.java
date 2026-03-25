package com.example.cryingbabyanalyzerapp;

/*감지모드 ON이면 마이크 시작
* 500ms마다 YAMNet 추론
* Baby cry, infant cry 또는 Crying, sobbing 점수가 임계값 넘으면 콜백
* 첫 버전은 울음 후보 감지되면 YAMNet 녹음을 멈추고, 새로 5초 wav를 떠서 서버로 보냄
*/

import android.content.Context;
import android.media.AudioRecord;

import org.tensorflow.lite.support.audio.TensorAudio;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.audio.classifier.AudioClassifier;
import org.tensorflow.lite.task.audio.classifier.Classifications;
import org.tensorflow.lite.task.core.BaseOptions;

import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class YamnetMonitor {

    public interface Listener {
        void onStatus(String message);
        void onCryDetected();
        void onError(String message);
    }

    private static final String MODEL_NAME = "yamnet.tflite";
    private static final float BABY_CRY_THRESHOLD = 0.35f;
    private static final float CRYING_THRESHOLD = 0.45f;

    private final Context context;
    private final Listener listener;

    private AudioClassifier classifier;
    private TensorAudio tensorAudio;
    private AudioRecord recorder;
    private ScheduledThreadPoolExecutor executor;

    private boolean running = false;
    private int consecutiveHits = 0;

    public YamnetMonitor(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    public boolean isRunning() {
        return running;
    }

    public void start() {
        if (running) return;

        try {
            BaseOptions baseOptions = BaseOptions.builder()
                    .setNumThreads(2)
                    .build();

            AudioClassifier.AudioClassifierOptions options =
                    AudioClassifier.AudioClassifierOptions.builder()
                            .setBaseOptions(baseOptions)
                            .setScoreThreshold(0.2f)
                            .setMaxResults(5)
                            .build();

            classifier = AudioClassifier.createFromFileAndOptions(context, MODEL_NAME, options);
            tensorAudio = classifier.createInputTensorAudio();
            recorder = classifier.createAudioRecord();

            recorder.startRecording();
            running = true;

            executor = new ScheduledThreadPoolExecutor(1);
            executor.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    classifySafe();
                }
            }, 0, 500, TimeUnit.MILLISECONDS);

            listener.onStatus("감지 모드 ON");
        } catch (Exception e) {
            running = false;
            listener.onError("YAMNet 시작 실패: " + e.getMessage());
        }
    }

    public void stop() {
        running = false;
        consecutiveHits = 0;

        try {
            if (executor != null) {
                executor.shutdownNow();
                executor = null;
            }
            if (recorder != null) {
                if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop();
                }
                recorder.release();
                recorder = null;
            }
            classifier = null;
            tensorAudio = null;
        } catch (Exception ignored) {
        }

        listener.onStatus("감지 모드 OFF");
    }

    private void classifySafe() {
        try {
            classify();
        } catch (Exception e) {
            listener.onError("YAMNet 추론 실패: " + e.getMessage());
        }
    }

    private void classify() {
        if (!running || recorder == null || tensorAudio == null || classifier == null) return;

        tensorAudio.load(recorder);
        List<Classifications> output = classifier.classify(tensorAudio);
        if (output == null || output.isEmpty()) return;

        List<Category> categories = output.get(0).getCategories();
        float babyCryScore = findScore(categories, "Baby cry, infant cry");
        float cryingScore = findScore(categories, "Crying, sobbing");

        if (babyCryScore >= BABY_CRY_THRESHOLD || cryingScore >= CRYING_THRESHOLD) {
            consecutiveHits++;
            listener.onStatus("울음 후보 감지 중... babyCry=" + babyCryScore + ", crying=" + cryingScore);

            // 2번 연속 뜨면 오탐 조금 줄이기
            if (consecutiveHits >= 2) {
                consecutiveHits = 0;
                stop();
                listener.onCryDetected();
            }
        } else {
            consecutiveHits = 0;
        }
    }

    private float findScore(List<Category> categories, String target) {
        for (Category category : categories) {
            if (target.equals(category.getLabel())) {
                return category.getScore();
            }
        }
        return 0f;
    }
}