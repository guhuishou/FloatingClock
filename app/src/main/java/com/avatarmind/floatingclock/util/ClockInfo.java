package com.avatarmind.floatingclock.util;

import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;

public class ClockInfo {
    private int x;
    private int y;
    private int textSize;
    private boolean showMillis;   // 是否显示毫秒

    public ClockInfo(int x, int y, int textSize) {
        this.x = x;
        this.y = y;
        this.textSize = textSize;
        this.showMillis = false;
    }

    public int getX() { return x; }
    public void setX(int x) { this.x = x; }

    public int getY() { return y; }
    public void setY(int y) { this.y = y; }

    public int getTextSize() { return textSize; }
    public void setTextSize(int textSize) { this.textSize = textSize; }

    public boolean isShowMillis() { return showMillis; }
    public void setShowMillis(boolean showMillis) { this.showMillis = showMillis; }

    public String getString() {
        return new Gson().toJson(this);
    }

    public static String getDefault() {
        return new ClockInfo(1, 1, 30).getString();
    }

    public static ClockInfo getClockInfo(String json) {
        if (TextUtils.isEmpty(json)) {
            return null;
        }
        Type type = new TypeToken<ClockInfo>() {}.getType();
        return new Gson().fromJson(json, type);
    }
}
