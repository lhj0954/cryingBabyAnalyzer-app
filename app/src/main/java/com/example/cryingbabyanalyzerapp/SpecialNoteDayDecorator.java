package com.example.cryingbabyanalyzerapp;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.google.android.material.datepicker.DayViewDecorator;

import java.util.List;

public class SpecialNoteDayDecorator extends DayViewDecorator {

    private final int[] noteDateKeys;

    private Drawable topSpacer;
    private Drawable emptyIndicator;
    private Drawable noteIndicator;
    private Drawable noteIndicatorSelected;

    public SpecialNoteDayDecorator(List<Integer> dateKeys) {
        noteDateKeys = new int[dateKeys != null ? dateKeys.size() : 0];

        if (dateKeys != null) {
            for (int i = 0; i < dateKeys.size(); i++) {
                noteDateKeys[i] = dateKeys.get(i);
            }
        }
    }

    private SpecialNoteDayDecorator(Parcel in) {
        int[] restored = in.createIntArray();
        noteDateKeys = restored != null ? restored : new int[0];
    }

    @Override
    public void initialize(@NonNull Context context) {
        int size = Math.round(
                12f * context.getResources().getDisplayMetrics().density
        );

        topSpacer = new ColorDrawable(Color.TRANSPARENT);
        topSpacer.setBounds(0, 0, size, size);

        emptyIndicator = new ColorDrawable(Color.TRANSPARENT);
        emptyIndicator.setBounds(0, 0, size, size);

        noteIndicator = createTintedCheck(
                context,
                ContextCompat.getColor(context, R.color.baby_pink_dark),
                size
        );

        noteIndicatorSelected = createTintedCheck(
                context,
                ContextCompat.getColor(context, android.R.color.white),
                size
        );
    }

    @Nullable
    @Override
    public Drawable getCompoundDrawableTop(
            @NonNull Context context,
            int year,
            int month,
            int day,
            boolean valid,
            boolean selected
    ) {
        return topSpacer;
    }

    @Nullable
    @Override
    public Drawable getCompoundDrawableBottom(
            @NonNull Context context,
            int year,
            int month,
            int day,
            boolean valid,
            boolean selected
    ) {
        if (!valid || !hasSpecialNote(year, month, day)) {
            return emptyIndicator;
        }

        return selected ? noteIndicatorSelected : noteIndicator;
    }

    @Nullable
    @Override
    public CharSequence getContentDescription(
            @NonNull Context context,
            int year,
            int month,
            int day,
            boolean valid,
            boolean selected,
            @Nullable CharSequence originalContentDescription
    ) {
        if (!valid || !hasSpecialNote(year, month, day)) {
            return originalContentDescription;
        }

        String base = originalContentDescription != null
                ? originalContentDescription.toString()
                : year + "년 " + (month + 1) + "월 " + day + "일";

        return base + ", 특이사항 메모 있음";
    }

    private boolean hasSpecialNote(int year, int month, int day) {
        int key = year * 10000 + (month + 1) * 100 + day;

        for (int noteDateKey : noteDateKeys) {
            if (noteDateKey == key) {
                return true;
            }
        }

        return false;
    }

    private Drawable createTintedCheck(
            Context context,
            int tint,
            int size
    ) {
        Drawable drawable = ContextCompat.getDrawable(
                context,
                R.drawable.ic_calendar_note_check
        );

        if (drawable == null) {
            Drawable fallback = new ColorDrawable(tint);
            fallback.setBounds(0, 0, size, size);
            return fallback;
        }

        drawable = DrawableCompat.wrap(drawable).mutate();
        DrawableCompat.setTint(drawable, tint);
        drawable.setBounds(0, 0, size, size);
        return drawable;
    }

    public static final Parcelable.Creator<SpecialNoteDayDecorator> CREATOR =
            new Parcelable.Creator<SpecialNoteDayDecorator>() {
                @Override
                public SpecialNoteDayDecorator createFromParcel(Parcel source) {
                    return new SpecialNoteDayDecorator(source);
                }

                @Override
                public SpecialNoteDayDecorator[] newArray(int size) {
                    return new SpecialNoteDayDecorator[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeIntArray(noteDateKeys);
    }
}
