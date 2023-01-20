package jwtc.android.chess.helpers;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

public class Clipboard {
    private static ClipboardManager clipboardManager = null;
    private static ClipData clipData = null;


    public static void stringToClipboard(Context context, String s, String sToast) {
        initClipboardManager(context);
        clipData = ClipData.newPlainText("text", s);
        clipboardManager.setPrimaryClip(clipData);
    }

    public static String getStringFromClipboard(Context context) {
        initClipboardManager(context);
        ClipData pData = clipboardManager.getPrimaryClip();
        ClipData.Item item = pData.getItemAt(0);
        return item.getText().toString();
    }

    private static void initClipboardManager(Context context) {
        if (clipboardManager == null) {
            clipboardManager = (ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
        }
    }
}
