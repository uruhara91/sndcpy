package com.rom1v.sndcpy;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;

public class RecordService extends Service {
    private static final String TAG = "sndcpy";
    private static final String CHANNEL_ID = "sndcpy";
    private static final int NOTIFICATION_ID = 1;
    private static final String ACTION_RECORD = "com.rom1v.sndcpy.RECORD";
    private static final String ACTION_STOP = "com.rom1v.sndcpy.STOP";
    private static final String EXTRA_MEDIA_PROJECTION_DATA = "mediaProjectionData";
    private static final int MSG_CONNECTION_ESTABLISHED = 1;
    private static final String SOCKET_NAME = "sndcpy";

    static {
        System.loadLibrary("native-lib");
    }

    // Native Methods - Harus sesuai dengan native-lib.cpp
    private native void nativeStartCapture(MediaProjection projection);
    private native byte[] nativeReadAudio();
    private native void nativeStopCapture();

    private final Handler handler = new ConnectionHandler(this);
    private MediaProjection mediaProjection;
    private Thread recorderThread;

    public static void start(Context context, Intent data) {
        Intent intent = new Intent(context, RecordService.class);
        intent.setAction(ACTION_RECORD);
        intent.putExtra(EXTRA_MEDIA_PROJECTION_DATA, data);
        context.startForegroundService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Audio Capture", NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        startForeground(NOTIFICATION_ID, createNotification(false), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        Intent data = intent.getParcelableExtra(EXTRA_MEDIA_PROJECTION_DATA);
        MediaProjectionManager mpManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mediaProjection = mpManager.getMediaProjection(Activity.RESULT_OK, data);
        
        if (mediaProjection != null) {
            startRecording();
        } else {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void startRecording() {
        recorderThread = new Thread(() -> {
            try (LocalSocket socket = connect()) {
                handler.sendEmptyMessage(MSG_CONNECTION_ESTABLISHED);
                
                // Panggil Native AAudio
                nativeStartCapture(mediaProjection);

                try (OutputStream output = socket.getOutputStream()) {
                    while (!Thread.currentThread().isInterrupted()) {
                        byte[] buffer = nativeReadAudio();
                        if (buffer != null) {
                            output.write(buffer);
                        }
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket error", e);
            } finally {
                nativeStopCapture();
                if (mediaProjection != null) mediaProjection.stop();
                stopSelf();
            }
        });
        recorderThread.start();
    }

    private LocalSocket connect() throws IOException {
        LocalServerSocket localServerSocket = new LocalServerSocket(SOCKET_NAME);
        try {
            return localServerSocket.accept();
        } finally {
            localServerSocket.close();
        }
    }

    private Notification createNotification(boolean established) {
        Intent stopIntent = new Intent(this, RecordService.class).setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Sndcpy Audio")
                .setContentText(established ? "Forwarding audio..." : "Waiting for connection...")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .addAction(new Notification.Action.Builder(null, "STOP", stopPendingIntent).build())
                .build();
    }

    @Override public void onDestroy() {
        if (recorderThread != null) recorderThread.interrupt();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private static final class ConnectionHandler extends Handler {
        private final RecordService service;
        ConnectionHandler(RecordService service) { this.service = service; }
        @Override
        public void handleMessage(Message message) {
            if (message.what == MSG_CONNECTION_ESTABLISHED) {
                NotificationManager nm = (NotificationManager) service.getSystemService(NOTIFICATION_SERVICE);
                nm.notify(NOTIFICATION_ID, service.createNotification(true));
            }
        }
    }
}
