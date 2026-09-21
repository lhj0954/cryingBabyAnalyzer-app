package com.example.cryingbabyanalyzerapp;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MedicalRecordActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "medical_questionnaire";

    private TextView tvSummary;
    private TextView tvEmpty;
    private TextView tvQuestionnaireSaved;
    private LinearLayout recordListContainer;
    private Button btnRefresh;
    private Button btnSaveQuestionnaire;

    private EditText etAgeMonths;
    private EditText etBirthWeight;
    private EditText etFeedsPerDay;
    private EditText etWetDiapers;
    private EditText etStoolCount;
    private EditText etSleepHours;
    private EditText etCaregiverMemo;

    private RadioGroup rgFeedingType;

    private CheckBox cbPreterm;
    private CheckBox cbFeedingDecrease;
    private CheckBox cbHardToSoothe;
    private CheckBox cbFever;
    private CheckBox cbVomiting;
    private CheckBox cbDiarrhea;
    private CheckBox cbBreathing;
    private CheckBox cbLethargy;
    private CheckBox cbRash;
    private CheckBox cbConstipationOrBlood;

    private CryApiService apiService;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_medical_record);

        bindViews();

        apiService = new CryApiService(BuildConfig.SERVER_IP);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        loadQuestionnaire();

        btnRefresh.setOnClickListener(v -> loadRecords());
        btnSaveQuestionnaire.setOnClickListener(v -> saveQuestionnaire());

        loadRecords();
    }

    private void bindViews() {
        tvSummary = findViewById(R.id.tvSummary);
        tvEmpty = findViewById(R.id.tvEmpty);
        tvQuestionnaireSaved = findViewById(R.id.tvQuestionnaireSaved);
        recordListContainer = findViewById(R.id.recordListContainer);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnSaveQuestionnaire = findViewById(R.id.btnSaveQuestionnaire);

        etAgeMonths = findViewById(R.id.etAgeMonths);
        etBirthWeight = findViewById(R.id.etBirthWeight);
        etFeedsPerDay = findViewById(R.id.etFeedsPerDay);
        etWetDiapers = findViewById(R.id.etWetDiapers);
        etStoolCount = findViewById(R.id.etStoolCount);
        etSleepHours = findViewById(R.id.etSleepHours);
        etCaregiverMemo = findViewById(R.id.etCaregiverMemo);

        rgFeedingType = findViewById(R.id.rgFeedingType);

        cbPreterm = findViewById(R.id.cbPreterm);
        cbFeedingDecrease = findViewById(R.id.cbFeedingDecrease);
        cbHardToSoothe = findViewById(R.id.cbHardToSoothe);
        cbFever = findViewById(R.id.cbFever);
        cbVomiting = findViewById(R.id.cbVomiting);
        cbDiarrhea = findViewById(R.id.cbDiarrhea);
        cbBreathing = findViewById(R.id.cbBreathing);
        cbLethargy = findViewById(R.id.cbLethargy);
        cbRash = findViewById(R.id.cbRash);
        cbConstipationOrBlood = findViewById(R.id.cbConstipationOrBlood);
    }

    private void loadRecords() {
        tvSummary.setText("최근 7일 울음 기록을 정리하는 중...");
        tvEmpty.setVisibility(View.GONE);
        recordListContainer.removeAllViews();

        apiService.getRecords(new CryApiService.RecordsCallback() {
            @Override
            public void onSuccess(List<CryApiService.CryRecord> records) {
                runOnUiThread(() -> {
                    tvSummary.setText(buildClinicalSummary(records));
                    showRecords(records);
                });
            }

            @Override
            public void onFailure(String message) {
                runOnUiThread(() -> {
                    tvSummary.setText("울음 기록 조회 실패: " + message);
                    tvEmpty.setVisibility(View.VISIBLE);
                    tvEmpty.setText("서버에서 진료용 기록을 불러오지 못했습니다.");
                    Toast.makeText(MedicalRecordActivity.this, message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private String buildClinicalSummary(List<CryApiService.CryRecord> records) {
        if (records == null || records.isEmpty()) {
            return "최근 7일 울음 감지: 0회\n"
                    + "하루 평균: 0.0회\n"
                    + "가장 많이 감지된 원인: 없음\n"
                    + "가장 많이 감지된 시간대: 없음\n"
                    + "평균 기록 길이: 없음";
        }

        long now = System.currentTimeMillis();
        long sevenDaysAgo = now - (7L * 24L * 60L * 60L * 1000L);

        int total = 0;
        double durationSum = 0.0;
        int durationCount = 0;

        Map<String, Integer> labelCounts = new HashMap<>();
        int[] timeBuckets = new int[4];

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA);

        for (CryApiService.CryRecord record : records) {
            Date createdDate = parseDate(format, record.created_at);
            if (createdDate == null || createdDate.getTime() < sevenDaysAgo || createdDate.getTime() > now) {
                continue;
            }

            total++;

            if (record.label != null && !record.label.trim().isEmpty()) {
                labelCounts.put(record.label, labelCounts.getOrDefault(record.label, 0) + 1);
            }

            if (record.duration_sec != null) {
                durationSum += record.duration_sec;
                durationCount++;
            }

            Calendar calendar = Calendar.getInstance();
            calendar.setTime(createdDate);
            int hour = calendar.get(Calendar.HOUR_OF_DAY);

            if (hour < 6) {
                timeBuckets[0]++;
            } else if (hour < 12) {
                timeBuckets[1]++;
            } else if (hour < 18) {
                timeBuckets[2]++;
            } else {
                timeBuckets[3]++;
            }
        }

        if (total == 0) {
            return "최근 7일 울음 감지: 0회\n"
                    + "하루 평균: 0.0회\n"
                    + "가장 많이 감지된 원인: 없음\n"
                    + "가장 많이 감지된 시간대: 없음\n"
                    + "평균 기록 길이: 없음";
        }

        String topLabel = "없음";
        int topLabelCount = 0;
        for (Map.Entry<String, Integer> entry : labelCounts.entrySet()) {
            if (entry.getValue() > topLabelCount) {
                topLabelCount = entry.getValue();
                topLabel = convertLabelToKorean(entry.getKey()) + " (" + entry.getValue() + "회)";
            }
        }

        String[] bucketLabels = {"00~06시", "06~12시", "12~18시", "18~24시"};
        int topBucketIndex = 0;
        for (int i = 1; i < timeBuckets.length; i++) {
            if (timeBuckets[i] > timeBuckets[topBucketIndex]) {
                topBucketIndex = i;
            }
        }

        String averageDuration = durationCount > 0
                ? String.format(Locale.KOREA, "%.1f초", durationSum / durationCount)
                : "없음";

        return "최근 7일 울음 감지: " + total + "회\n"
                + String.format(Locale.KOREA, "하루 평균: %.1f회\n", total / 7.0)
                + "가장 많이 감지된 원인: " + topLabel + "\n"
                + "가장 많이 감지된 시간대: " + bucketLabels[topBucketIndex]
                + " (" + timeBuckets[topBucketIndex] + "회)\n"
                + "평균 기록 길이: " + averageDuration;
    }

    private Date parseDate(SimpleDateFormat format, String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return format.parse(value);
        } catch (ParseException e) {
            return null;
        }
    }

    private void showRecords(List<CryApiService.CryRecord> records) {
        recordListContainer.removeAllViews();

        if (records == null || records.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText("아직 저장된 울음 기록이 없습니다.\n울음 분석을 실행하면 자동으로 기록됩니다.");
            return;
        }

        tvEmpty.setVisibility(View.GONE);

        int displayCount = Math.min(records.size(), 20);
        for (int i = 0; i < displayCount; i++) {
            recordListContainer.addView(createRecordView(records.get(i)));
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
        cardParams.setMargins(0, 0, 0, 20);
        card.setLayoutParams(cardParams);
        card.setBackgroundResource(R.drawable.bg_record_card);

        TextView tvDate = new TextView(this);
        tvDate.setText(record.created_at != null ? record.created_at : "날짜 정보 없음");
        tvDate.setTextSize(14);
        tvDate.setTextColor(0xFF7F8C8D);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(convertLabelToKorean(record.label));
        tvLabel.setTextSize(19);
        tvLabel.setTextColor(0xFF2C3E50);
        tvLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        tvLabel.setPadding(0, 8, 0, 6);

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

        String durationText = "기록 길이: 없음";
        if (record.duration_sec != null) {
            durationText = String.format(Locale.KOREA, "기록 길이: %.1f초", record.duration_sec);
        }

        return confidenceText + " · " + durationText;
    }

    private void saveQuestionnaire() {
        SharedPreferences.Editor editor = prefs.edit();

        editor.putString("age_months", etAgeMonths.getText().toString().trim());
        editor.putString("birth_weight", etBirthWeight.getText().toString().trim());
        editor.putString("feeds_per_day", etFeedsPerDay.getText().toString().trim());
        editor.putString("wet_diapers", etWetDiapers.getText().toString().trim());
        editor.putString("stool_count", etStoolCount.getText().toString().trim());
        editor.putString("sleep_hours", etSleepHours.getText().toString().trim());
        editor.putString("caregiver_memo", etCaregiverMemo.getText().toString().trim());

        editor.putInt("feeding_type", rgFeedingType.getCheckedRadioButtonId());

        editor.putBoolean("preterm", cbPreterm.isChecked());
        editor.putBoolean("feeding_decrease", cbFeedingDecrease.isChecked());
        editor.putBoolean("hard_to_soothe", cbHardToSoothe.isChecked());
        editor.putBoolean("fever", cbFever.isChecked());
        editor.putBoolean("vomiting", cbVomiting.isChecked());
        editor.putBoolean("diarrhea", cbDiarrhea.isChecked());
        editor.putBoolean("breathing", cbBreathing.isChecked());
        editor.putBoolean("lethargy", cbLethargy.isChecked());
        editor.putBoolean("rash", cbRash.isChecked());
        editor.putBoolean("constipation_or_blood", cbConstipationOrBlood.isChecked());

        editor.putLong("saved_at", System.currentTimeMillis());
        editor.apply();

        tvQuestionnaireSaved.setText("문진 정보가 이 기기에 저장되었습니다.");
        Toast.makeText(this, "문진 정보를 저장했습니다.", Toast.LENGTH_SHORT).show();
    }

    private void loadQuestionnaire() {
        etAgeMonths.setText(prefs.getString("age_months", ""));
        etBirthWeight.setText(prefs.getString("birth_weight", ""));
        etFeedsPerDay.setText(prefs.getString("feeds_per_day", ""));
        etWetDiapers.setText(prefs.getString("wet_diapers", ""));
        etStoolCount.setText(prefs.getString("stool_count", ""));
        etSleepHours.setText(prefs.getString("sleep_hours", ""));
        etCaregiverMemo.setText(prefs.getString("caregiver_memo", ""));

        int feedingTypeId = prefs.getInt("feeding_type", -1);
        if (feedingTypeId != -1) {
            rgFeedingType.check(feedingTypeId);
        }

        cbPreterm.setChecked(prefs.getBoolean("preterm", false));
        cbFeedingDecrease.setChecked(prefs.getBoolean("feeding_decrease", false));
        cbHardToSoothe.setChecked(prefs.getBoolean("hard_to_soothe", false));
        cbFever.setChecked(prefs.getBoolean("fever", false));
        cbVomiting.setChecked(prefs.getBoolean("vomiting", false));
        cbDiarrhea.setChecked(prefs.getBoolean("diarrhea", false));
        cbBreathing.setChecked(prefs.getBoolean("breathing", false));
        cbLethargy.setChecked(prefs.getBoolean("lethargy", false));
        cbRash.setChecked(prefs.getBoolean("rash", false));
        cbConstipationOrBlood.setChecked(prefs.getBoolean("constipation_or_blood", false));

        long savedAt = prefs.getLong("saved_at", 0L);
        if (savedAt > 0L) {
            SimpleDateFormat displayFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA);
            tvQuestionnaireSaved.setText("마지막 저장: " + displayFormat.format(new Date(savedAt)));
        }
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
