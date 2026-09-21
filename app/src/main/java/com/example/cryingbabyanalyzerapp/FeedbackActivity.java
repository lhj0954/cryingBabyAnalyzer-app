package com.example.cryingbabyanalyzerapp;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class FeedbackActivity extends AppCompatActivity {

    private TextView tvFeedbackResult;
    private RadioGroup rgCorrect;
    private RadioGroup rgReason;
    private EditText etAction;
    private Button btnSubmit;
    private Button btnClose;

    private CryApiService apiService;
    private int recordId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feedback);

        tvFeedbackResult = findViewById(R.id.tvFeedbackResult);
        rgCorrect = findViewById(R.id.rgCorrect);
        rgReason = findViewById(R.id.rgReason);
        etAction = findViewById(R.id.etAction);
        btnSubmit = findViewById(R.id.btnSubmit);
        btnClose = findViewById(R.id.btnClose);

        apiService = new CryApiService(BuildConfig.SERVER_IP);

        recordId = getIntent().getIntExtra("RECORD_ID", -1);

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
        });

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

        apiService.submitFeedback(
                recordId,
                correct,
                actualReason,
                action,
                new CryApiService.FeedbackCallback() {
                    @Override
                    public void onSuccess(CryApiService.CryRecord record) {
                        runOnUiThread(() -> {
                            Toast.makeText(
                                    FeedbackActivity.this,
                                    "피드백이 진료용 기록에 저장되었습니다.",
                                    Toast.LENGTH_SHORT
                            ).show();
                            finish();
                        });
                    }

                    @Override
                    public void onFailure(String message) {
                        runOnUiThread(() -> {
                            btnSubmit.setEnabled(true);
                            btnSubmit.setText("피드백 저장하기");
                            Toast.makeText(
                                    FeedbackActivity.this,
                                    message,
                                    Toast.LENGTH_LONG
                            ).show();
                        });
                    }
                }
        );
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
