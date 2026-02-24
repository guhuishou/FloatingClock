package com.avatarmind.floatingclock.service;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.avatarmind.floatingclock.util.ClockInfo;
import com.avatarmind.floatingclock.util.NtpSyncUtil;
import com.avatarmind.floatingclock.util.SharedPreferencesUtil;
import com.avatarmind.floatingclock.util.event.UpdateClockViewEvent;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FloatingService extends Service {
    private WindowManager windowManager;
    private WindowManager.LayoutParams layoutParams;
    // 替换 TextClock 为 TextView，这样我们可以完全控制显示内容
    private TextView mClockTextView;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mShowMillis = false;

    // 不显示毫秒时每秒刷新一次；显示毫秒时每 50ms 刷新一次（约 20fps）
    private static final long REFRESH_NORMAL_MS = 1000L;
    private static final long REFRESH_MILLIS_MS = 50L;

    private final Runnable mClockRunnable = new Runnable() {
        @Override
        public void run() {
            updateClockDisplay();
            mHandler.postDelayed(this, mShowMillis ? REFRESH_MILLIS_MS : REFRESH_NORMAL_MS);
            // 每次刷新顺带检查是否需要重新同步 NTP
            NtpSyncUtil.syncIfNeeded(FloatingService.this);
        }
    };

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        init();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        uninit();
    }

    private void init() {
        EventBus.getDefault().register(this);

        ClockInfo clockInfo = SharedPreferencesUtil.getClockInfo(this);
        mShowMillis = clockInfo.isShowMillis();

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        layoutParams = new WindowManager.LayoutParams();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutParams.type = WindowManager.LayoutParams.TYPE_PHONE;
        }
        layoutParams.format = PixelFormat.RGBA_8888;
        layoutParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        layoutParams.x = clockInfo.getX();
        layoutParams.y = clockInfo.getY();

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
            mClockTextView = new TextView(getApplicationContext());
            mClockTextView.setTextSize(clockInfo.getTextSize());
            mClockTextView.setGravity(Gravity.CENTER);
            mClockTextView.setTextColor(Color.BLACK);
            mClockTextView.setBackgroundColor(Color.WHITE);
            mClockTextView.setPadding(8, 4, 8, 4);
            mClockTextView.setOnTouchListener(new FloatingOnTouchListener());

            windowManager.addView(mClockTextView, layoutParams);

            // 立即触发一次 NTP 同步
            NtpSyncUtil.sync(this, null);

            // 启动时钟刷新循环
            mHandler.post(mClockRunnable);
        }
    }

    private void uninit() {
        mHandler.removeCallbacks(mClockRunnable);
        EventBus.getDefault().unregister(this);
        if (mClockTextView != null && mClockTextView.isAttachedToWindow()) {
            windowManager.removeView(mClockTextView);
        }
    }

    /**
     * 更新时钟文字显示
     * 使用 NTP 校正后的时间
     */
    private void updateClockDisplay() {
        if (mClockTextView == null) return;
        long timeMs = NtpSyncUtil.getCorrectedTimeMillis();
        String pattern = mShowMillis ? "HH:mm:ss.SSS" : "HH:mm:ss";
        SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.getDefault());
        mClockTextView.setText(sdf.format(new Date(timeMs)));
    }

    private class FloatingOnTouchListener implements View.OnTouchListener {
        private int x;
        private int y;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    x = (int) event.getRawX();
                    y = (int) event.getRawY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    int nowX = (int) event.getRawX();
                    int nowY = (int) event.getRawY();
                    int movedX = nowX - x;
                    int movedY = nowY - y;
                    x = nowX;
                    y = nowY;
                    layoutParams.x += movedX;
                    layoutParams.y += movedY;
                    windowManager.updateViewLayout(view, layoutParams);

                    ClockInfo clockInfo = SharedPreferencesUtil.getClockInfo(FloatingService.this);
                    clockInfo.setX(layoutParams.x);
                    clockInfo.setY(layoutParams.y);
                    SharedPreferencesUtil.setClockInfo(FloatingService.this, clockInfo);
                    break;
                default:
                    break;
            }
            return false;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(UpdateClockViewEvent event) {
        if (event != null && mClockTextView != null) {
            ClockInfo clockInfo = event.getClockInfo();
            mClockTextView.setTextSize(clockInfo.getTextSize());
            mShowMillis = clockInfo.isShowMillis();
            // 重置刷新循环以应用新的刷新频率
            mHandler.removeCallbacks(mClockRunnable);
            mHandler.post(mClockRunnable);
            SharedPreferencesUtil.setClockInfo(FloatingService.this, clockInfo);
        }
    }
}
