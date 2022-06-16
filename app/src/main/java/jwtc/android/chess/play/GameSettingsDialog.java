package jwtc.android.chess.play;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;

import androidx.annotation.NonNull;
import jwtc.android.chess.R;
import jwtc.android.chess.helpers.ResultDialog;
import jwtc.android.chess.helpers.ResultDialogListener;

public class GameSettingsDialog extends ResultDialog {
    public GameSettingsDialog(@NonNull Context context, ResultDialogListener listener, int requestCode, final SharedPreferences prefs) {
        super(context, listener, requestCode);

        setContentView(R.layout.game_settings);

        final boolean vsCPU = prefs.getBoolean("opponent", true);
        final boolean playAsWhite = prefs.getBoolean("myTurn", true);

        final RadioButton radioAndroid = findViewById(R.id.radioAndroid);
        final RadioButton radioHuman = findViewById(R.id.radioHuman);
        final RadioButton radioWhite = findViewById(R.id.radioWhite);
        final RadioButton radioBlack = findViewById(R.id.radioBlack);

        radioAndroid.setChecked(vsCPU);
        radioHuman.setChecked(!vsCPU);

        radioWhite.setChecked(playAsWhite);
        radioBlack.setChecked(!playAsWhite);

        Button buttonOk = findViewById(R.id.ButtonOk);
        buttonOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                SharedPreferences.Editor editor = prefs.edit();

                editor.putBoolean("opponent", radioAndroid.isChecked());
                editor.putBoolean("myTurn", radioWhite.isChecked());

                editor.commit();

                setResult(new Bundle());

                dismiss();
            }
        });

        Button buttonCancel = findViewById(R.id.ButtonCancel);
        buttonCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
    }
}
