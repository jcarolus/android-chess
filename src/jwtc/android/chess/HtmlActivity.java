package jwtc.android.chess;

import java.util.Locale;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Window;
import android.webkit.WebView;

public class HtmlActivity extends Activity {

	public static String HELP_MODE = "HELP_MODE";

	private WebView _webview;
	private String _lang;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_NO_TITLE);

		setContentView(R.layout.help);

		// html assets localization
		if (Locale.getDefault().getLanguage().equals("it")) {
			_lang = "it";
		} else {
			_lang = "en";
		}
		_webview = (WebView) findViewById(R.id.WebViewHelp);

	}

	@Override
	protected void onResume() {
		super.onResume();

		final Intent intent = getIntent();
		// final Uri uri = intent.getData();

		Bundle extras = intent.getExtras();
		if (extras != null) {
			String s = extras.getString(HELP_MODE);

			_webview.loadUrl("file:///android_asset/" + s + "-" + _lang
					+ ".html");
		}
	}
}
