package jwtc.android.chess.play;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.TextAttribute;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.util.function.Consumer;

final class DictationProbe extends AppCompatEditText {
    static final String TAG = "PlayDictationProbe";

    private final Consumer<DictatedMove> onCandidate;
    private int revision;
    private int connectionId;

    DictationProbe(Context context, Consumer<DictatedMove> onCandidate) {
        super(context);
        this.onCandidate = onCandidate;
        setHint("Move, for example e2 e4");
        setSingleLine(true);
        addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        revision++;
                        Log.d(TAG, "text changed: " + s);
                    }

                    @Override
                    public void afterTextChanged(Editable text) {}
                });
        setOnFocusChangeListener(
                (view, focused) -> {
                    revision++;
                    Log.d(TAG, "input focus: " + focused);
                });
        ViewCompat.setOnApplyWindowInsetsListener(
                this,
                (view, insets) -> {
                    Log.d(TAG, "IME visible: " + insets.isVisible(WindowInsetsCompat.Type.ime()));
                    return insets;
                });
    }

    void resetForPosition() {
        connectionId++;
        setText("");
        if (hasFocus()) {
            getContext().getSystemService(InputMethodManager.class).restartInput(this);
        }
    }

    @Override
    public void sendAccessibilityEvent(int eventType) {
        if (eventType == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED
                || eventType == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED) {
            Log.d(TAG, "accessibility focus event: " + eventType);
        }
        super.sendAccessibilityEvent(eventType);
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo info) {
        InputConnection connection = super.onCreateInputConnection(info);
        if (connection == null) {
            return null;
        }
        int id = ++connectionId;
        return new InputConnectionWrapper(connection, false) {
            @Override
            public boolean beginBatchEdit() {
                Log.d(TAG, "beginBatchEdit");
                return super.beginBatchEdit();
            }

            @Override
            public boolean endBatchEdit() {
                Log.d(TAG, "endBatchEdit");
                return super.endBatchEdit();
            }

            @Override
            public boolean setComposingRegion(int start, int end) {
                Log.d(TAG, "setComposingRegion: " + start + ".." + end);
                return super.setComposingRegion(start, end);
            }

            @Override
            public boolean setComposingRegion(int start, int end, TextAttribute attribute) {
                Log.d(TAG, "setComposingRegion: " + start + ".." + end);
                return super.setComposingRegion(start, end, attribute);
            }

            @Override
            public boolean replaceText(
                    int start, int end, CharSequence text, int cursor, TextAttribute attribute) {
                Log.d(TAG, "replaceText: " + start + ".." + end + " with " + text);
                return super.replaceText(start, end, text, cursor, attribute);
            }

            @Override
            public boolean setComposingText(CharSequence text, int cursor) {
                Log.d(TAG, "setComposingText: " + text);
                return super.setComposingText(text, cursor);
            }

            @Override
            public boolean finishComposingText() {
                Log.d(TAG, "finishComposingText");
                return super.finishComposingText();
            }

            @Override
            public boolean commitText(CharSequence text, int cursor) {
                return inspectCommit(text.toString(), super.commitText(text, cursor));
            }

            @Override
            public boolean commitText(CharSequence text, int cursor, TextAttribute attribute) {
                return inspectCommit(text.toString(), super.commitText(text, cursor, attribute));
            }

            @Override
            public boolean setComposingText(
                    CharSequence text, int cursor, TextAttribute attribute) {
                Log.d(TAG, "setComposingText: " + text);
                return super.setComposingText(text, cursor, attribute);
            }

            @Override
            public void closeConnection() {
                if (id == connectionId) {
                    connectionId++;
                }
                super.closeConnection();
            }

            private boolean inspectCommit(String committedText, boolean accepted) {
                Log.d(TAG, "commitText: " + committedText);
                int committedRevision = revision;
                DictatedMove candidate = DictatedMove.parse(committedText);
                if (accepted && candidate != null && committedText.contentEquals(getText())) {
                    post(
                            () -> {
                                if (id == connectionId
                                        && committedRevision == revision
                                        && hasFocus()
                                        && hasWindowFocus()) {
                                    Log.d(TAG, "whole-field commit candidate: " + committedText);
                                    onCandidate.accept(candidate);
                                }
                            });
                }
                return accepted;
            }
        };
    }
}
