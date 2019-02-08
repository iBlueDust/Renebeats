package com.yearzero.renebeats;

import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.NumberPicker;
import android.widget.TextView;

import java.util.Locale;

import androidx.annotation.NonNull;

public class DurationPicker extends Dialog {
    public enum Mode { Hour, Minute, Second }
    public interface Callbacks {
        String Validate(int time);
        void onSubmit(int time);
    }

    private TextView Title, Error;
    private NumberPicker Hour, Minute, Second;
    private Button Cancel, OK;

    private short maxH, maxM, maxS;
    private Callbacks callbacks;

    private static final NumberPicker.Formatter formatter = i -> String.format(Locale.ENGLISH, "%02d", i);

    public DurationPicker(@NonNull Context context) {
        super(context);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_duration);
        Title = findViewById(R.id.title);
        Title.setText("Set a time");

        Hour = findViewById(R.id.hour);
        Minute = findViewById(R.id.minute);
        Second = findViewById(R.id.second);

        Error = findViewById(R.id.error);

        Minute.setFormatter(formatter);
        Second.setFormatter(formatter);

        Cancel = findViewById(R.id.cancel);
        OK = findViewById(R.id.ok);

        Cancel.setOnClickListener(v -> dismiss());

        OK.setOnClickListener(v -> {
            Error.setText("");
            int time = getTime();
            if (callbacks != null) {
                String error = callbacks.Validate(time);
                if (error == null) {
                    callbacks.onSubmit(time);
                    dismiss();
                } else Error.setText(error);
            }
        });
    }

    public void setTitle(String title) {
        Title.setText(title);
    }

    public void setEnabled(Mode mode) {
        Hour.setVisibility(mode == Mode.Hour ? View.VISIBLE : View.GONE);
        Minute.setVisibility(mode == Mode.Hour || mode == Mode.Minute ? View.VISIBLE : View.GONE);
    }

    public void setMaxTime(short hour, short minute, short second) {
        if (hour < 0 || minute < 0 || second < 0 || (hour == 0 && minute == 0 && second == 0)) throw new IllegalArgumentException();
        this.maxH = hour;
        this.maxM = minute;
        this.maxS = second;

        Hour.setMinValue(0);
        Hour.setMaxValue(hour);
        Minute.setMinValue(0);
        Minute.setMaxValue(hour <= 0 ? minute : 59);
        Second.setMinValue(0);
        Second.setMaxValue(59);

        Hour.setOnValueChangedListener((picker, oldVal, newVal) -> Minute.setMaxValue(newVal >= maxH ? maxM : 59));

        Minute.setOnValueChangedListener((picker, oldVal, newVal) -> Second.setMaxValue(newVal >= maxM ? maxS : 59));
    }

    public void setMaxTime(int kiloms) {
        setMaxTime(
                (short) (Math.floor(kiloms / 3600) % 3600),
                (short) (Math.floor(kiloms / 60) % 60),
                (short) (kiloms % 60)
        );
    }

    public void setTime(short hour, short minute, short second) {
        Hour.setValue(hour);
        setTime(minute, second);
    }

    public void setTime(short minute, short second) {
        Minute.setValue(minute % 60);
        Second.setValue(second % 60);
    }

    public void setTime(int kiloms) {
        setTime((short) Math.floor((kiloms % 3600) / 3600),
                (short) Math.floor((kiloms % 60) / 60),
                (short) (kiloms % 60));
    }

    public void setCallbacks(Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    public int getTime() {
        return (Hour.getVisibility() == View.VISIBLE ? Hour.getValue() * 3600 : 0) +
            Minute.getValue() * 60 + Second.getValue();
    }

}
