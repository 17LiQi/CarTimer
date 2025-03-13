package com.yueqi.timer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;

public class TimerService extends Service {
    public static final String ACTION_START = "com.yueqi.timer.ACTION_START";
    public static final String ACTION_PAUSE = "com.yueqi.timer.ACTION_PAUSE";
    public static final String ACTION_STOP = "com.yueqi.timer.ACTION_STOP";
    public static final String ACTION_TIME_UPDATE = "com.yueqi.timer.ACTION_TIME_UPDATE";
    public static final String ACTION_RESUME = "com.yueqi.timer.ACTION_RESUME";
    public static final String ACTION_GET_STATUS = "com.yueqi.timer.ACTION_GET_STATUS";
    public static final String EXTRA_IS_RUNNING = "is_running";

    private final Handler handler;
    private long startTime;
    private long elapsedTime;
    private boolean isRunning;
    private Runnable timerRunnable;
    private static final String CHANNEL_ID = "timer_channel";
    private static final int NOTIFICATION_ID = 1;

    public TimerService() {
        handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRunning) {
                    elapsedTime = System.currentTimeMillis() - startTime;
                    broadcastTime();
                    updateNotification();
                    handler.postDelayed(this, 1000);
                }
            }
        };
    }

    private void createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "计时器服务",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private void startForegroundService() {
        Notification notification = createNotification();
        startForeground(NOTIFICATION_ID, notification);
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("计时器正在运行")
                .setContentText(formatTime(elapsedTime))
                .setSmallIcon(R.mipmap.timer)
                .setContentIntent(pendingIntent)
                .build();
    }

    private void updateNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(NOTIFICATION_ID, createNotification());
    }

    private String formatTime(long timeInMillis) {
        int hours = (int) (timeInMillis / 3600000);
        int minutes = (int) ((timeInMillis % 3600000) / 60000);
        int seconds = (int) ((timeInMillis % 60000) / 1000);
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_START:
                    startTimer();
                    startForegroundService();
                    break;
                case ACTION_PAUSE:
                    pauseTimer();
                    break;
                case ACTION_STOP:
                    stopTimer();
                    break;
                case ACTION_RESUME:
                    startTimer();
                    break;
                case ACTION_GET_STATUS:
                    Intent statusIntent = new Intent(ACTION_TIME_UPDATE);
                    statusIntent.putExtra(EXTRA_IS_RUNNING, isRunning);
                    statusIntent.putExtra("time", elapsedTime);
                    sendBroadcast(statusIntent);
                    break;
            }
        }
        return START_STICKY;
    }

    private void startTimer() {
        if (!isRunning) {
            isRunning = true;
            startTime = System.currentTimeMillis() - elapsedTime;
            handler.post(timerRunnable);
        }
    }

    private void pauseTimer() {
        isRunning = false;
        handler.removeCallbacks(timerRunnable);
    }

    private void stopTimer() {
        isRunning = false;
        elapsedTime = 0;
        handler.removeCallbacks(timerRunnable);
        broadcastTime();
    }

    private void broadcastTime() {
        Intent intent = new Intent(ACTION_TIME_UPDATE);
        intent.putExtra("time", elapsedTime);
        sendBroadcast(intent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(timerRunnable);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        if (isRunning) {
            SharedPreferences prefs = getSharedPreferences("TimerPrefs", Context.MODE_PRIVATE);
            prefs.edit().putLong("last_elapsed_time", elapsedTime).apply();
        }
    }
} 