package jwtc.android.chess.lichess;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;

import java.util.HashMap;
import java.util.Map;

import jwtc.android.chess.R;
import jwtc.android.chess.helpers.ResultDialog;
import jwtc.android.chess.helpers.ResultDialogListener;
import jwtc.android.chess.helpers.Utils;

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

        final MaterialButtonToggleGroup toggleTimeControl = findViewById(R.id.ToggleTimeControlGroup);

        final LinearLayout layoutDays = findViewById(R.id.LayoutDays);
        final LinearLayout layoutMinutes = findViewById(R.id.LayoutMinutes);
        final LinearLayout layoutIncrement = findViewById(R.id.LayoutIncrement);
        final EditText editTextDays = findViewById(R.id.EditTextDays);
        final EditText editTextTime = findViewById(R.id.EditTextMinutes);
        final EditText editTextIncrement = findViewById(R.id.EditTextIncrement);

        final MaterialButtonToggleGroup toggleVariant = findViewById(R.id.ToggleVariantGroup);
        final MaterialButton buttonVariantDefault = findViewById(R.id.RadioButtonStandard);
        final MaterialButton buttonVariantChess960 = findViewById(R.id.RadioButtonChess960);

        final MaterialButtonToggleGroup toggleColor = findViewById(R.id.ToggleColorGroup);

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
            toggleTimeControl.check(R.id.RadioButtonTimeControl);
        } else {
            layoutDays.setVisibility(View.VISIBLE);
            layoutMinutes.setVisibility(View.INVISIBLE);
            layoutIncrement.setVisibility(View.GONE);
            toggleTimeControl.check(R.id.RadioButtonUnlimitedTime);
        }

        String variant = prefs.getString("lichess_challenge_variant", "standard");
        toggleVariant.check(variant.equals("standard")
                ? R.id.RadioButtonStandard
                : R.id.RadioButtonChess960);
        buttonVariantDefault.setEnabled(false); // @TODO until castling is fixed
        buttonVariantChess960.setEnabled(false);

        String color = prefs.getString("lichess_challenge_color", "random");
        if (color.equals("white")) {
            toggleColor.check(R.id.RadioButtonWhite);
        } else if (color.equals("black")) {
            toggleColor.check(R.id.RadioButtonBlack);
        } else {
            toggleColor.check(R.id.RadioButtonRandom);
        }

        checkBoxRated.setChecked(prefs.getBoolean("lichess_challenge_rated", false));

        // toggle group behaviour
        toggleTimeControl.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            if (checkedId == R.id.RadioButtonUnlimitedTime) {
                layoutDays.setVisibility(View.VISIBLE);
                layoutMinutes.setVisibility(View.INVISIBLE);
                layoutIncrement.setVisibility(View.GONE);
            } else if (checkedId == R.id.RadioButtonTimeControl) {
                layoutDays.setVisibility(View.GONE);
                layoutMinutes.setVisibility(View.VISIBLE);
                layoutIncrement.setVisibility(View.VISIBLE);
            }
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
            if (toggleTimeControl.getCheckedButtonId() == R.id.RadioButtonTimeControl) {

                int editMinutes = Utils.parseInt(editTextTime.getText().toString(), 5);
                int increment = Utils.parseInt(editTextIncrement.getText().toString(), 0);

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
                int editDays = Utils.parseInt(editTextDays.getText().toString(), 1);
                editor.putBoolean("lichess_challenge_timetcontrol", false);
                editor.putInt("lichess_challenge_days", editDays);
                data.put("days", editDays);
            }

            // variant
            String editVariant = toggleVariant.getCheckedButtonId() == R.id.RadioButtonStandard
                    ? "standard"
                    : "chess960";
            editor.putString("lichess_challenge_variant", editVariant);
            data.put("variant", editVariant);

            // color
            int selectedColor = toggleColor.getCheckedButtonId();
            String editColor = selectedColor == R.id.RadioButtonWhite
                    ? "white"
                    : selectedColor == R.id.RadioButtonBlack
                        ? "black"
                        : "random";

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
