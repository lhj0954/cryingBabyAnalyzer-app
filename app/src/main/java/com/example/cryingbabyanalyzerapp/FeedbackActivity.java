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
    private RadioGroup rgCorrect, rgReason;
    private EditText etAction;
    private Button btnSubmit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feedback);

        tvFeedbackResult = findViewById(R.id.tvFeedbackResult);
        rgCorrect = findViewById(R.id.rgCorrect);
        rgReason = findViewById(R.id.rgReason);
        etAction = findViewById(R.id.etAction);
        btnSubmit = findViewById(R.id.btnSubmit);

        // 1. MainActivity에서 넘겨준 분석 결과 텍스트를 받아서 띄워줌
        String resultText = getIntent().getStringExtra("RESULT_TEXT");
        if (resultText != null && !resultText.isEmpty()) {
            tvFeedbackResult.setText("현재 분석 결과:\n" + resultText);
        }

        // 2. 디테일: 초기에는 2번 질문(이유 선택) 비활성화
        setReasonGroupEnabled(false);

        // '틀렸어요'를 눌렀을 때만 2번 질문 활성화
        rgCorrect.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbNo) {
                setReasonGroupEnabled(true);
            } else {
                setReasonGroupEnabled(false);
                rgReason.clearCheck(); // 선택 초기화
            }
        });

        // 3. 제출 버튼 클릭 시 동작
        btnSubmit.setOnClickListener(v -> {
            // (나중에 여기에 서버로 데이터를 전송하는 코드를 추가하면 됩니다)
            Toast.makeText(this, "소중한 피드백이 기록되었습니다!", Toast.LENGTH_SHORT).show();
            finish(); // 피드백 화면 닫고 원래 화면으로 돌아가기
        });
    }

    // 라디오 그룹 안의 모든 버튼을 켜거나 끄는 함수
    private void setReasonGroupEnabled(boolean enabled) {
        for (int i = 0; i < rgReason.getChildCount(); i++) {
            rgReason.getChildAt(i).setEnabled(enabled);
        }
    }
}