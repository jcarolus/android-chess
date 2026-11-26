package jwtc.android.chess.gamedatabase;

import android.app.Application;
import android.util.Log;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import jwtc.android.chess.helpers.GameStore;

/** Keeps an in-progress database operation alive across screen recreation. */
public final class BulkGameEditModel extends AndroidViewModel {
    public enum State {
        IDLE,
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    private final MutableLiveData<State> state = new MutableLiveData<>(State.IDLE);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public BulkGameEditModel(Application application) {
        super(application);
    }

    public LiveData<State> state() {
        return state;
    }

    public boolean isRunning() {
        return state.getValue() == State.RUNNING;
    }

    public void delete(long[] ids) {
        execute(ids.clone(), null, true);
    }

    public void setTopic(long[] ids, String topic) {
        execute(ids.clone(), topic, false);
    }

    private void execute(long[] ids, String topic, boolean delete) {
        if (isRunning() || ids.length == 0) {
            return;
        }
        state.setValue(State.RUNNING);
        executor.execute(
                () -> {
                    try {
                        GameStore store = new GameStore(getApplication().getContentResolver());
                        if (delete) {
                            store.deleteSelected(ids);
                        } else {
                            store.setSelectedTopic(ids, topic);
                        }
                        state.postValue(State.SUCCEEDED);
                    } catch (Exception e) {
                        Log.e("BulkGameEdit", "Could not edit selected games", e);
                        state.postValue(State.FAILED);
                    }
                });
    }

    public void acknowledgeResult() {
        state.setValue(State.IDLE);
    }

    @Override
    protected void onCleared() {
        executor.shutdown();
    }
}
