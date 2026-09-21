package com.example.cryingbabyanalyzerapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MedicalRecordActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "medical_questionnaire";
    private static final String NOTES_KEY = "special_notes_json";
    private static final String MIGRATED_KEY = "special_notes_migrated";

    private TextView tvSummary;
    private TextView tvEmpty;
    private TextView tvSpecialNoteStatus;
    private LinearLayout recordListContainer;
    private LinearLayout specialNoteFormContainer;
    private LinearLayout specialNoteListContainer;
    private Button btnRefresh;
    private Button btnToggleSpecialNoteForm;
    private Button btnToggleSpecialNoteList;
    private Button btnSaveSpecialNote;
    private Spinner spinnerPeriod;
    private Spinner spinnerLabel;

    private List<CryApiService.CryRecord> allRecords = new ArrayList<>();

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
    private final Gson gson = new Gson();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_medical_record);

        bindViews();

        apiService = new CryApiService(BuildConfig.SERVER_IP);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        migrateOldQuestionnaireIfNeeded();
        refreshSpecialNoteList();
        setupRecordFilters();

        btnRefresh.setOnClickListener(v -> loadRecords());
        btnToggleSpecialNoteForm.setOnClickListener(v -> toggleSpecialNoteForm());
        btnToggleSpecialNoteList.setOnClickListener(v -> toggleSpecialNoteList());
        btnSaveSpecialNote.setOnClickListener(v -> saveSpecialNote());
    }

    private void bindViews() {
        tvSummary = findViewById(R.id.tvSummary);
        tvEmpty = findViewById(R.id.tvEmpty);
        tvSpecialNoteStatus = findViewById(R.id.tvSpecialNoteStatus);

        recordListContainer = findViewById(R.id.recordListContainer);
        specialNoteFormContainer = findViewById(R.id.specialNoteFormContainer);
        specialNoteListContainer = findViewById(R.id.specialNoteListContainer);

        btnRefresh = findViewById(R.id.btnRefresh);
        btnToggleSpecialNoteForm = findViewById(R.id.btnToggleSpecialNoteForm);
        btnToggleSpecialNoteList = findViewById(R.id.btnToggleSpecialNoteList);
        btnSaveSpecialNote = findViewById(R.id.btnSaveSpecialNote);
        spinnerPeriod = findViewById(R.id.spinnerPeriod);
        spinnerLabel = findViewById(R.id.spinnerLabel);

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

    private void toggleSpecialNoteForm() {
        boolean willShow = specialNoteFormContainer.getVisibility() != View.VISIBLE;
        specialNoteFormContainer.setVisibility(willShow ? View.VISIBLE : View.GONE);
        btnToggleSpecialNoteForm.setText(willShow ? "특이사항 메모 작성 ▲" : "특이사항 메모 작성 ▼");
    }

    private void toggleSpecialNoteList() {
        boolean willShow = specialNoteListContainer.getVisibility() != View.VISIBLE;
        specialNoteListContainer.setVisibility(willShow ? View.VISIBLE : View.GONE);
        updateSpecialNoteListButtonText();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (apiService != null) {
            loadRecords();
        }
    }

    private void setupRecordFilters() {
        String[] periods = {"오늘", "최근 7일", "최근 30일"};
        ArrayAdapter<String> periodAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                periods
        );
        periodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPeriod.setAdapter(periodAdapter);
        spinnerPeriod.setSelection(1);

        String[] labels = {
                "전체 원인",
                "배고픔",
                "졸림",
                "기저귀",
                "안아달람",
                "불편함",
                "깸"
        };
        ArrayAdapter<String> labelAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                labels
        );
        labelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLabel.setAdapter(labelAdapter);

        AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applyRecordFilters();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        };

        spinnerPeriod.setOnItemSelectedListener(listener);
        spinnerLabel.setOnItemSelectedListener(listener);
    }

    private void loadRecords() {
        tvSummary.setText("울음 기록을 정리하는 중...");
        tvEmpty.setVisibility(View.GONE);
        recordListContainer.removeAllViews();

        apiService.getRecords(new CryApiService.RecordsCallback() {
            @Override
            public void onSuccess(List<CryApiService.CryRecord> records) {
                runOnUiThread(() -> {
                    allRecords = records != null ? records : new ArrayList<>();
                    applyRecordFilters();
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

    private void applyRecordFilters() {
        if (spinnerPeriod == null || spinnerLabel == null) {
            return;
        }

        List<CryApiService.CryRecord> filtered = new ArrayList<>();
        long startTime = getSelectedStartTime();
        long now = System.currentTimeMillis();
        String selectedLabel = getSelectedRawLabel();

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA);

        for (CryApiService.CryRecord record : allRecords) {
            Date created = parseDate(format, record.created_at);
            if (created == null || created.getTime() < startTime || created.getTime() > now) {
                continue;
            }

            if (selectedLabel != null) {
                if (record.label == null || !selectedLabel.equalsIgnoreCase(record.label)) {
                    continue;
                }
            }

            filtered.add(record);
        }

        tvSummary.setText(buildFilteredClinicalSummary(filtered));
        showRecords(filtered);
    }

    private long getSelectedStartTime() {
        int position = spinnerPeriod.getSelectedItemPosition();

        if (position == 0) {
            Calendar start = Calendar.getInstance();
            start.set(Calendar.HOUR_OF_DAY, 0);
            start.set(Calendar.MINUTE, 0);
            start.set(Calendar.SECOND, 0);
            start.set(Calendar.MILLISECOND, 0);
            return start.getTimeInMillis();
        }

        int days = position == 2 ? 30 : 7;
        return System.currentTimeMillis() - (days * 24L * 60L * 60L * 1000L);
    }

    private int getSelectedPeriodDays() {
        int position = spinnerPeriod.getSelectedItemPosition();
        if (position == 0) return 1;
        if (position == 2) return 30;
        return 7;
    }

    private String getSelectedPeriodTitle() {
        Object item = spinnerPeriod.getSelectedItem();
        return item != null ? item.toString() : "최근 7일";
    }

    private String getSelectedRawLabel() {
        int position = spinnerLabel.getSelectedItemPosition();
        if (position == 1) return "hungry";
        if (position == 2) return "sleepy";
        if (position == 3) return "diaper";
        if (position == 4) return "hug";
        if (position == 5) return "uncomfortable";
        if (position == 6) return "awake";
        return null;
    }

    private String buildFilteredClinicalSummary(List<CryApiService.CryRecord> records) {
        int total = records != null ? records.size() : 0;
        int days = getSelectedPeriodDays();
        String periodTitle = getSelectedPeriodTitle();

        if (total == 0) {
            return periodTitle + " 울음 감지: 0회\n"
                    + "하루 평균: 0.0회\n"
                    + "가장 많이 감지된 원인: 없음\n"
                    + "가장 많이 감지된 시간대: 없음\n"
                    + "보호자 피드백 완료: 0회";
        }

        Map<String, Integer> labelCounts = new HashMap<>();
        int[] timeBuckets = new int[4];
        double durationSum = 0.0;
        int durationCount = 0;
        int feedbackCount = 0;

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA);

        for (CryApiService.CryRecord record : records) {
            if (record.label != null && !record.label.trim().isEmpty()) {
                labelCounts.put(record.label, labelCounts.getOrDefault(record.label, 0) + 1);
            }

            if (record.duration_sec != null) {
                durationSum += record.duration_sec;
                durationCount++;
            }

            if (record.feedback_correct != null) {
                feedbackCount++;
            }

            Date created = parseDate(format, record.created_at);
            if (created != null) {
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(created);
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

        return periodTitle + " 울음 감지: " + total + "회\n"
                + String.format(Locale.KOREA, "하루 평균: %.1f회\n", total / (double) days)
                + "가장 많이 감지된 원인: " + topLabel + "\n"
                + "가장 많이 감지된 시간대: " + bucketLabels[topBucketIndex]
                + " (" + timeBuckets[topBucketIndex] + "회)\n"
                + "평균 기록 길이: " + averageDuration + "\n"
                + "보호자 피드백 완료: " + feedbackCount + "회";
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

        if (record.feedback_correct != null) {
            TextView tvFeedback = new TextView(this);
            tvFeedback.setText(buildFeedbackText(record));
            tvFeedback.setTextSize(14);
            tvFeedback.setTextColor(0xFF2C3E50);
            tvFeedback.setPadding(0, 12, 0, 0);
            card.addView(tvFeedback);
        } else {
            Button feedbackButton = new Button(this);
            feedbackButton.setText("이 기록에 피드백 작성");
            feedbackButton.setAllCaps(false);

            LinearLayout.LayoutParams feedbackParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            feedbackParams.setMargins(0, 12, 0, 0);
            feedbackButton.setLayoutParams(feedbackParams);

            feedbackButton.setOnClickListener(v -> {
                Intent intent = new Intent(
                        MedicalRecordActivity.this,
                        FeedbackActivity.class
                );
                intent.putExtra("RECORD_ID", record.id);
                intent.putExtra(
                        "RESULT_TEXT",
                        "분석 원인: " + convertLabelToKorean(record.label)
                );
                startActivity(intent);
            });

            card.addView(feedbackButton);
        }

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

    private String buildFeedbackText(CryApiService.CryRecord record) {
        StringBuilder builder = new StringBuilder();
        builder.append("보호자 피드백: ");

        if (Boolean.TRUE.equals(record.feedback_correct)) {
            builder.append("분석 결과와 일치");
        } else {
            builder.append("분석 결과와 다름");
            if (record.actual_reason != null && !record.actual_reason.trim().isEmpty()) {
                builder.append("\n실제 원인: ")
                        .append(convertLabelToKorean(record.actual_reason));
            }
        }

        if (record.caregiver_action != null && !record.caregiver_action.trim().isEmpty()) {
            builder.append("\n대처: ").append(record.caregiver_action);
        }

        if (record.feedback_created_at != null && !record.feedback_created_at.trim().isEmpty()) {
            builder.append("\n피드백 저장: ").append(record.feedback_created_at);
        }

        return builder.toString();
    }

    private void saveSpecialNote() {
        SpecialNote note = buildNoteFromForm();
        List<SpecialNote> notes = getSpecialNotes();

        notes.add(0, note);
        saveSpecialNotes(notes);

        clearSpecialNoteForm();
        specialNoteFormContainer.setVisibility(View.GONE);
        btnToggleSpecialNoteForm.setText("특이사항 메모 작성 ▼");

        refreshSpecialNoteList();
        specialNoteListContainer.setVisibility(View.VISIBLE);
        updateSpecialNoteListButtonText();

        tvSpecialNoteStatus.setText("저장 완료 · 특이사항 목록에 추가되었습니다.");
        Toast.makeText(this, "특이사항 메모를 저장했습니다.", Toast.LENGTH_SHORT).show();
    }

    private SpecialNote buildNoteFromForm() {
        SpecialNote note = new SpecialNote();
        note.savedAt = System.currentTimeMillis();
        note.ageMonths = etAgeMonths.getText().toString().trim();
        note.birthWeight = etBirthWeight.getText().toString().trim();
        note.feedsPerDay = etFeedsPerDay.getText().toString().trim();
        note.wetDiapers = etWetDiapers.getText().toString().trim();
        note.stoolCount = etStoolCount.getText().toString().trim();
        note.sleepHours = etSleepHours.getText().toString().trim();
        note.caregiverMemo = etCaregiverMemo.getText().toString().trim();

        int feedingId = rgFeedingType.getCheckedRadioButtonId();
        if (feedingId == R.id.rbBreast) {
            note.feedingType = "모유";
        } else if (feedingId == R.id.rbFormula) {
            note.feedingType = "분유";
        } else if (feedingId == R.id.rbMixed) {
            note.feedingType = "혼합";
        } else {
            note.feedingType = "";
        }

        note.preterm = cbPreterm.isChecked();
        note.feedingDecrease = cbFeedingDecrease.isChecked();
        note.hardToSoothe = cbHardToSoothe.isChecked();
        note.fever = cbFever.isChecked();
        note.vomiting = cbVomiting.isChecked();
        note.diarrhea = cbDiarrhea.isChecked();
        note.breathing = cbBreathing.isChecked();
        note.lethargy = cbLethargy.isChecked();
        note.rash = cbRash.isChecked();
        note.constipationOrBlood = cbConstipationOrBlood.isChecked();

        return note;
    }

    private List<SpecialNote> getSpecialNotes() {
        String json = prefs.getString(NOTES_KEY, "");
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }

        try {
            Type type = new TypeToken<List<SpecialNote>>() {}.getType();
            List<SpecialNote> notes = gson.fromJson(json, type);
            return notes != null ? notes : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void saveSpecialNotes(List<SpecialNote> notes) {
        prefs.edit().putString(NOTES_KEY, gson.toJson(notes)).apply();
    }

    private void refreshSpecialNoteList() {
        List<SpecialNote> notes = getSpecialNotes();
        specialNoteListContainer.removeAllViews();

        if (notes.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("아직 저장된 특이사항 메모가 없습니다.");
            empty.setTextColor(0xFF95A5A6);
            empty.setTextSize(14);
            empty.setPadding(8, 18, 8, 18);
            specialNoteListContainer.addView(empty);
        } else {
            for (SpecialNote note : notes) {
                specialNoteListContainer.addView(createSpecialNoteView(note));
            }
        }

        updateSpecialNoteListButtonText();
    }

    private void updateSpecialNoteListButtonText() {
        int count = getSpecialNotes().size();
        boolean opened = specialNoteListContainer.getVisibility() == View.VISIBLE;
        btnToggleSpecialNoteList.setText(
                "특이사항 목록 (" + count + ")" + (opened ? " ▲" : " ▼")
        );
    }

    private View createSpecialNoteView(SpecialNote note) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(28, 24, 28, 24);
        card.setBackgroundResource(R.drawable.bg_record_card);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, 18);
        card.setLayoutParams(params);

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA);

        TextView date = new TextView(this);
        date.setText(format.format(new Date(note.savedAt)));
        date.setTextColor(0xFF7F8C8D);
        date.setTextSize(13);

        TextView title = new TextView(this);
        String memoTitle = valueOrDash(note.caregiverMemo);
        if (memoTitle.length() > 24) {
            memoTitle = memoTitle.substring(0, 24) + "...";
        }
        title.setText(memoTitle.equals("-") ? "특이사항 메모" : memoTitle);
        title.setTextColor(0xFF2C3E50);
        title.setTextSize(18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 8, 0, 8);

        TextView detail = new TextView(this);
        detail.setText(buildSpecialNoteSummary(note));
        detail.setTextColor(0xFF34495E);
        detail.setTextSize(14);
        detail.setLineSpacing(0, 1.15f);

        card.addView(date);
        card.addView(title);
        card.addView(detail);

        return card;
    }

    private String buildSpecialNoteSummary(SpecialNote note) {
        StringBuilder observations = new StringBuilder();
        appendCheckedItem(observations, note.preterm, "미숙아 출생");
        appendCheckedItem(observations, note.feedingDecrease, "수유량/횟수 감소");
        appendCheckedItem(observations, note.hardToSoothe, "평소보다 달래기 어려움");
        if (observations.length() == 0) {
            observations.append("체크된 특이사항 없음");
        }

        StringBuilder symptoms = new StringBuilder();
        appendCheckedItem(symptoms, note.fever, "발열");
        appendCheckedItem(symptoms, note.vomiting, "구토");
        appendCheckedItem(symptoms, note.diarrhea, "설사");
        appendCheckedItem(symptoms, note.breathing, "호흡 변화");
        appendCheckedItem(symptoms, note.lethargy, "처짐/반응 저하");
        appendCheckedItem(symptoms, note.rash, "발진");
        appendCheckedItem(symptoms, note.constipationOrBlood, "심한 변비/혈변 의심");
        if (symptoms.length() == 0) {
            symptoms.append("체크된 동반 증상 없음");
        }

        String age = valueOrDash(note.ageMonths);
        String birthWeight = valueOrDash(note.birthWeight);
        String feeding = valueOrDash(note.feedingType);
        String feeds = valueOrDash(note.feedsPerDay);
        String sleep = valueOrDash(note.sleepHours);
        String wet = valueOrDash(note.wetDiapers);
        String stool = valueOrDash(note.stoolCount);
        String memo = valueOrDash(note.caregiverMemo);

        return "[기본 정보]\n"
                + "생후 개월 수: " + age + "\n"
                + "출생체중: " + (birthWeight.equals("-") ? "-" : birthWeight + " g") + "\n"
                + "특이사항: " + observations + "\n\n"
                + "[수유 · 수면 · 배변]\n"
                + "수유 형태: " + feeding + "\n"
                + "하루 수유 횟수: " + feeds + "\n"
                + "하루 수면시간: " + (sleep.equals("-") ? "-" : sleep + "시간") + "\n"
                + "젖은 기저귀: " + (wet.equals("-") ? "-" : wet + "회") + "\n"
                + "대변: " + (stool.equals("-") ? "-" : stool + "회") + "\n\n"
                + "[최근 동반 증상]\n"
                + symptoms + "\n\n"
                + "[보호자 메모]\n"
                + memo;
    }

    private void clearSpecialNoteForm() {
        etAgeMonths.setText("");
        etBirthWeight.setText("");
        etFeedsPerDay.setText("");
        etWetDiapers.setText("");
        etStoolCount.setText("");
        etSleepHours.setText("");
        etCaregiverMemo.setText("");

        rgFeedingType.clearCheck();

        cbPreterm.setChecked(false);
        cbFeedingDecrease.setChecked(false);
        cbHardToSoothe.setChecked(false);
        cbFever.setChecked(false);
        cbVomiting.setChecked(false);
        cbDiarrhea.setChecked(false);
        cbBreathing.setChecked(false);
        cbLethargy.setChecked(false);
        cbRash.setChecked(false);
        cbConstipationOrBlood.setChecked(false);

        etCaregiverMemo.clearFocus();
    }

    private void migrateOldQuestionnaireIfNeeded() {
        if (prefs.getBoolean(MIGRATED_KEY, false)) {
            return;
        }

        List<SpecialNote> notes = getSpecialNotes();
        long oldSavedAt = prefs.getLong("saved_at", 0L);

        if (notes.isEmpty() && oldSavedAt > 0L) {
            SpecialNote note = new SpecialNote();
            note.savedAt = oldSavedAt;
            note.ageMonths = prefs.getString("age_months", "");
            note.birthWeight = prefs.getString("birth_weight", "");
            note.feedsPerDay = prefs.getString("feeds_per_day", "");
            note.wetDiapers = prefs.getString("wet_diapers", "");
            note.stoolCount = prefs.getString("stool_count", "");
            note.sleepHours = prefs.getString("sleep_hours", "");
            note.caregiverMemo = prefs.getString("caregiver_memo", "");

            int oldFeedingId = prefs.getInt("feeding_type", -1);
            if (oldFeedingId == R.id.rbBreast) {
                note.feedingType = "모유";
            } else if (oldFeedingId == R.id.rbFormula) {
                note.feedingType = "분유";
            } else if (oldFeedingId == R.id.rbMixed) {
                note.feedingType = "혼합";
            } else {
                note.feedingType = "";
            }

            note.preterm = prefs.getBoolean("preterm", false);
            note.feedingDecrease = prefs.getBoolean("feeding_decrease", false);
            note.hardToSoothe = prefs.getBoolean("hard_to_soothe", false);
            note.fever = prefs.getBoolean("fever", false);
            note.vomiting = prefs.getBoolean("vomiting", false);
            note.diarrhea = prefs.getBoolean("diarrhea", false);
            note.breathing = prefs.getBoolean("breathing", false);
            note.lethargy = prefs.getBoolean("lethargy", false);
            note.rash = prefs.getBoolean("rash", false);
            note.constipationOrBlood = prefs.getBoolean("constipation_or_blood", false);

            notes.add(note);
            saveSpecialNotes(notes);
        }

        prefs.edit().putBoolean(MIGRATED_KEY, true).apply();
    }

    private void appendCheckedItem(StringBuilder builder, boolean checked, String text) {
        if (!checked) return;
        if (builder.length() > 0) {
            builder.append(", ");
        }
        builder.append(text);
    }

    private String valueOrDash(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "-";
        }
        return value.trim();
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

    private static class SpecialNote {
        long savedAt;
        String ageMonths;
        String birthWeight;
        String feedingType;
        String feedsPerDay;
        String sleepHours;
        String wetDiapers;
        String stoolCount;
        String caregiverMemo;

        boolean preterm;
        boolean feedingDecrease;
        boolean hardToSoothe;
        boolean fever;
        boolean vomiting;
        boolean diarrhea;
        boolean breathing;
        boolean lethargy;
        boolean rash;
        boolean constipationOrBlood;
    }
}
