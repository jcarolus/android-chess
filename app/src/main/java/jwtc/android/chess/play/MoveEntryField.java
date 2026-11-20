package jwtc.android.chess.play;

import android.content.Context;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.TextAttribute;
import androidx.appcompat.widget.AppCompatEditText;
import java.util.function.Consumer;
import jwtc.android.chess.R;

final class MoveEntryField extends AppCompatEditText {
    private final Consumer<String> submit;
    private int inputGeneration;
    private int textRevision;
    private int focusRevision;

    MoveEntryField(Context context, Consumer<String> submit) {
        super(context);
        this.submit = submit;
        setSingleLine();
        setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        setHint(R.string.move_entry_hint);
        setOnEditorActionListener(
                (view, action, event) -> {
                    if (action == EditorInfo.IME_ACTION_DONE
                            || (event != null
                                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                                    && event.getAction() == KeyEvent.ACTION_DOWN)) {
                        submit.accept(getText().toString());
                        return true;
                    }
                    return false;
                });
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int before, int after) {
        super.onTextChanged(text, start, before, after);
        textRevision++;
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, android.graphics.Rect previous) {
        super.onFocusChanged(focused, direction, previous);
        focusRevision++;
    }

    int textRevision() {
        return textRevision;
    }

    void resetConnection(boolean clear) {
        inputGeneration++;
        if (clear) {
            setText("");
        }
        setError(null);
        if (hasFocus()) {
            getContext().getSystemService(InputMethodManager.class).restartInput(this);
        }
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo info) {
        InputConnection connection = super.onCreateInputConnection(info);
        if (connection == null) {
            return null;
        }
        // Android can create another connection while the IME still uses this one.
        int generation = inputGeneration;
        return new InputConnectionWrapper(connection, false) {
            private boolean closed;

            private boolean isCurrent() {
                return !closed && generation == inputGeneration;
            }

            @Override
            public boolean commitText(CharSequence text, int cursor) {
                return isCurrent() && committed(text, super.commitText(text, cursor));
            }

            @Override
            public boolean commitText(CharSequence text, int cursor, TextAttribute attribute) {
                return isCurrent()
                        && committed(text, super.commitText(text, cursor, attribute));
            }

            @Override
            public boolean setComposingText(CharSequence text, int cursor) {
                return isCurrent() && super.setComposingText(text, cursor);
            }

            @Override
            public boolean setComposingText(
                    CharSequence text, int cursor, TextAttribute attribute) {
                return isCurrent() && super.setComposingText(text, cursor, attribute);
            }

            @Override
            public boolean setComposingRegion(int start, int end) {
                return isCurrent() && super.setComposingRegion(start, end);
            }

            @Override
            public boolean setComposingRegion(int start, int end, TextAttribute attribute) {
                return isCurrent() && super.setComposingRegion(start, end, attribute);
            }

            @Override
            public boolean replaceText(
                    int start, int end, CharSequence text, int cursor, TextAttribute attribute) {
                return isCurrent() && super.replaceText(start, end, text, cursor, attribute);
            }

            @Override
            public boolean sendKeyEvent(KeyEvent event) {
                return isCurrent() && super.sendKeyEvent(event);
            }

            @Override
            public boolean performEditorAction(int action) {
                return isCurrent() && super.performEditorAction(action);
            }

            @Override
            public void closeConnection() {
                closed = true;
                super.closeConnection();
            }

            private boolean committed(CharSequence text, boolean accepted) {
                String value = text.toString();
                int revision = textRevision;
                int focusedRevision = focusRevision;
                // Single-character commits are ordinary typing. The editor action submits typed
                // moves.
                if (accepted && value.length() > 1 && value.contentEquals(getText())) {
                    post(
                            () -> {
                                if (isCurrent()
                                        && revision == textRevision
                                        && focusedRevision == focusRevision
                                        && hasFocus()
                                        && hasWindowFocus()
                                        && value.contentEquals(getText())) {
                                    submit.accept(value);
                                }
                            });
                }
                return accepted;
            }
        };
    }
}
