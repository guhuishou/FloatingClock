package com.avatarmind.floatingclock;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import com.avatarmind.floatingclock.service.FloatingService;
import com.avatarmind.floatingclock.util.ClockInfo;
import com.avatarmind.floatingclock.util.LogUtil;
import com.avatarmind.floatingclock.util.NtpSyncUtil;
import com.avatarmind.floatingclock.util.SharedPreferencesUtil;
import com.avatarmind.floatingclock.util.ToastUtil;
import com.avatarmind.floatingclock.util.Util;
import com.avatarmind.floatingclock.util.event.UpdateClockViewEvent;

import org.greenrobot.eventbus.EventBus;

public class MainActivity extends Activity {
    private TextView mTVClockSize;
    private EditText mEtClockSize;
    private TextView mTvNtpStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SharedPreferencesUtil.initSharedPreferences(MainActivity.this);
        ClockInfo clockInfo = SharedPreferencesUtil.getClockInfo(MainActivity.this);

        // 时钟大小显示
        mTVClockSize = (TextView) findViewById(R.id.tv_clocksize);
        mTVClockSize.setText(getString(R.string.currentclocksize) + clockInfo.getTextSize());

        mEtClockSize = (EditText) findViewById(R.id.et_clocksize);
        mEtClockSize.setText(String.valueOf(clockInfo.getTextSize()));
        mEtClockSize.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                try {
                    String text = mEtClockSize.getText().toString();
                    if (TextUtils.isEmpty(text)) return;
                    ClockInfo ci = SharedPreferencesUtil.getClockInfo(MainActivity.this);
                    int size = Integer.parseInt(text);
                    if (size <= 0 || size > 100) {
                        ToastUtil.showToast(MainActivity.this, getString(R.string.clocksizeremind));
                        mEtClockSize.setText("");
                        return;
                    }
                    ci.setTextSize(size);
                    EventBus.getDefault().post(new UpdateClockViewEvent(ci));
                    mTVClockSize.setText(getString(R.string.currentclocksize) + ci.getTextSize());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        // 后台运行开关
        Switch switchCloseClock = (Switch) findViewById(R.id.st_close_clock);
        switchCloseClock.setChecked(SharedPreferencesUtil.isExit(MainActivity.this));
        switchCloseClock.setOnCheckedChangeListener((buttonView, isChecked) ->
                SharedPreferencesUtil.setIsExit(MainActivity.this, isChecked));

        // 显示毫秒开关
        Switch switchShowMillis = (Switch) findViewById(R.id.st_show_millis);
        switchShowMillis.setChecked(clockInfo.isShowMillis());
        switchShowMillis.setOnCheckedChangeListener((buttonView, isChecked) -> {
            ClockInfo ci = SharedPreferencesUtil.getClockInfo(MainActivity.this);
            ci.setShowMillis(isChecked);
            EventBus.getDefault().post(new UpdateClockViewEvent(ci));
            SharedPreferencesUtil.setClockInfo(MainActivity.this, ci);
        });

        // NTP 同步状态显示
        mTvNtpStatus = (TextView) findViewById(R.id.tv_ntp_status);
        updateNtpStatusText();

        // 手动同步按钮
        findViewById(R.id.btn_ntp_sync).setOnClickListener(v -> {
            mTvNtpStatus.setText(getString(R.string.ntp_syncing));
            NtpSyncUtil.sync(MainActivity.this, new NtpSyncUtil.SyncCallback() {
                @Override
                public void onSyncSuccess(long offsetMs) {
                    updateNtpStatusText();
                    ToastUtil.showToast(MainActivity.this, getString(R.string.ntp_sync_success));
                }
                @Override
                public void onSyncFailed(String reason) {
                    mTvNtpStatus.setText(getString(R.string.ntp_sync_failed));
                    ToastUtil.showToast(MainActivity.this, getString(R.string.ntp_sync_failed));
                }
            });
        });

        checkOverlayPermission();
    }

    private void updateNtpStatusText() {
        if (NtpSyncUtil.isSynced()) {
            mTvNtpStatus.setText(getString(R.string.ntp_synced));
        } else {
            mTvNtpStatus.setText(getString(R.string.ntp_not_synced));
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            super.onSaveInstanceState(outState);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!SharedPreferencesUtil.isExit(MainActivity.this))
            Util.stopService(MainActivity.this, FloatingService.class);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 0) {
            if (!Settings.canDrawOverlays(this)) {
                ToastUtil.showToast(MainActivity.this, "授权失败");
            } else {
                ToastUtil.showToast(MainActivity.this, "授权成功");
                Util.startService(MainActivity.this, FloatingService.class);
            }
        }
    }

    private void checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                ToastUtil.showToast(MainActivity.this, "应用没有显示悬浮窗权限，请授权");
                startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())), 0);
            } else {
                Util.startService(MainActivity.this, FloatingService.class);
            }
        } else {
            Util.startService(MainActivity.this, FloatingService.class);
        }
    }
}
