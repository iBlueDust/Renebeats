package com.yearzero.renebeats.preferences;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yearzero.renebeats.R;

import java.util.Locale;

public class TimeoutDialog extends Dialog {

    private TextView Title, Message;
    private Button Positive, Negative;

    private String title, message, positive, negative;
    private OnClickListener positivel, negativel;
    private int timeout;

    private Runnable runnable = () -> {
        if (timeout < 0) {
            if (negativel == null || negativel.onClick()) dismiss();
        } else {
            Negative.setText(String.format(Locale.ENGLISH, "%s (%d)", negative, timeout));
            timeout--;
            new Handler().postDelayed(TimeoutDialog.this.runnable, 1000);
        }
    };

    TimeoutDialog(@NonNull Context context) {
        super(context);
    }

    public interface OnClickListener {
        boolean onClick();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        if (window != null) window.requestFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_timeout);

        Title = findViewById(R.id.title);
        Message = findViewById(R.id.message);

        Positive = findViewById(R.id.positive);
        Negative = findViewById(R.id.negative);
        
        if (title != null) Title.setText(title);
        if (message != null) Message.setText(message);
        
        if (positive != null) Positive.setText(positive);
        Positive.setOnClickListener(v -> {
            if (positivel != null) positivel.onClick();
            TimeoutDialog.this.dismiss();
        });

        if (negative != null) Negative.setText(negative);
        Negative.setOnClickListener(v -> {
            if (negativel != null) negativel.onClick();
            TimeoutDialog.this.dismiss();
        });

        if (timeout > 0) runnable.run();
    }

    public TimeoutDialog setTimeout(int timeout) {
        this.timeout = timeout;
        return this;
    }

    public TimeoutDialog setTitle(@NonNull String title) {
        this.title = title;
        return this;
    }

    public TimeoutDialog setMessage(@NonNull String message) {
        this.message = message;
        return this;
    }

    public TimeoutDialog setPositive(@NonNull String text, @Nullable OnClickListener listener) {
        this.positivel = listener;
        this.positive = text;
        return this;
    }

    public TimeoutDialog setNegative(@NonNull String text, @Nullable OnClickListener listener) {
        this.negativel = listener;
        this.negative = text;
        return this;
    }
}
