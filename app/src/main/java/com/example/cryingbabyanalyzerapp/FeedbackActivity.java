package com.example.cryingbabyanalyzerapp;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.concurrent.Executors;

public class FeedbackActivity extends AppCompatActivity {

    private TextView tvFeedbackResult;
    private RadioGroup rgCorrect;
    private RadioGroup rgReason;
    private EditText etAction;
    private LinearLayout actionSection;
    private Button btnSubmit;
    private Button btnClose;

    private LocalCryRecordStore localRecordStore;
    private int recordId = -1;
    private String currentRecordLabel = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feedback);

        tvFeedbackResult = findViewById(R.id.tvFeedbackResult);
        rgCorrect = findViewById(R.id.rgCorrect);
        rgReason = findViewById(R.id.rgReason);
        etAction = findViewById(R.id.etAction);
        actionSection = findViewById(R.id.actionSection);
        btnSubmit = findViewById(R.id.btnSubmit);
        btnClose = findViewById(R.id.btnClose);

        localRecordStore = new LocalCryRecordStore(this);

        recordId = getIntent().getIntExtra("RECORD_ID", -1);

        if (recordId > 0) {
            CryApiService.CryRecord record = localRecordStore.getRecord(recordId);
            if (record != null && record.label != null) {
                currentRecordLabel = record.label;
            }
        }

        String resultText = getIntent().getStringExtra("RESULT_TEXT");
        if (resultText != null && !resultText.isEmpty()) {
            tvFeedbackResult.setText("현재 분석 결과:\n" + resultText);
        }

        setReasonGroupEnabled(false);

        rgCorrect.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbNo) {
                setReasonGroupEnabled(true);
            } else {
                setReasonGroupEnabled(false);
                rgReason.clearCheck();
            }

            updateActionSectionVisibility();
        });

        rgReason.setOnCheckedChangeListener(
                (group, checkedId) -> updateActionSectionVisibility()
        );

        updateActionSectionVisibility();

        btnClose.setOnClickListener(v -> finish());
        btnSubmit.setOnClickListener(v -> submitFeedback());
    }

    private void submitFeedback() {
        int correctId = rgCorrect.getCheckedRadioButtonId();

        if (correctId != R.id.rbYes && correctId != R.id.rbNo) {
            Toast.makeText(this, "분석 결과가 맞는지 선택해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (recordId <= 0) {
            Toast.makeText(
                    this,
                    "연결된 울음 기록을 찾을 수 없습니다. 새 울음 분석 후 다시 시도해주세요.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        boolean correct = correctId == R.id.rbYes;
        String actualReason = null;

        if (!correct) {
            actualReason = getSelectedReason();
            if (actualReason == null) {
                Toast.makeText(this, "실제 울음 원인을 선택해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        String action = etAction.getText().toString().trim();
        if (action.isEmpty()) {
            action = null;
        }

        btnSubmit.setEnabled(false);
        btnSubmit.setText("저장 중...");

        final String finalActualReason = actualReason;
        final String finalAction = action;

        Executors.newSingleThreadExecutor().execute(() -> {
            boolean updated = localRecordStore.updateFeedback(
                    recordId,
                    correct,
                    finalActualReason,
                    finalAction
            );

            runOnUiThread(() -> {
                if (updated) {
                    Toast.makeText(
                            FeedbackActivity.this,
                            "피드백이 태블릿 진료 기록에 저장되었습니다.",
                            Toast.LENGTH_SHORT
                    ).show();
                    finish();
                } else {
                    btnSubmit.setEnabled(true);
                    btnSubmit.setText("피드백 저장하기");
                    Toast.makeText(
                            FeedbackActivity.this,
                            "연결된 로컬 울음 기록을 찾지 못했습니다.",
                            Toast.LENGTH_LONG
                    ).show();
                }
            });
        });
    }

    private void updateActionSectionVisibility() {
        boolean show = false;
        int correctId = rgCorrect.getCheckedRadioButtonId();

        if (correctId == R.id.rbYes) {
            show = isDiscomfortLabel(currentRecordLabel);
        } else if (correctId == R.id.rbNo) {
            show = isDiscomfortLabel(getSelectedReason());
        }

        actionSection.setVisibility(show ? android.view.View.VISIBLE : android.view.View.GONE);

        if (!show) {
            etAction.setText("");
        }
    }

    private boolean isDiscomfortLabel(String label) {
        if (label == null) return false;

        String lower = label.toLowerCase();
        return lower.contains("uncomfortable")
                || lower.contains("discomfort")
                || lower.contains("belly_pain");
    }

    private String getSelectedReason() {
        int id = rgReason.getCheckedRadioButtonId();

        if (id == R.id.rbAwake) return "awake";
        if (id == R.id.rbDiaper) return "diaper";
        if (id == R.id.rbFuss) return "hug";
        if (id == R.id.rbHungry) return "hungry";
        if (id == R.id.rbSleepy) return "sleepy";
        if (id == R.id.rbUncomfortable) return "uncomfortable";

        return null;
    }

    private void setReasonGroupEnabled(boolean enabled) {
        for (int i = 0; i < rgReason.getChildCount(); i++) {
            rgReason.getChildAt(i).setEnabled(enabled);
        }
    }
}
