package com.example.cryingbabyanalyzerapp;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;
import java.util.Locale;

public class MedicalRecordActivity extends AppCompatActivity {

    private TextView tvSummary;
    private TextView tvEmpty;
    private LinearLayout recordListContainer;
    private Button btnRefresh;
    private CryApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_medical_record);

        tvSummary = findViewById(R.id.tvSummary);
        tvEmpty = findViewById(R.id.tvEmpty);
        recordListContainer = findViewById(R.id.recordListContainer);
        btnRefresh = findViewById(R.id.btnRefresh);

        apiService = new CryApiService(BuildConfig.SERVER_IP);

        btnRefresh.setOnClickListener(v -> loadRecords());

        loadRecords();
    }

    private void loadRecords() {
        tvSummary.setText("진료용 기록을 불러오는 중...");
        tvEmpty.setVisibility(View.GONE);
        recordListContainer.removeAllViews();

        apiService.getStats(new CryApiService.StatsCallback() {
            @Override
            public void onSuccess(CryApiService.RecordsStats stats) {
                runOnUiThread(() -> tvSummary.setText(buildSummaryText(stats)));
            }

            @Override
            public void onFailure(String message) {
                runOnUiThread(() -> tvSummary.setText("통계 조회 실패: " + message));
            }
        });

        apiService.getRecords(new CryApiService.RecordsCallback() {
            @Override
            public void onSuccess(List<CryApiService.CryRecord> records) {
                runOnUiThread(() -> showRecords(records));
            }

            @Override
            public void onFailure(String message) {
                runOnUiThread(() -> {
                    tvEmpty.setVisibility(View.VISIBLE);
                    tvEmpty.setText(message);
                    Toast.makeText(MedicalRecordActivity.this, message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private String buildSummaryText(CryApiService.RecordsStats stats) {
        if (stats == null) {
            return "총 울음 기록: 0회";
        }

        String topLabel = "없음";
        if (stats.by_label != null && !stats.by_label.isEmpty()) {
            CryApiService.LabelCount first = stats.by_label.get(0);
            topLabel = convertLabelToKorean(first.label) + " (" + first.count + "회)";
        }

        String topHour = "없음";
        if (stats.by_hour != null && !stats.by_hour.isEmpty()) {
            CryApiService.HourCount first = stats.by_hour.get(0);
            topHour = first.hour + "시대 (" + first.count + "회)";
        }

        return "총 울음 기록: " + stats.total + "회\n"
                + "가장 많이 나온 원인: " + topLabel + "\n"
                + "가장 많이 운 시간대: " + topHour;
    }

    private void showRecords(List<CryApiService.CryRecord> records) {
        recordListContainer.removeAllViews();

        if (records == null || records.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("아직 저장된 진료용 기록이 없습니다.\n울음 분석을 실행하면 자동으로 기록됩니다.");
            return;
        }

        tvEmpty.setVisibility(View.GONE);

        for (CryApiService.CryRecord record : records) {
            recordListContainer.addView(createRecordView(record));
        }
    }

    private View createRecordView(CryApiService.CryRecord record) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(28, 24, 28, 24);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, 24);
        card.setLayoutParams(cardParams);
        card.setBackgroundResource(R.drawable.bg_record_card);

        TextView tvDate = new TextView(this);
        tvDate.setText(record.created_at != null ? record.created_at : "날짜 정보 없음");
        tvDate.setTextSize(14);
        tvDate.setTextColor(0xFF7F8C8D);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(convertLabelToKorean(record.label));
        tvLabel.setTextSize(20);
        tvLabel.setTextColor(0xFF2C3E50);
        tvLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        tvLabel.setPadding(0, 10, 0, 8);

        TextView tvDetail = new TextView(this);
        tvDetail.setText(buildDetailText(record));
        tvDetail.setTextSize(14);
        tvDetail.setTextColor(0xFF34495E);

        card.addView(tvDate);
        card.addView(tvLabel);
        card.addView(tvDetail);

        return card;
    }

    private String buildDetailText(CryApiService.CryRecord record) {
        String confidenceText = "신뢰도: 없음";
        if (record.confidence != null) {
            confidenceText = String.format(Locale.KOREA, "신뢰도: %.1f%%", record.confidence * 100f);
        }

        String durationText = "분석 길이: 없음";
        if (record.duration_sec != null) {
            durationText = String.format(Locale.KOREA, "분석 길이: %.1f초", record.duration_sec);
        }

        String fileText = "오디오 파일: " + (record.saved_filename != null ? record.saved_filename : "없음");
        String messageText = "메시지: " + (record.message != null ? record.message : "없음");

        return confidenceText + "\n" + durationText + "\n" + messageText + "\n" + fileText;
    }

    private String convertLabelToKorean(String input) {
        if (input == null || input.isEmpty()) return "분석 결과 없음";

        String lower = input.toLowerCase(Locale.US);
        if (lower.contains("uncomfortable")) return "불편함";
        if (lower.contains("awake")) return "깸";
        if (lower.contains("diaper")) return "기저귀";
        if (lower.contains("hug")) return "안아달람";
        if (lower.contains("hungry")) return "배고픔";
        if (lower.contains("sleepy")) return "졸림";

        return input;
    }
}
