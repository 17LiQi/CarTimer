package com.yueqi.timer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

public class MainActivity extends AppCompatActivity {
    private TextView timerTextView;
    private Button startButton, pauseButton, stopButton, minimizeButton;
    private static final int OVERLAY_PERMISSION_REQUEST_CODE = 1;
    private BroadcastReceiver timerReceiver;
    private static final String PREFS_NAME = "TimerPrefs";
    private static final String PREF_FIRST_RUN = "first_run";
    private static final String PREF_AUTO_START_TIMER = "auto_start_timer";
    private static final String PREF_AUTO_MINIMIZE = "auto_minimize";
    private ActivityResultLauncher<Intent> overlayPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 注册权限请求启动器
        overlayPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                // 检查权限是否已授予
                if (Settings.canDrawOverlays(this)) {
                    // 权限已授予，可以执行相关操作
                }
            }
        );

        initViews();
        checkPermissions();
        registerTimerReceiver();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 查询计时器当前状态
        Intent intent = new Intent(this, TimerService.class);
        intent.setAction(TimerService.ACTION_GET_STATUS);
        startService(intent);
    }

    private void initViews() {
        timerTextView = findViewById(R.id.timerTextView);
        startButton = findViewById(R.id.startButton);
        pauseButton = findViewById(R.id.pauseButton);
        stopButton = findViewById(R.id.stopButton);
        minimizeButton = findViewById(R.id.minimizeButton);

        // 检查是否是首次运行
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean isFirstRun = prefs.getBoolean(PREF_FIRST_RUN, true);

        TextView autoStartHint = findViewById(R.id.autoStartHint);
        Button settingsButton = findViewById(R.id.settingsButton);

        // 让提示和按钮始终可见
        autoStartHint.setVisibility(View.VISIBLE);
        settingsButton.setVisibility(View.VISIBLE);
        
        // 只在首次运行时记录
        if (isFirstRun) {
            prefs.edit().putBoolean(PREF_FIRST_RUN, false).apply();
        }

        settingsButton.setOnClickListener(v -> openAutoStartSettings());

        // 初始化自动计时复选框
        CheckBox autoStartTimerCheckBox = findViewById(R.id.autoStartTimerCheckBox);
        
        // 读取保存的自动计时设置
        boolean autoStartTimer = prefs.getBoolean(PREF_AUTO_START_TIMER, false);
        autoStartTimerCheckBox.setChecked(autoStartTimer);
        
        // 如果启用了自动计时，则立即开始计时
        if (autoStartTimer) {
            startTimer();
        }

        // 保存复选框状态
        autoStartTimerCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(PREF_AUTO_START_TIMER, isChecked).apply();
        });

        // 初始化自动最小化复选框
        CheckBox autoMinimizeCheckBox = findViewById(R.id.autoMinimizeCheckBox);
        boolean autoMinimize = prefs.getBoolean(PREF_AUTO_MINIMIZE, false);
        autoMinimizeCheckBox.setChecked(autoMinimize);

        // 保存自动最小化设置
        autoMinimizeCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(PREF_AUTO_MINIMIZE, isChecked).apply();
        });

        // 如果设置了自动最小化，立即最小化
        if (autoMinimize && prefs.getBoolean(PREF_AUTO_START_TIMER, false)) {
            startService(new Intent(this, FloatingWindowService.class));
            moveTaskToBack(true);
        }

        startButton.setOnClickListener(v -> startTimer());

        pauseButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, TimerService.class);
            if (pauseButton.getText().equals("暂停")) {
                intent.setAction(TimerService.ACTION_PAUSE);
                pauseButton.setText("继续");
            } else {
                intent.setAction(TimerService.ACTION_RESUME);
                pauseButton.setText("暂停");
            }
            startService(intent);
        });

        stopButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, TimerService.class);
            intent.setAction(TimerService.ACTION_STOP);
            startService(intent);
            updateButtonStates(false);
        });

        minimizeButton.setOnClickListener(v -> {
            startService(new Intent(this, FloatingWindowService.class));
            moveTaskToBack(true);
        });

        updateButtonStates(false);
    }

    private void updateButtonStates(boolean isRunning) {
        startButton.setEnabled(!isRunning);
        pauseButton.setEnabled(isRunning);
        stopButton.setEnabled(isRunning);
    }

    private void checkPermissions() {
        // 检查悬浮窗权限
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            // 使用新的 API 替代 startActivityForResult
            overlayPermissionLauncher.launch(intent);
        }
    }

    private void registerTimerReceiver() {
        timerReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (TimerService.ACTION_TIME_UPDATE.equals(intent.getAction())) {
                    long timeInMillis = intent.getLongExtra("time", 0);
                    updateTimerDisplay(timeInMillis);
                    
                    // 更新按钮状态
                    if (intent.hasExtra(TimerService.EXTRA_IS_RUNNING)) {
                        boolean isRunning = intent.getBooleanExtra(TimerService.EXTRA_IS_RUNNING, false);
                        updateButtonStates(isRunning);
                        if (isRunning) {
                            pauseButton.setText("暂停");
                        }
                    }
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
        timerTextView.setText(timeString);
    }

    private void openAutoStartSettings() {
        try {
            Intent intent = new Intent();
            intent.setAction("android.settings.APPLICATION_DETAILS_SETTINGS");
            intent.setData(Uri.fromParts("package", getPackageName(), null));
            startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 提取开始计时的逻辑为单独的方法
    private void startTimer() {
        Intent intent = new Intent(this, TimerService.class);
        intent.setAction(TimerService.ACTION_START);
        startService(intent);
        updateButtonStates(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timerReceiver != null) {
            unregisterReceiver(timerReceiver);
        }
    }
} 