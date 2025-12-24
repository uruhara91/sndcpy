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

import java.io.FileOutputStream;
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

    // Native Methods
    static {
        System.loadLibrary("native-lib");
    }
    private native void nativeStartCapture(MediaProjection projection);
    private native byte[] nativeReadAudio();
    private native void nativeStopCapture();

    private final Handler handler = new ConnectionHandler(this);
    private MediaProjectionManager mediaProjectionManager;
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
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, getString(R.string.app_name), NotificationManager.IMPORTANCE_NONE);
        getNotificationManager().createNotificationChannel(channel);
        startForeground(NOTIFICATION_ID, createNotification(false), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (isRunning()) return START_NOT_STICKY;

        Intent data = intent.getParcelableExtra(EXTRA_MEDIA_PROJECTION_DATA);
        mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mediaProjection = mediaProjectionManager.getMediaProjection(Activity.RESULT_OK, data);
        
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
                
                // Inisialisasi Native Audio dengan MediaProjection
                nativeStartCapture(mediaProjection);

                try (OutputStream output = socket.getOutputStream()) {
                    while (!Thread.currentThread().isInterrupted()) {
                        byte[] buffer = nativeReadAudio();
                        if (buffer != null && buffer.length > 0) {
                            output.write(buffer);
                        }
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket error", e);
            } finally {
                nativeStopCapture();
                stopSelf();
            }
        });
        recorderThread.start();
    }

    private static LocalSocket connect() throws IOException {
        LocalServerSocket localServerSocket = new LocalServerSocket(SOCKET_NAME);
        try {
            return localServerSocket.accept();
        } finally {
            localServerSocket.close();
        }
    }

    private Notification createNotification(boolean established) {
        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getText(established ? R.string.notification_forwarding : R.string.notification_waiting))
                .setSmallIcon(R.drawable.ic_album_black_24dp)
                .addAction(createStopAction());
        return builder.build();
    }

    private Notification.Action createStopAction() {
        Intent stopIntent = new Intent(this, RecordService.class).setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_close_24dp), getString(R.string.action_stop), stopPendingIntent).build();
    }

    private boolean isRunning() { return recorderThread != null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (recorderThread != null) {
            recorderThread.interrupt();
            recorderThread = null;
        }
    }

    @Override public IBinder onBind(Intent intent) { return null; }
    private NotificationManager getNotificationManager() { return (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE); }

    private static final class ConnectionHandler extends Handler {
        private RecordService service;
        ConnectionHandler(RecordService service) { this.service = service; }
        @Override
        public void handleMessage(Message message) {
            if (message.what == MSG_CONNECTION_ESTABLISHED && service.isRunning()) {
                service.getNotificationManager().notify(NOTIFICATION_ID, service.createNotification(true));
            }
        }
    }
}
