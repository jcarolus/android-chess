package jwtc.android.chess.helpers;

import android.database.Cursor;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;

public class Utils {
    private static final String TAG = "Utils";

    public static int parseInt(String in, int defaultValue) {
        try {
            return Integer.parseInt(in);
        } catch(NumberFormatException ex) {
            return defaultValue;
        }
    }

    public static String getTrimmedOrNull(CharSequence cs) {
        if (cs == null) return null;
        String s = cs.toString().trim();
        return s.isEmpty() ? null : s;
    }

    public static String getTrimmedOrDefault(CharSequence cs, String sDefault) {
        if (cs == null) {
            return sDefault;
        }
        String s = cs.toString().trim();
        return s.isEmpty() ? sDefault : s;
    }

    public static String formatDate(Date d) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MM.dd");
        return d == null ? "YYYY.MM.DD" : formatter.format(d);
    }

    public static String getColumnString(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        if (index >= 0 && index < cursor.getColumnCount()) {
            try {
                String value = cursor.getString(index);
                return value == null ? "" : value;
            } catch (Exception ex) {
                Log.d(TAG, "Caught getString exception for " + column);
                return "";
            }
        }
        Log.d(TAG, "invalid index " + index + " for " + column);
        return "";
    }

    public static long getColumnLong(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        if (index >= 0 && index < cursor.getColumnCount()) {
            return cursor.getLong(index);
        }
        return -1;
    }

    public static Date getColumnDate(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        if (index >= 0 && index < cursor.getColumnCount()) {
            try {
                return new Date(cursor.getLong(index));
            } catch (Exception ex) {
                Log.d(TAG, "Caught exception for " + column + " " + ex.getMessage());
                return null;
            }
        }
        Log.d(TAG, "invalid index " + index + " for " + column);
        return null;
    }
}
