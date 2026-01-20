package jwtc.android.chess.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import jwtc.android.chess.R;

public class FixedDropdownView extends LinearLayout {

    private TextInputLayout textInputLayout;
    private MaterialAutoCompleteTextView autoCompleteTextView;

    private ArrayAdapter<String> adapter;
    private final List<String> items = new ArrayList<>();

    public FixedDropdownView(Context context) {
        super(context);
        init(context, null);
    }

    public FixedDropdownView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public FixedDropdownView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.fixed_dropdown, this, true);

        textInputLayout = findViewById(R.id.til);
        autoCompleteTextView = findViewById(R.id.actv);

        adapter = new ArrayAdapter<>(context, R.layout.dropdown_menu_item, items);
        autoCompleteTextView.setAdapter(adapter);

        autoCompleteTextView.setOnClickListener(v -> autoCompleteTextView.showDropDown());
        autoCompleteTextView.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) autoCompleteTextView.showDropDown();
        });

        textInputLayout.setEndIconOnClickListener(v -> {
            // Clear any filtering by resetting text
            autoCompleteTextView.setText(autoCompleteTextView.getText(), false);
            autoCompleteTextView.requestFocus();
            autoCompleteTextView.showDropDown();
        });


//        if (attrs != null) {
//
//        }
    }

    public void setItems(List<String> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        adapter.notifyDataSetChanged();
    }

    public void setItems(String[] newItems) {
        items.clear();
        if (newItems != null) {
            for (String s : newItems) items.add(s);
        }
        adapter.notifyDataSetChanged();
    }

    public void setHint(CharSequence hint) {
        textInputLayout.setHint(hint);
    }

    public void setSelectionText(CharSequence text) {
        autoCompleteTextView.setText(text, false);
    }

    public String getSelectionText() {
        CharSequence t = autoCompleteTextView.getText();
        return t == null ? "" : t.toString();
    }

    public void setOnItemClickListener(AdapterView.OnItemClickListener listener) {
        autoCompleteTextView.setOnItemClickListener(listener);
    }

    public TextInputLayout getTextInputLayout() {
        return textInputLayout;
    }

    public MaterialAutoCompleteTextView getAutoCompleteTextView() {
        return autoCompleteTextView;
    }
}