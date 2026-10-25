package com.example.cryingbabyanalyzerapp;

import android.app.DatePickerDialog;
import android.content.Intent;
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

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.Calendar;
import java.util.Locale;

public class ProfileActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigationView;

    private TextView tvProfileGreeting;
    private TextView tvProfileTitle;
    private TextView tvProfileSubtitle;
    private TextView tvProfileDetails;

    private LinearLayout profileSummaryContainer;
    private LinearLayout profileEditContainer;

    private EditText etBabyName;
    private EditText etBirthDate;
    private EditText etBirthWeight;
    private EditText etGuardianName;
    private RadioGroup rgGender;
    private CheckBox cbProfilePreterm;

    private Button btnEditProfile;
    private Button btnSaveProfile;
    private Button btnCancelProfileEdit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        bindViews();
        setupBottomNavigation();

        etBirthDate.setOnClickListener(v -> showBirthDatePicker());
        btnEditProfile.setOnClickListener(v -> showEditForm());
        btnSaveProfile.setOnClickListener(v -> saveProfile());
        btnCancelProfileEdit.setOnClickListener(v -> {
            loadFormFromSavedProfile();
            showSummary();
        });

        if (BabyProfilePreferences.hasProfile(this)) {
            showSummary();
        } else {
            showEditForm();
        }
    }

    private void bindViews() {
        bottomNavigationView = findViewById(R.id.bottom_navigation);

        tvProfileGreeting = findViewById(R.id.tvProfileGreeting);
        tvProfileTitle = findViewById(R.id.tvProfileTitle);
        tvProfileSubtitle = findViewById(R.id.tvProfileSubtitle);
        tvProfileDetails = findViewById(R.id.tvProfileDetails);

        profileSummaryContainer = findViewById(R.id.profileSummaryContainer);
        profileEditContainer = findViewById(R.id.profileEditContainer);

        etBabyName = findViewById(R.id.etBabyName);
        etBirthDate = findViewById(R.id.etBirthDate);
        etBirthWeight = findViewById(R.id.etProfileBirthWeight);
        etGuardianName = findViewById(R.id.etGuardianName);
        rgGender = findViewById(R.id.rgGender);
        cbProfilePreterm = findViewById(R.id.cbProfilePreterm);

        btnEditProfile = findViewById(R.id.btnEditProfile);
        btnSaveProfile = findViewById(R.id.btnSaveProfile);
        btnCancelProfileEdit = findViewById(R.id.btnCancelProfileEdit);
    }

    private void setupBottomNavigation() {
        bottomNavigationView.setSelectedItemId(R.id.nav_profile);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.nav_profile) {
                return true;
            }

            if (itemId == R.id.nav_home) {
                startActivity(new Intent(ProfileActivity.this, MainActivity.class));
                finish();
                return true;
            }

            if (itemId == R.id.nav_medical_record) {
                startActivity(new Intent(ProfileActivity.this, MedicalRecordActivity.class));
                finish();
                return true;
            }

            return false;
        });
    }

    private void showSummary() {
        if (!BabyProfilePreferences.hasProfile(this)) {
            showEditForm();
            return;
        }

        profileSummaryContainer.setVisibility(View.VISIBLE);
        profileEditContainer.setVisibility(View.GONE);

        String babyName = BabyProfilePreferences.getBabyName(this);
        String birthDate = BabyProfilePreferences.getBirthDate(this);
        String gender = BabyProfilePreferences.getGender(this);
        String birthWeight = BabyProfilePreferences.getBirthWeight(this);
        String guardianName = BabyProfilePreferences.getGuardianName(this);
        boolean preterm = BabyProfilePreferences.isPreterm(this);
        int ageMonths = BabyProfilePreferences.getAgeMonths(this);

        tvProfileGreeting.setText(BabyProfilePreferences.getHomeGreeting(this));
        tvProfileTitle.setText(babyName.isEmpty() ? "우리 아기 프로필" : babyName + " 프로필");

        String ageText = ageMonths >= 0 ? "생후 " + ageMonths + "개월" : "생후 개월 수 미확인";
        String birthText = birthDate.isEmpty() ? "생년월일 미입력" : birthDate + "생";
        tvProfileSubtitle.setText(ageText + " · " + birthText);

        StringBuilder details = new StringBuilder();
        details.append("성별  ").append(gender.isEmpty() ? "미입력" : gender).append("\n");
        details.append("출생체중  ")
                .append(birthWeight.isEmpty() ? "미입력" : birthWeight + " g")
                .append("\n");
        details.append("미숙아 여부  ").append(preterm ? "예" : "아니오").append("\n");
        details.append("보호자  ").append(guardianName.isEmpty() ? "미입력" : guardianName);

        tvProfileDetails.setText(details.toString());
    }

    private void showEditForm() {
        loadFormFromSavedProfile();
        profileSummaryContainer.setVisibility(View.GONE);
        profileEditContainer.setVisibility(View.VISIBLE);

        if (BabyProfilePreferences.hasProfile(this)) {
            tvProfileGreeting.setText("아기 프로필을 수정해요");
        } else {
            tvProfileGreeting.setText("우리 아기 정보를 등록해요");
        }
    }

    private void loadFormFromSavedProfile() {
        etBabyName.setText(BabyProfilePreferences.getBabyName(this));
        etBirthDate.setText(BabyProfilePreferences.getBirthDate(this));
        etBirthWeight.setText(BabyProfilePreferences.getBirthWeight(this));
        etGuardianName.setText(BabyProfilePreferences.getGuardianName(this));
        cbProfilePreterm.setChecked(BabyProfilePreferences.isPreterm(this));

        String gender = BabyProfilePreferences.getGender(this);
        if ("남아".equals(gender)) {
            rgGender.check(R.id.rbGenderBoy);
        } else if ("여아".equals(gender)) {
            rgGender.check(R.id.rbGenderGirl);
        } else {
            rgGender.clearCheck();
        }
    }

    private void saveProfile() {
        String babyName = etBabyName.getText().toString().trim();
        String birthDate = etBirthDate.getText().toString().trim();

        if (babyName.isEmpty()) {
            Toast.makeText(this, "아기 이름 또는 애칭을 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (birthDate.isEmpty()) {
            Toast.makeText(this, "생년월일을 선택해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        String gender = "";
        int genderId = rgGender.getCheckedRadioButtonId();
        if (genderId == R.id.rbGenderBoy) {
            gender = "남아";
        } else if (genderId == R.id.rbGenderGirl) {
            gender = "여아";
        }

        BabyProfilePreferences.save(
                this,
                babyName,
                birthDate,
                gender,
                etBirthWeight.getText().toString().trim(),
                cbProfilePreterm.isChecked(),
                etGuardianName.getText().toString().trim()
        );

        Toast.makeText(this, "아기 프로필을 저장했습니다.", Toast.LENGTH_SHORT).show();
        showSummary();
    }

    private void showBirthDatePicker() {
        Calendar calendar = Calendar.getInstance();

        String saved = etBirthDate.getText().toString().trim();
        if (!saved.isEmpty()) {
            String[] parts = saved.split("-");
            if (parts.length == 3) {
                try {
                    calendar.set(
                            Integer.parseInt(parts[0]),
                            Integer.parseInt(parts[1]) - 1,
                            Integer.parseInt(parts[2])
                    );
                } catch (NumberFormatException ignored) {
                }
            }
        }

        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> etBirthDate.setText(
                        String.format(Locale.KOREA, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                ),
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );

        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        dialog.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bottomNavigationView != null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_profile);
        }

        if (BabyProfilePreferences.hasProfile(this)
                && profileEditContainer.getVisibility() != View.VISIBLE) {
            showSummary();
        }
    }
}
