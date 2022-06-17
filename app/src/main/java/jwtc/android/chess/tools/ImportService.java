package jwtc.android.chess.tools;

import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import androidx.annotation.Nullable;
import jwtc.android.chess.services.GameApi;
import jwtc.chess.PGNColumns;


public class ImportService extends Service {
    private static final String TAG = "ImportService";

    public static final int IMPORT_LOCAL_PGN = 0;

    protected ArrayList<ImportListener> listeners = new ArrayList<>();
    private PGNProcessor _processor;
    private ImportApi importApi;
    private final IBinder mBinder = new ImportService.LocalBinder();

    public void addListener(ImportListener listener) {
        this.listeners.add(listener);
    }

    public void removeListener(ImportListener listener) {
        this.listeners.remove(listener);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Received start id " + startId + ": " + intent);

        final Uri uri = intent.getData();
        Bundle extras = intent.getExtras();

        // IMPORT_LOCAL_PGN
        _processor = new PGNImportProcessor(importApi, getContentResolver());
        _processor.m_threadUpdateHandler = new ImportService.ThreadMessageHandler(this);

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy");

        if (_processor != null) {
            _processor.stopProcessing();
        }
    }

    public void handleThreadMessage(Message msg) {
        for (ImportListener listener : listeners) {
            switch (msg.what) {
                case PGNProcessor.MSG_PROCESSED_PGN:
                    listener.OnImportSuccess();
                    break;
                case PGNProcessor.MSG_FAILED_PGN:
                    listener.OnImportFail();
                    break;
                case PGNProcessor.MSG_FINISHED:
                    listener.OnImportFinished();
                    break;
                case PGNProcessor.MSG_FATAL_ERROR:
                    listener.OnImportFatalError();
                    break;
            }
        }
    }

    private class LocalBinder extends Binder {
        ImportService getService() {
            return ImportService.this;
        }
    }


    private class ThreadMessageHandler extends Handler {
        private WeakReference<ImportService> serverWeakReference;

        ThreadMessageHandler(ImportService importService) {
            this.serverWeakReference = new WeakReference<ImportService>(importService);
        }

        @Override
        public void handleMessage(Message msg) {
            ImportService importService = serverWeakReference.get();
            if (importService != null) {
                importService.handleThreadMessage(msg);
                super.handleMessage(msg);
            }
        }
    }

    private class ImportApi extends GameApi {}
}
