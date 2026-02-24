package com.avatarmind.floatingclock.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * NTP 联网对时工具
 * 通过 NTP 服务器获取网络时间，计算本地时钟偏差，然后在显示时补偿该偏差
 */
public class NtpSyncUtil {

    // 常用 NTP 服务器列表（国内优先）
    private static final String[] NTP_SERVERS = {
        "ntp.aliyun.com",
        "ntp.ntsc.ac.cn",
        "cn.pool.ntp.org",
        "time.windows.com",
        "pool.ntp.org"
    };

    private static final int TIMEOUT_MS = 5000;
    // 每隔 30 分钟自动同步一次
    private static final long SYNC_INTERVAL_MS = 30 * 60 * 1000L;

    /** 本地时钟与 NTP 时间的偏差（毫秒），正值表示本地时钟偏快 */
    private static volatile long sOffsetMs = 0;
    /** 上次成功同步的时间戳 */
    private static volatile long sLastSyncTime = 0;
    /** 是否曾经同步成功 */
    private static volatile boolean sSynced = false;

    private static final ExecutorService sExecutor = Executors.newSingleThreadExecutor();
    private static final Handler sMainHandler = new Handler(Looper.getMainLooper());

    public interface SyncCallback {
        void onSyncSuccess(long offsetMs);
        void onSyncFailed(String reason);
    }

    /**
     * 获取经过 NTP 校正后的当前时间（毫秒）
     */
    public static long getCorrectedTimeMillis() {
        return System.currentTimeMillis() - sOffsetMs;
    }

    /**
     * 是否已完成过至少一次同步
     */
    public static boolean isSynced() {
        return sSynced;
    }

    /**
     * 触发一次 NTP 同步（异步执行，不阻塞 UI）
     */
    public static void sync(Context context, SyncCallback callback) {
        sExecutor.execute(() -> {
            for (String server : NTP_SERVERS) {
                NTPUDPClient client = new NTPUDPClient();
                client.setDefaultTimeout(TIMEOUT_MS);
                try {
                    client.open();
                    InetAddress address = InetAddress.getByName(server);
                    TimeInfo timeInfo = client.getTime(address);
                    timeInfo.computeDetails();

                    long offset = timeInfo.getOffset(); // 本地时钟比 NTP 快的毫秒数
                    sOffsetMs = offset;
                    sLastSyncTime = System.currentTimeMillis();
                    sSynced = true;

                    if (callback != null) {
                        sMainHandler.post(() -> callback.onSyncSuccess(offset));
                    }
                    return; // 同步成功，退出
                } catch (Exception e) {
                    LogUtil.e("NTP sync failed for server: " + server + " - " + e.getMessage());
                } finally {
                    if (client.isOpen()) client.close();
                }
            }
            // 所有服务器均失败
            if (callback != null) {
                sMainHandler.post(() -> callback.onSyncFailed("All NTP servers unreachable"));
            }
        });
    }

    /**
     * 如果距离上次同步超过间隔，则自动触发静默同步
     */
    public static void syncIfNeeded(Context context) {
        long now = System.currentTimeMillis();
        if (!sSynced || (now - sLastSyncTime) > SYNC_INTERVAL_MS) {
            sync(context, null);
        }
    }
}
