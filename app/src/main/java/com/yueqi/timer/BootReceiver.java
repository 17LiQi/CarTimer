package com.yueqi.timer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            PowerManager.WakeLock wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Timer:BootWakeLock");
            
            try {
                wakeLock.acquire(10*60*1000L /*10 minutes*/);
                
                SharedPreferences prefs = context.getSharedPreferences("TimerPrefs", Context.MODE_PRIVATE);
                boolean autoStartTimer = prefs.getBoolean("auto_start_timer", false);
                
                if (autoStartTimer) {
                    // 使用前台服务启动
                    Intent serviceIntent = new Intent(context, TimerService.class)
                            .setAction(TimerService.ACTION_START);
                    
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent);
                    } else {
                        context.startService(serviceIntent);
                    }
                    
                    boolean autoMinimize = prefs.getBoolean("auto_minimize", false);
                    if (autoMinimize) {
                        // 延迟启动悬浮窗服务，确保主服务已经启动
                        new android.os.Handler().postDelayed(() -> {
                            context.startService(new Intent(context, FloatingWindowService.class));
                        }, 2000);
                    }
                }
            } finally {
                if (wakeLock.isHeld()) {
                    wakeLock.release();
                }
            }
        }
    }
} 