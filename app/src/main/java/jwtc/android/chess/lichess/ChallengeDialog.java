package jwtc.android.chess.lichess;

import android.content.Context;
import android.content.SharedPreferences;
import android.opengl.Visibility;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;

import java.util.HashMap;
import java.util.Map;

import jwtc.android.chess.R;
import jwtc.android.chess.helpers.ResultDialog;
import jwtc.android.chess.helpers.ResultDialogListener;

public class ChallengeDialog extends ResultDialog<Map<String, Object>> {

    private static final String TAG = "Lichess.ChallengeDialog";
    public static final int REQUEST_CHALLENGE = 1;
    public static final int REQUEST_SEEK = 2;

    public ChallengeDialog(Context context, ResultDialogListener<Map<String, Object>> listener, int requestCode, final SharedPreferences prefs) {
        super(context, listener, requestCode);

        setContentView(R.layout.lichess_challenge);

        setTitle(requestCode == REQUEST_CHALLENGE ? R.string.lichess_create_challenge_title : R.string.lichess_create_seek_title);

        final MaterialCardView playerView = findViewById(R.id.CardViewPlayer);
        playerView.setVisibility(requestCode == REQUEST_CHALLENGE ? View.VISIBLE : View.GONE);

        final EditText editTextPlayer = findViewById(R.id.EditTextMatchOpponent);
        final TextView textViewPlayerName = findViewById(R.id.tvMatchPlayerName);

        final RadioButton radioButtonUnlimitedTime = findViewById(R.id.RadioButtonUnlimitedTime);
        final RadioButton radioButtonTimeControl = findViewById(R.id.RadioButtonTimeControl);

        final LinearLayout layoutDays = findViewById(R.id.LayoutDays);
        final LinearLayout layoutMinutes = findViewById(R.id.LayoutMinutes);
        final LinearLayout layoutIncrement = findViewById(R.id.LayoutIncrement);
        final EditText editTextDays = findViewById(R.id.EditTextDays);
        final EditText editTextTime = findViewById(R.id.EditTextMinutes);
        final EditText editTextIncrement = findViewById(R.id.EditTextIncrement);

        final RadioButton radioButtonVariantDefault = findViewById(R.id.RadioButtonStandard);
        final RadioButton radioButtonVariantChess960 = findViewById(R.id.RadioButtonChess960);

        final RadioButton radioButtonRandom = findViewById(R.id.RadioButtonRandom);
        final RadioButton radioButtonWhite = findViewById(R.id.RadioButtonWhite);
        final RadioButton radioButtonBlack = findViewById(R.id.RadioButtonBlack);

        final CheckBox checkBoxRated = findViewById(R.id.CheckBoxSeekRated);

        // initial values
        editTextPlayer.setText(prefs.getString("lichess_challenge_name", ""));
        boolean withTimeControl = prefs.getBoolean("lichess_challenge_timetcontrol", true);
        int days = prefs.getInt("lichess_challenge_days", 1);
        editTextDays.setText("" + days);
        int minutes = prefs.getInt("lichess_challenge_minutes", 5);
        editTextTime.setText("" + minutes);
        editTextIncrement.setText("" + prefs.getInt("lichess_challenge_increment", 10));

        if (withTimeControl) {
            layoutDays.setVisibility(View.GONE);
            layoutMinutes.setVisibility(View.VISIBLE);
            layoutIncrement.setVisibility(View.VISIBLE);
            radioButtonTimeControl.setChecked(true);
            radioButtonUnlimitedTime.setChecked(false);
        } else {
            layoutDays.setVisibility(View.VISIBLE);
            layoutMinutes.setVisibility(View.INVISIBLE);
            layoutIncrement.setVisibility(View.GONE);
            radioButtonTimeControl.setChecked(false);
            radioButtonUnlimitedTime.setChecked(true);
        }

        String variant = prefs.getString("lichess_challenge_variant", "standard");
        radioButtonVariantDefault.setChecked(variant.equals("standard"));
        radioButtonVariantChess960.setChecked(!radioButtonVariantDefault.isChecked());
        radioButtonVariantDefault.setEnabled(false); // @TODO until castling is fixed
        radioButtonVariantChess960.setEnabled(false);

        String color = prefs.getString("lichess_challenge_color", "random");
        radioButtonRandom.setChecked(color.equals("random"));
        radioButtonWhite.setChecked(color.equals("white"));
        radioButtonBlack.setChecked(color.equals("black"));

        checkBoxRated.setChecked(prefs.getBoolean("lichess_challenge_rated", false));

        // radio group behaviour
        radioButtonUnlimitedTime.setOnClickListener(v -> {
            radioButtonTimeControl.setChecked(!radioButtonUnlimitedTime.isChecked());
            layoutDays.setVisibility(View.VISIBLE);
            layoutMinutes.setVisibility(View.INVISIBLE);
            layoutIncrement.setVisibility(View.GONE);
        });
        radioButtonTimeControl.setOnClickListener(v -> {
            radioButtonUnlimitedTime.setChecked(!radioButtonTimeControl.isChecked());
            layoutDays.setVisibility(View.GONE);
            layoutMinutes.setVisibility(View.VISIBLE);
            layoutIncrement.setVisibility(View.VISIBLE);
        });

        radioButtonVariantDefault.setOnClickListener(v -> radioButtonVariantChess960.setChecked(!radioButtonVariantDefault.isChecked()));
        radioButtonVariantChess960.setOnClickListener(v -> radioButtonVariantDefault.setChecked(!radioButtonVariantChess960.isChecked()));

        radioButtonRandom.setOnClickListener(v -> {
            radioButtonWhite.setChecked(!radioButtonRandom.isChecked());
            radioButtonBlack.setChecked(!radioButtonRandom.isChecked());
        });
        radioButtonWhite.setOnClickListener(v -> {
            radioButtonRandom.setChecked(!radioButtonWhite.isChecked());
            radioButtonBlack.setChecked(!radioButtonWhite.isChecked());
        });
        radioButtonBlack.setOnClickListener(v -> {
            radioButtonWhite.setChecked(!radioButtonBlack.isChecked());
            radioButtonRandom.setChecked(!radioButtonBlack.isChecked());
        });

        final Button buttonOk = findViewById(R.id.ButtonChallengeOk);
        buttonOk.setOnClickListener(v -> {

            SharedPreferences.Editor editor = prefs.edit();
            Map<String, Object> data = new HashMap<>();

            // username
            if (requestCode == REQUEST_CHALLENGE) {
                String username = editTextPlayer.getText().toString();
                if (!username.isEmpty()) {
                    data.put("username", username);
                    editor.putString("lichess_challenge_name", username);
                }
            }

            // timecontrol
            if (radioButtonTimeControl.isChecked()) {

                int editMinutes = Integer.parseInt(editTextTime.getText().toString());
                int increment = Integer.parseInt(editTextIncrement.getText().toString());

                editor.putBoolean("lichess_challenge_timetcontrol", true);
                editor.putInt("lichess_challenge_minutes", editMinutes);
                editor.putInt("lichess_challenge_increment", increment);

                if (editMinutes >= 3) {
                    editTextTime.setError(null);

                    if (requestCode == REQUEST_CHALLENGE) {
                        data.put("clock.limit", editMinutes * 60);
                        data.put("clock.increment", increment);
                    } else {
                        data.put("time", editMinutes);
                        data.put("increment", increment);
                    }
                } else {
                    editTextTime.setError("Number must be greater than 2");
                    return;
                }
            } else {
                int editDays = Integer.parseInt(editTextDays.getText().toString());
                editor.putBoolean("lichess_challenge_timetcontrol", false);
                editor.putInt("lichess_challenge_days", editDays);
                data.put("days", editDays);
            }

            // variant
            String editVariant = radioButtonVariantDefault.isChecked() ? "standard" : "chess960";
            editor.putString("lichess_challenge_variant", editVariant);
            data.put("variant", editVariant);

            // color
            String editColor = radioButtonRandom.isChecked()
                    ? "random" : radioButtonWhite.isChecked() ? "white" : "black";

            editor.putString("lichess_challenge_color", editColor);
            data.put("color", editColor);

            editor.putBoolean("lichess_challenge_rated", checkBoxRated.isChecked());
            data.put("rated", checkBoxRated.isChecked());

            editor.apply();

            ChallengeDialog.this.dismiss();
            setResult(data);
        });
        final Button buttonCancel = findViewById(R.id.ButtonChallengeCancel);
        buttonCancel.setOnClickListener(v -> {
            ChallengeDialog.this.dismiss();
            setResult(null);
        });
    }
}