package jwtc.android.chess.gamedatabase;

import android.content.SharedPreferences;
import java.util.Date;
import jwtc.chess.GameSelection;

/** Separate saved filter values for the Game Database and Study selection screens. */
public final class GameSelectionPreferences {
    public static final String DATABASE = "gameslist_filter_";
    public static final String STUDY = "studies_filter_";

    private GameSelectionPreferences() {}

    public static GameSelection load(SharedPreferences prefs, String prefix) {
        GameSelection selection = new GameSelection();
        selection.white = prefs.getString(prefix + "white", selection.white);
        selection.black = prefs.getString(prefix + "black", selection.black);
        selection.event = prefs.getString(prefix + "event", selection.event);
        selection.topic = prefs.getString(prefix + "topic", selection.topic);
        selection.result = prefs.getString(prefix + "result", selection.result);
        selection.after = new Date(prefs.getLong(prefix + "date_after", selection.after.getTime()));
        selection.before =
                new Date(prefs.getLong(prefix + "date_before", selection.before.getTime()));
        selection.useWhite = prefs.getBoolean(prefix + "switch_white", false);
        selection.useBlack = prefs.getBoolean(prefix + "switch_black", false);
        selection.useEvent =
                prefs.getBoolean(
                        prefix + "switch_event",
                        STUDY.equals(prefix) && prefs.contains(prefix + "event"));
        selection.useTopic =
                prefs.getBoolean(
                        prefix + "switch_topic",
                        STUDY.equals(prefix) && prefs.contains(prefix + "topic"));
        selection.useAfter = prefs.getBoolean(prefix + "switch_date_after", false);
        selection.useBefore = prefs.getBoolean(prefix + "switch_date_before", false);
        selection.useResult = prefs.getBoolean(prefix + "switch_result", false);
        selection.sortColumn = prefs.getString(prefix + "order_by", selection.sortColumn);
        selection.sortDirection =
                prefs.getString(prefix + "order_direction", selection.sortDirection);
        return selection;
    }

    public static void save(SharedPreferences prefs, String prefix, GameSelection selection) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(prefix + "white", selection.white);
        editor.putString(prefix + "black", selection.black);
        editor.putString(prefix + "event", selection.event);
        editor.putString(prefix + "topic", selection.topic);
        editor.putString(prefix + "result", selection.result);
        if (selection.after != null) {
            editor.putLong(prefix + "date_after", selection.after.getTime());
        }
        if (selection.before != null) {
            editor.putLong(prefix + "date_before", selection.before.getTime());
        }
        editor.putBoolean(prefix + "switch_white", selection.useWhite);
        editor.putBoolean(prefix + "switch_black", selection.useBlack);
        editor.putBoolean(prefix + "switch_event", selection.useEvent);
        editor.putBoolean(prefix + "switch_topic", selection.useTopic);
        editor.putBoolean(prefix + "switch_date_after", selection.useAfter);
        editor.putBoolean(prefix + "switch_date_before", selection.useBefore);
        editor.putBoolean(prefix + "switch_result", selection.useResult);
        editor.putString(prefix + "order_by", selection.sortColumn);
        editor.putString(prefix + "order_direction", selection.sortDirection);
        editor.apply();
    }
}
