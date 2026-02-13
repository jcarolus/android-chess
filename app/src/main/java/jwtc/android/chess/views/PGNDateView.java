package jwtc.android.chess.views;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.res.TypedArray;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Calendar;
import java.util.Date;

import jwtc.android.chess.R;
import jwtc.android.chess.helpers.PGNHelper;
import jwtc.android.chess.helpers.Utils;

public class PGNDateView extends LinearLayout {
    private static final String TAG = "PGNDateView";
    private TextInputLayout textInputLayout;
    private TextInputEditText textInputEditText;
    private final Calendar calendar = Calendar.getInstance();
    private DatePickerDialog datePickerDialog;


    public PGNDateView(Context context) {
        super(context);
        init(context, null);
    }

    public PGNDateView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public PGNDateView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.pgn_date, this, true);

        textInputLayout = findViewById(R.id.TextInputLayout);
        textInputEditText = findViewById(R.id.TextInputEditText);

        textInputEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                String input = editable.toString();

                Log.d(TAG, input);
                Log.d(TAG, textInputLayout == null ? "null" : "not null");

                if (input.isEmpty()) {
                    textInputEditText.setError(null);
                    return;
                }

                if (PGNHelper.getDate(input) == null) {
                    textInputEditText.setError("Invalid date");
                } else {
                    textInputEditText.setError(null);
                }
            }
        });

        datePickerDialog = new DatePickerDialog(context);

        datePickerDialog.setOnDateSetListener((view, year, monthOfYear, dayOfMonth) -> {
            calendar.set(year, monthOfYear, dayOfMonth);
            updateText();
        });

        textInputEditText.setOnClickListener(v -> {
            datePickerDialog.show();
        });

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, new int[]{android.R.attr.hint});
            CharSequence hint = a.getText(0);
            a.recycle();

            if (hint != null) {
                textInputLayout.setHint(hint);
            }
        }
    }

    public void setDate(Date date) {
        if (date != null) {
            calendar.setTime(date);
        } else {
            calendar.setTime(Calendar.getInstance().getTime());
        }
        updateText();
    }

    public Date getDate() {
        return calendar.getTime();
    }

    public void addOnTextChangedListener(TextWatcher watcher) {
        textInputEditText.addTextChangedListener(watcher);
    }

    private void updateText() {
        datePickerDialog.getDatePicker().updateDate(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        textInputEditText.setText(Utils.formatDate(calendar.getTime()));
    }
}
