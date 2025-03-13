package com.yueqi.timer;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

public class FloatingWindowService extends Service {
    private static final String PREFS_NAME = "TimerPrefs";
    private static final String PREF_X = "window_x";
    private static final String PREF_Y = "window_y";
    private int initialX;
    private int initialY;
    private float initialTouchX;
    private float initialTouchY;

    private WindowManager windowManager;
    private View floatingView;
    private TextView floatingTimerText;
    private BroadcastReceiver timerReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        
        initFloatingWindow();
        registerTimerReceiver();
    }

    private void initFloatingWindow() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_timer, null);
        floatingTimerText = floatingView.findViewById(R.id.floating_timer_text);

        // 读取保存的位置
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int x = prefs.getInt(PREF_X, 0);
        int y = prefs.getInt(PREF_Y, 100);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = x;
        params.y = y;
        
        // 设置触摸监听
        floatingView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingView, params);
                        return true;

                    case MotionEvent.ACTION_UP:
                        // 保存位置
                        android.content.SharedPreferences.Editor editor = 
                            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
                        editor.putInt(PREF_X, params.x);
                        editor.putInt(PREF_Y, params.y);
                        editor.apply();

                        // 如果移动距离很小，认为是点击事件
                        if (Math.abs(event.getRawX() - initialTouchX) < 10 &&
                            Math.abs(event.getRawY() - initialTouchY) < 10) {
                            Intent intent = new Intent(FloatingWindowService.this, MainActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        }
                        return true;
                }
                return false;
            }
        });

        windowManager.addView(floatingView, params);
    }

    private void registerTimerReceiver() {
        timerReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (TimerService.ACTION_TIME_UPDATE.equals(intent.getAction())) {
                    long timeInMillis = intent.getLongExtra("time", 0);
                    updateTimerDisplay(timeInMillis);
                }
            }
        };
        IntentFilter filter = new IntentFilter(TimerService.ACTION_TIME_UPDATE);
        registerReceiver(timerReceiver, filter);
    }

    private void updateTimerDisplay(long timeInMillis) {
        int hours = (int) (timeInMillis / 3600000);
        int minutes = (int) ((timeInMillis % 3600000) / 60000);
        int seconds = (int) ((timeInMillis % 60000) / 1000);
        String timeString = String.format("%02d:%02d:%02d", hours, minutes, seconds);
        floatingTimerText.setText(timeString);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null) {
            windowManager.removeView(floatingView);
        }
        if (timerReceiver != null) {
            unregisterReceiver(timerReceiver);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
} 