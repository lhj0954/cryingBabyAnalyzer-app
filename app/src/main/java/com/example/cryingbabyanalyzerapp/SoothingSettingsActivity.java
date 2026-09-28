package com.example.cryingbabyanalyzerapp;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

public class SoothingSettingsActivity extends AppCompatActivity {

    private TextView tvSelectedAudio;
    private TextView tvPlaybackStatus;
    private Switch switchAutoPlay;
    private Spinner spinnerDuration;
    private Button btnSelectAudio;
    private Button btnTestPlay;
    private Button btnStopPlayback;

    private final int[] durationValues = {30, 60, 120, 300};

    private final ActivityResultLauncher<Intent> audioPickerLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                            return;
                        }

                        Uri uri = result.getData().getData();
                        if (uri == null) {
                            return;
                        }

                        try {
                            getContentResolver().takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            );
                        } catch (Exception ignored) {
                        }

                        String displayName = getDisplayName(uri);
                        SoothingAudioManager.setSelectedAudio(
                                this,
                                uri.toString(),
                                displayName
                        );

                        updateSelectedAudioText();
                        Toast.makeText(this, "완화 음원을 저장했습니다.", Toast.LENGTH_SHORT).show();
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_soothing_settings);

        tvSelectedAudio = findViewById(R.id.tvSelectedAudio);
        tvPlaybackStatus = findViewById(R.id.tvPlaybackStatus);
        switchAutoPlay = findViewById(R.id.switchAutoPlay);
        spinnerDuration = findViewById(R.id.spinnerDuration);
        btnSelectAudio = findViewById(R.id.btnSelectAudio);
        btnTestPlay = findViewById(R.id.btnTestPlay);
        btnStopPlayback = findViewById(R.id.btnStopPlayback);

        setupDurationSpinner();

        switchAutoPlay.setChecked(
                SoothingAudioManager.isAutoPlayEnabled(this)
        );

        switchAutoPlay.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && !SoothingAudioManager.hasSelectedAudio(this)) {
                buttonView.setChecked(false);
                Toast.makeText(
                        this,
                        "먼저 엄마 음성 또는 진정 음악을 선택해주세요.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            SoothingAudioManager.setAutoPlayEnabled(this, isChecked);
        });

        btnSelectAudio.setOnClickListener(v -> openAudioPicker());

        btnTestPlay.setOnClickListener(v -> {
            if (!SoothingAudioManager.hasSelectedAudio(this)) {
                Toast.makeText(
                        this,
                        "먼저 재생할 음원을 선택해주세요.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            boolean started = SoothingAudioManager.play(this);
            if (started) {
                tvPlaybackStatus.setText(
                        "재생 중 · "
                                + SoothingAudioManager.getDurationSeconds(this)
                                + "초 후 자동 종료"
                );
            } else {
                tvPlaybackStatus.setText("음원을 재생하지 못했습니다.");
            }
        });

        btnStopPlayback.setOnClickListener(v -> {
            SoothingAudioManager.stop();
            tvPlaybackStatus.setText("재생이 중지되었습니다.");
        });

        updateSelectedAudioText();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (tvPlaybackStatus != null) {
            tvPlaybackStatus.setText(
                    SoothingAudioManager.isPlaying()
                            ? "현재 완화 음원이 재생 중입니다."
                            : "현재 재생 중인 완화 음원이 없습니다."
            );
        }
    }

    private void setupDurationSpinner() {
        String[] labels = {
                "30초",
                "1분",
                "2분",
                "5분"
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                labels
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDuration.setAdapter(adapter);

        int savedDuration = SoothingAudioManager.getDurationSeconds(this);
        int selectedIndex = 1;

        for (int i = 0; i < durationValues.length; i++) {
            if (durationValues[i] == savedDuration) {
                selectedIndex = i;
                break;
            }
        }

        spinnerDuration.setSelection(selectedIndex);
        spinnerDuration.setOnItemSelectedListener(
                new android.widget.AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            android.widget.AdapterView<?> parent,
                            android.view.View view,
                            int position,
                            long id
                    ) {
                        SoothingAudioManager.setDurationSeconds(
                                SoothingSettingsActivity.this,
                                durationValues[position]
                        );
                    }

                    @Override
                    public void onNothingSelected(
                            android.widget.AdapterView<?> parent
                    ) {
                    }
                }
        );
    }

    private void openAudioPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        );
        audioPickerLauncher.launch(intent);
    }

    private void updateSelectedAudioText() {
        String name = SoothingAudioManager.getSelectedAudioName(this);

        if (name == null || name.trim().isEmpty()) {
            tvSelectedAudio.setText("선택된 음원 없음");
        } else {
            tvSelectedAudio.setText("선택된 음원: " + name);
        }
    }

    private String getDisplayName(Uri uri) {
        String result = "선택한 음원";

        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(
                    uri,
                    null,
                    null,
                    null,
                    null
            );

            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(
                        OpenableColumns.DISPLAY_NAME
                );
                if (nameIndex >= 0) {
                    String value = cursor.getString(nameIndex);
                    if (value != null && !value.trim().isEmpty()) {
                        result = value;
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return result;
    }
}
