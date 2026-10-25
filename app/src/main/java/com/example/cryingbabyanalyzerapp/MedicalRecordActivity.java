package com.example.cryingbabyanalyzerapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.MotionEvent;
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

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
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
import java.util.concurrent.Executors;

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
    private Button btnPrevDate;
    private Button btnNextDate;
    private Spinner spinnerLabel;
    private TextView tvSelectedDate;
    private LinearLayout dateNavigationCard;
    private BottomNavigationView bottomNavigationView;

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

    private LocalCryRecordStore localRecordStore;
    private SharedPreferences prefs;
    private final Gson gson = new Gson();

    private final Calendar selectedDate = Calendar.getInstance();
    private float dateTouchDownX = 0f;
    private boolean dateTransitionRunning = false;
    private MediaPlayer activePlayer;
    private Button activePlaybackButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_medical_record);

        bindViews();

        localRecordStore = new LocalCryRecordStore(this);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        migrateOldQuestionnaireIfNeeded();
        refreshSpecialNoteList();
        setupRecordFilters();
        setupDateNavigation();
        setupBottomNavigation();

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
        btnPrevDate = findViewById(R.id.btnPrevDate);
        btnNextDate = findViewById(R.id.btnNextDate);
        spinnerLabel = findViewById(R.id.spinnerLabel);
        tvSelectedDate = findViewById(R.id.tvSelectedDate);
        dateNavigationCard = findViewById(R.id.dateNavigationCard);
        bottomNavigationView = findViewById(R.id.bottom_navigation);

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

    private void setupBottomNavigation() {
        if (bottomNavigationView == null) return;

        bottomNavigationView.setSelectedItemId(R.id.nav_medical_record);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.nav_medical_record) {
                return true;
            }

            if (itemId == R.id.nav_home) {
                startActivity(new Intent(MedicalRecordActivity.this, MainActivity.class));
                finish();
                return true;
            }

            if (itemId == R.id.nav_profile) {
                startActivity(new Intent(MedicalRecordActivity.this, ProfileActivity.class));
                finish();
                return true;
            }

            return false;
        });
    }

    private void toggleSpecialNoteForm() {
        boolean willShow = specialNoteFormContainer.getVisibility() != View.VISIBLE;

        if (willShow) {
            applyProfileDefaultsToSpecialNoteForm();
        }

        specialNoteFormContainer.setVisibility(willShow ? View.VISIBLE : View.GONE);
        btnToggleSpecialNoteForm.setText(willShow ? "특이사항 메모 작성 ▲" : "특이사항 메모 작성 ▼");
    }

    private void applyProfileDefaultsToSpecialNoteForm() {
        if (!BabyProfilePreferences.hasProfile(this)) {
            return;
        }

        int ageMonths = BabyProfilePreferences.getAgeMonths(this);
        if (ageMonths >= 0 && etAgeMonths.getText().toString().trim().isEmpty()) {
            etAgeMonths.setText(String.valueOf(ageMonths));
        }

        String birthWeight = BabyProfilePreferences.getBirthWeight(this);
        if (!birthWeight.isEmpty() && etBirthWeight.getText().toString().trim().isEmpty()) {
            etBirthWeight.setText(birthWeight);
        }

        if (BabyProfilePreferences.isPreterm(this)) {
            cbPreterm.setChecked(true);
        }
    }

    private void toggleSpecialNoteList() {
        boolean willShow = specialNoteListContainer.getVisibility() != View.VISIBLE;
        specialNoteListContainer.setVisibility(willShow ? View.VISIBLE : View.GONE);
        updateSpecialNoteListButtonText();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bottomNavigationView != null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_medical_record);
        }
        if (localRecordStore != null) {
            loadRecords();
        }
    }

    private void setupRecordFilters() {
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

        spinnerLabel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applyRecordFilters();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void setupDateNavigation() {
        selectedDate.setTimeInMillis(System.currentTimeMillis());
        normalizeSelectedDate();
        updateSelectedDateUi();

        btnPrevDate.setOnClickListener(v -> animateDateChange(-1));
        btnNextDate.setOnClickListener(v -> animateDateChange(1));

        dateNavigationCard.setOnTouchListener((v, event) -> {
            if (dateTransitionRunning) {
                return true;
            }

            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                dateTouchDownX = event.getX();
                cancelDatePageAnimations();
                return true;
            }

            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                float deltaX = event.getX() - dateTouchDownX;

                // 손가락을 따라 페이지가 조금씩 움직이도록 해 스와이프 느낌을 줍니다.
                float dragX = deltaX * 0.55f;

                // 오늘에서 미래 날짜로 넘기는 동작은 탄성처럼 제한합니다.
                if (deltaX < 0 && isTodaySelected()) {
                    dragX *= 0.25f;
                }

                float width = Math.max(
                        dateNavigationCard.getWidth(),
                        getResources().getDisplayMetrics().widthPixels * 0.7f
                );
                float alpha = 1f - Math.min(
                        0.30f,
                        Math.abs(dragX) / width * 0.45f
                );

                setDatePageTransform(dragX, alpha);
                return true;
            }

            if (event.getAction() == MotionEvent.ACTION_UP) {
                float deltaX = event.getX() - dateTouchDownX;

                if (Math.abs(deltaX) >= dpToPx(64)) {
                    int days = deltaX > 0 ? -1 : 1;

                    if (days > 0 && isTodaySelected()) {
                        animateDatePageBack();
                    } else {
                        animateDateChange(days);
                    }
                } else {
                    animateDatePageBack();
                }

                v.performClick();
                return true;
            }

            if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                animateDatePageBack();
                return true;
            }

            return true;
        });
    }

    private void animateDateChange(int days) {
        if (dateTransitionRunning) {
            return;
        }

        if (days > 0 && isTodaySelected()) {
            animateDatePageBack();
            return;
        }

        dateTransitionRunning = true;
        releaseActivePlayer();

        float width = Math.max(
                dateNavigationCard.getWidth(),
                getResources().getDisplayMetrics().widthPixels * 0.82f
        );

        // 이전 날짜는 현재 페이지가 오른쪽으로, 다음 날짜는 왼쪽으로 빠집니다.
        float outX = days < 0 ? width : -width;
        float inX = -outX;

        tvSummary.animate()
                .translationX(outX)
                .alpha(0.12f)
                .setDuration(150L)
                .start();

        recordListContainer.animate()
                .translationX(outX)
                .alpha(0.12f)
                .setDuration(150L)
                .start();

        dateNavigationCard.animate()
                .translationX(outX)
                .alpha(0.12f)
                .setDuration(150L)
                .withEndAction(() -> {
                    moveSelectedDate(days);
                    updateSelectedDateUi();
                    applyRecordFilters();

                    setDatePageTransform(inX, 0.12f);

                    tvSummary.animate()
                            .translationX(0f)
                            .alpha(1f)
                            .setDuration(230L)
                            .start();

                    recordListContainer.animate()
                            .translationX(0f)
                            .alpha(1f)
                            .setDuration(230L)
                            .start();

                    dateNavigationCard.animate()
                            .translationX(0f)
                            .alpha(1f)
                            .setDuration(230L)
                            .withEndAction(() -> dateTransitionRunning = false)
                            .start();
                })
                .start();
    }

    private void moveSelectedDate(int days) {
        selectedDate.add(Calendar.DAY_OF_MONTH, days);

        Calendar today = Calendar.getInstance();
        normalizeCalendar(today);

        if (selectedDate.after(today)) {
            selectedDate.setTimeInMillis(today.getTimeInMillis());
        }
    }

    private void animateDatePageBack() {
        cancelDatePageAnimations();

        tvSummary.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(180L)
                .start();

        recordListContainer.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(180L)
                .start();

        dateNavigationCard.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(180L)
                .start();
    }

    private void setDatePageTransform(float translationX, float alpha) {
        dateNavigationCard.setTranslationX(translationX);
        tvSummary.setTranslationX(translationX);
        recordListContainer.setTranslationX(translationX);

        dateNavigationCard.setAlpha(alpha);
        tvSummary.setAlpha(alpha);
        recordListContainer.setAlpha(alpha);
    }

    private void cancelDatePageAnimations() {
        dateNavigationCard.animate().cancel();
        tvSummary.animate().cancel();
        recordListContainer.animate().cancel();
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private void updateSelectedDateUi() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy년 M월 d일 (E)", Locale.KOREA);
        String prefix = isTodaySelected() ? "오늘 · " : "";
        tvSelectedDate.setText(prefix + dateFormat.format(selectedDate.getTime()));
        btnNextDate.setEnabled(!isTodaySelected());
        btnNextDate.setAlpha(isTodaySelected() ? 0.35f : 1f);
    }

    private boolean isTodaySelected() {
        Calendar today = Calendar.getInstance();
        normalizeCalendar(today);
        return selectedDate.getTimeInMillis() == today.getTimeInMillis();
    }

    private void normalizeSelectedDate() {
        normalizeCalendar(selectedDate);
    }

    private void normalizeCalendar(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    private void loadRecords() {
        tvSummary.setText("태블릿에 저장된 울음 기록을 정리하는 중...");
        tvEmpty.setVisibility(View.GONE);
        recordListContainer.removeAllViews();

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                List<CryApiService.CryRecord> records = localRecordStore.listRecords();

                runOnUiThread(() -> {
                    allRecords = records != null ? records : new ArrayList<>();
                    applyRecordFilters();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvSummary.setText("태블릿 울음 기록을 불러오지 못했습니다.");
                    tvEmpty.setVisibility(View.VISIBLE);
                    tvEmpty.setText("기기에 저장된 기록을 확인하지 못했습니다.");
                    Toast.makeText(
                            MedicalRecordActivity.this,
                            "로컬 기록 조회 실패",
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }
        });
    }

    private void applyRecordFilters() {
        if (spinnerLabel == null) {
            return;
        }

        List<CryApiService.CryRecord> filtered = new ArrayList<>();
        String selectedLabel = getSelectedRawLabel();

        long startTime = selectedDate.getTimeInMillis();
        Calendar endCalendar = (Calendar) selectedDate.clone();
        endCalendar.add(Calendar.DAY_OF_MONTH, 1);
        long endTime = endCalendar.getTimeInMillis();

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA);

        for (CryApiService.CryRecord record : allRecords) {
            Date created = parseDate(format, record.created_at);
            if (created == null
                    || created.getTime() < startTime
                    || created.getTime() >= endTime) {
                continue;
            }

            if (selectedLabel != null) {
                if (record.label == null || !selectedLabel.equalsIgnoreCase(record.label)) {
                    continue;
                }
            }

            filtered.add(record);
        }

        tvSummary.setText(
                buildDailyClinicalSummary(filtered)
                        + "\n\n"
                        + buildWeekdaySummary(allRecords)
        );

        showRecords(filtered);
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

    private String buildDailyClinicalSummary(List<CryApiService.CryRecord> records) {
        int total = records != null ? records.size() : 0;
        String selectedDateText = new SimpleDateFormat(
                "M월 d일",
                Locale.KOREA
        ).format(selectedDate.getTime());

        if (total == 0) {
            return selectedDateText + " 울음 감지: 0회\n"
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
                labelCounts.put(
                        record.label,
                        labelCounts.getOrDefault(record.label, 0) + 1
                );
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
                timeBuckets[getTimeBucketIndex(
                        calendar.get(Calendar.HOUR_OF_DAY)
                )]++;
            }
        }

        String topLabel = "없음";
        int topLabelCount = 0;

        for (Map.Entry<String, Integer> entry : labelCounts.entrySet()) {
            if (entry.getValue() > topLabelCount) {
                topLabelCount = entry.getValue();
                topLabel = convertLabelToKorean(entry.getKey())
                        + " (" + entry.getValue() + "회)";
            }
        }

        String[] bucketLabels = {
                "새벽 00~06시",
                "오전 06~12시",
                "낮 12~18시",
                "저녁 18~24시"
        };

        int topBucketIndex = 0;
        for (int i = 1; i < timeBuckets.length; i++) {
            if (timeBuckets[i] > timeBuckets[topBucketIndex]) {
                topBucketIndex = i;
            }
        }

        String averageDuration = durationCount > 0
                ? String.format(
                        Locale.KOREA,
                        "%.1f초",
                        durationSum / durationCount
                )
                : "없음";

        return selectedDateText + " 울음 감지: " + total + "회\n"
                + "가장 많이 감지된 원인: " + topLabel + "\n"
                + "가장 많이 감지된 시간대: "
                + bucketLabels[topBucketIndex]
                + " (" + timeBuckets[topBucketIndex] + "회)\n"
                + "평균 기록 길이: " + averageDuration + "\n"
                + "보호자 피드백 완료: " + feedbackCount + "회";
    }

    private String buildWeekdaySummary(List<CryApiService.CryRecord> records) {
        int[] counts = new int[7];

        Calendar sevenDaysAgo = Calendar.getInstance();
        sevenDaysAgo.add(Calendar.DAY_OF_MONTH, -6);
        normalizeCalendar(sevenDaysAgo);

        long start = sevenDaysAgo.getTimeInMillis();
        long end = System.currentTimeMillis();

        SimpleDateFormat format = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.KOREA
        );

        if (records != null) {
            for (CryApiService.CryRecord record : records) {
                Date created = parseDate(format, record.created_at);
                if (created == null
                        || created.getTime() < start
                        || created.getTime() > end) {
                    continue;
                }

                Calendar calendar = Calendar.getInstance();
                calendar.setTime(created);

                int day = calendar.get(Calendar.DAY_OF_WEEK);
                int index;

                if (day == Calendar.MONDAY) index = 0;
                else if (day == Calendar.TUESDAY) index = 1;
                else if (day == Calendar.WEDNESDAY) index = 2;
                else if (day == Calendar.THURSDAY) index = 3;
                else if (day == Calendar.FRIDAY) index = 4;
                else if (day == Calendar.SATURDAY) index = 5;
                else index = 6;

                counts[index]++;
            }
        }

        return "최근 7일 요일별 횟수\n"
                + "월 " + counts[0] + " · "
                + "화 " + counts[1] + " · "
                + "수 " + counts[2] + " · "
                + "목 " + counts[3] + " · "
                + "금 " + counts[4] + " · "
                + "토 " + counts[5] + " · "
                + "일 " + counts[6];
    }

    private int getTimeBucketIndex(int hour) {
        if (hour < 6) return 0;
        if (hour < 12) return 1;
        if (hour < 18) return 2;
        return 3;
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

        List<List<CryApiService.CryRecord>> buckets = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            buckets.add(new ArrayList<>());
        }

        SimpleDateFormat format = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.KOREA
        );

        if (records != null) {
            for (CryApiService.CryRecord record : records) {
                Date created = parseDate(format, record.created_at);
                if (created == null) continue;

                Calendar calendar = Calendar.getInstance();
                calendar.setTime(created);
                int bucket = getTimeBucketIndex(
                        calendar.get(Calendar.HOUR_OF_DAY)
                );

                buckets.get(bucket).add(record);
            }
        }

        int total = records != null ? records.size() : 0;
        tvEmpty.setVisibility(total == 0 ? View.VISIBLE : View.GONE);

        if (total == 0) {
            tvEmpty.setText(
                    "선택한 날짜에 저장된 울음 기록이 없습니다.\n"
                            + "좌우로 날짜를 이동하거나 새 울음 분석을 실행해보세요."
            );
        }

        addTimeSection("새벽 · 00:00 ~ 06:00", buckets.get(0));
        addTimeSection("오전 · 06:00 ~ 12:00", buckets.get(1));
        addTimeSection("낮 · 12:00 ~ 18:00", buckets.get(2));
        addTimeSection("저녁 · 18:00 ~ 24:00", buckets.get(3));
    }

    private void addTimeSection(
            String title,
            List<CryApiService.CryRecord> records
    ) {
        TextView header = new TextView(this);
        header.setText(title + "  ·  " + records.size() + "회");
        header.setTextSize(15);
        header.setTextColor(0xFFD96B7E);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setPadding(20, 16, 20, 16);
        header.setBackgroundResource(R.drawable.bg_baby_soft_pink);

        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        headerParams.setMargins(0, 18, 0, 10);
        header.setLayoutParams(headerParams);

        recordListContainer.addView(header);

        if (records.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("이 시간대에는 울음 기록이 없어요.");
            empty.setTextColor(0xFF8D8481);
            empty.setTextSize(13);
            empty.setPadding(12, 8, 12, 10);
            recordListContainer.addView(empty);
            return;
        }

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
        cardParams.setMargins(0, 0, 0, 16);
        card.setLayoutParams(cardParams);
        card.setBackgroundResource(R.drawable.bg_record_card);

        TextView tvDate = new TextView(this);
        tvDate.setText(formatRecordTime(record.created_at));
        tvDate.setTextSize(14);
        tvDate.setTextColor(0xFF7F8C8D);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(buildRecordTitle(record));
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

        File audioFile = record.audio_path != null
                ? new File(record.audio_path)
                : null;

        if (audioFile != null && audioFile.exists()) {
            Button playbackButton = new Button(this);
            playbackButton.setText("▶ 녹음 재생");
            playbackButton.setAllCaps(false);

            LinearLayout.LayoutParams playbackParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            playbackParams.setMargins(0, 12, 0, 0);
            playbackButton.setLayoutParams(playbackParams);

            playbackButton.setOnClickListener(
                    v -> playRecordAudio(record, playbackButton)
            );

            card.addView(playbackButton);
        }

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
            feedbackParams.setMargins(0, 10, 0, 0);
            feedbackButton.setLayoutParams(feedbackParams);

            feedbackButton.setOnClickListener(v -> {
                Intent intent = new Intent(
                        MedicalRecordActivity.this,
                        FeedbackActivity.class
                );
                intent.putExtra("RECORD_ID", record.id);
                intent.putExtra(
                        "RESULT_TEXT",
                        buildRecordResultText(record)
                );
                startActivity(intent);
            });

            card.addView(feedbackButton);
        }

        return card;
    }

    private String buildRecordTitle(CryApiService.CryRecord record) {
        boolean showTopTwo = record.confidence != null
                && record.confidence < CryPredictionUtils.LOW_CONFIDENCE_THRESHOLD
                && record.secondary_label != null
                && record.secondary_confidence != null;

        if (showTopTwo) {
            return convertLabelToKorean(record.label)
                    + " / "
                    + convertLabelToKorean(record.secondary_label);
        }

        return convertLabelToKorean(record.label);
    }

    private String buildRecordResultText(CryApiService.CryRecord record) {
        if (record.confidence != null
                && record.confidence < CryPredictionUtils.LOW_CONFIDENCE_THRESHOLD
                && record.secondary_label != null
                && record.secondary_confidence != null) {

            return String.format(
                    Locale.KOREA,
                    "1순위: %s %.0f%%\n2순위: %s %.0f%%",
                    convertLabelToKorean(record.label),
                    record.confidence * 100f,
                    convertLabelToKorean(record.secondary_label),
                    record.secondary_confidence * 100f
            );
        }

        return "분석 원인: " + convertLabelToKorean(record.label);
    }

    private String buildDetailText(CryApiService.CryRecord record) {
        String confidenceText = "신뢰도: 없음";

        if (record.confidence != null) {
            confidenceText = String.format(
                    Locale.KOREA,
                    "1순위 신뢰도: %.1f%%",
                    record.confidence * 100f
            );
        }

        if (record.confidence != null
                && record.confidence < CryPredictionUtils.LOW_CONFIDENCE_THRESHOLD
                && record.secondary_label != null
                && record.secondary_confidence != null) {

            confidenceText += String.format(
                    Locale.KOREA,
                    " · 2순위 %s %.1f%%",
                    convertLabelToKorean(record.secondary_label),
                    record.secondary_confidence * 100f
            );
        }

        String durationText = "기록 길이: 없음";
        if (record.duration_sec != null) {
            durationText = String.format(
                    Locale.KOREA,
                    "기록 길이: %.1f초",
                    record.duration_sec
            );
        }

        return confidenceText + "\n" + durationText;
    }

    private String formatRecordTime(String createdAt) {
        if (createdAt == null || createdAt.trim().isEmpty()) {
            return "시간 정보 없음";
        }

        SimpleDateFormat input = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.KOREA
        );
        SimpleDateFormat output = new SimpleDateFormat(
                "a h:mm",
                Locale.KOREA
        );

        Date date = parseDate(input, createdAt);
        return date != null ? output.format(date) : createdAt;
    }

    private void playRecordAudio(
            CryApiService.CryRecord record,
            Button button
    ) {
        if (record.audio_path == null || record.audio_path.trim().isEmpty()) {
            Toast.makeText(
                    this,
                    "저장된 녹음 파일을 찾을 수 없습니다.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        File file = new File(record.audio_path);
        if (!file.exists()) {
            Toast.makeText(
                    this,
                    "녹음 파일이 기기에서 삭제되었거나 이동되었습니다.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        if (activePlayer != null && activePlaybackButton == button) {
            releaseActivePlayer();
            return;
        }

        releaseActivePlayer();

        try {
            MediaPlayer player = new MediaPlayer();
            player.setDataSource(file.getAbsolutePath());
            player.prepare();

            activePlayer = player;
            activePlaybackButton = button;
            button.setText("■ 재생 중지");

            player.setOnCompletionListener(mp -> releaseActivePlayer());
            player.setOnErrorListener((mp, what, extra) -> {
                releaseActivePlayer();
                Toast.makeText(
                        MedicalRecordActivity.this,
                        "녹음 파일을 재생하지 못했습니다.",
                        Toast.LENGTH_SHORT
                ).show();
                return true;
            });

            player.start();

        } catch (Exception e) {
            releaseActivePlayer();
            Toast.makeText(
                    this,
                    "녹음 파일을 재생하지 못했습니다.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void releaseActivePlayer() {
        if (activePlayer != null) {
            try {
                if (activePlayer.isPlaying()) {
                    activePlayer.stop();
                }
            } catch (Exception ignored) {
            }

            try {
                activePlayer.release();
            } catch (Exception ignored) {
            }

            activePlayer = null;
        }

        if (activePlaybackButton != null) {
            activePlaybackButton.setText("▶ 녹음 재생");
            activePlaybackButton = null;
        }
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
        card.setClickable(true);
        card.setFocusable(true);

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

        String baseTitle = memoTitle.equals("-") ? "특이사항 메모" : memoTitle;
        title.setText(baseTitle + "  ▼");
        title.setTextColor(0xFF2C3E50);
        title.setTextSize(18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 8, 0, 0);

        TextView detail = new TextView(this);
        detail.setText(buildSpecialNoteSummary(note));
        detail.setTextColor(0xFF34495E);
        detail.setTextSize(14);
        detail.setLineSpacing(0, 1.15f);
        detail.setPadding(0, 16, 0, 0);
        detail.setVisibility(View.GONE);

        card.addView(date);
        card.addView(title);
        card.addView(detail);

        card.setOnClickListener(v -> {
            boolean opening = detail.getVisibility() != View.VISIBLE;
            detail.setVisibility(opening ? View.VISIBLE : View.GONE);
            title.setText(baseTitle + (opening ? "  ▲" : "  ▼"));
        });

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

    @Override
    protected void onDestroy() {
        releaseActivePlayer();
        super.onDestroy();
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
