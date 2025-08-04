package jwtc.android.chess;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.core.text.HtmlCompat;
import jwtc.android.chess.activities.BaseActivity;
import jwtc.android.chess.helpers.ActivityHelper;

public class HtmlActivity extends BaseActivity {

    public static final String TAG = "HtmlActivity";
    public static String HELP_STRING_RESOURCE = "HELP_STRING_RESOURCE";

    private TextView textViewHelp;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.help);

        ActivityHelper.fixPaddings(this, findViewById(R.id.root_layout));

        textViewHelp = findViewById(R.id.TextViewHelp);
    }

    @Override
    protected void onResume() {
        super.onResume();

        final Intent intent = getIntent();
        // final Uri uri = intent.getData();

        Bundle extras = intent.getExtras();
        if (extras != null) {
            int resource = extras.getInt(HELP_STRING_RESOURCE);
            textViewHelp.setText(HtmlCompat.fromHtml(getString(resource), HtmlCompat.FROM_HTML_MODE_LEGACY));
        }
    }
}
