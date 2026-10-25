package com.example.cryingbabyanalyzerapp;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class BabyProfilePreferences {

    private static final String PREFS_NAME = "baby_profile";

    private static final String KEY_BABY_NAME = "baby_name";
    private static final String KEY_BIRTH_DATE = "birth_date";
    private static final String KEY_GENDER = "gender";
    private static final String KEY_BIRTH_WEIGHT = "birth_weight";
    private static final String KEY_PRETERM = "preterm";
    private static final String KEY_GUARDIAN_NAME = "guardian_name";
    private static final String KEY_SAVED_AT = "saved_at";

    private BabyProfilePreferences() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean hasProfile(Context context) {
        return prefs(context).getLong(KEY_SAVED_AT, 0L) > 0L;
    }

    public static void save(
            Context context,
            String babyName,
            String birthDate,
            String gender,
            String birthWeight,
            boolean preterm,
            String guardianName
    ) {
        prefs(context).edit()
                .putString(KEY_BABY_NAME, safe(babyName))
                .putString(KEY_BIRTH_DATE, safe(birthDate))
                .putString(KEY_GENDER, safe(gender))
                .putString(KEY_BIRTH_WEIGHT, safe(birthWeight))
                .putBoolean(KEY_PRETERM, preterm)
                .putString(KEY_GUARDIAN_NAME, safe(guardianName))
                .putLong(KEY_SAVED_AT, System.currentTimeMillis())
                .apply();
    }

    public static String getBabyName(Context context) {
        return prefs(context).getString(KEY_BABY_NAME, "");
    }

    public static String getBirthDate(Context context) {
        return prefs(context).getString(KEY_BIRTH_DATE, "");
    }

    public static String getGender(Context context) {
        return prefs(context).getString(KEY_GENDER, "");
    }

    public static String getBirthWeight(Context context) {
        return prefs(context).getString(KEY_BIRTH_WEIGHT, "");
    }

    public static boolean isPreterm(Context context) {
        return prefs(context).getBoolean(KEY_PRETERM, false);
    }

    public static String getGuardianName(Context context) {
        return prefs(context).getString(KEY_GUARDIAN_NAME, "");
    }

    public static int getAgeMonths(Context context) {
        String birthDate = getBirthDate(context);
        if (birthDate.isEmpty()) {
            return -1;
        }

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA);
        format.setLenient(false);

        try {
            Date parsed = format.parse(birthDate);
            if (parsed == null) return -1;

            Calendar birth = Calendar.getInstance();
            birth.setTime(parsed);

            Calendar today = Calendar.getInstance();
            if (birth.after(today)) return -1;

            int months = (today.get(Calendar.YEAR) - birth.get(Calendar.YEAR)) * 12
                    + today.get(Calendar.MONTH)
                    - birth.get(Calendar.MONTH);

            if (today.get(Calendar.DAY_OF_MONTH) < birth.get(Calendar.DAY_OF_MONTH)) {
                months--;
            }

            return Math.max(months, 0);
        } catch (ParseException e) {
            return -1;
        }
    }

    public static String getHomeGreeting(Context context) {
        String babyName = getBabyName(context);
        if (babyName.isEmpty()) {
            return "안녕하세요, 보호자님";
        }
        return "안녕하세요, " + babyName + " 보호자님";
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
