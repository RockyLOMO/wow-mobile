package com.winlator.wowmobile;

import android.app.*;
import android.content.*;
import android.os.*;
import androidx.preference.PreferenceManager;
import com.winlator.R;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/** One foreground worker owns download/extraction; activities only render snapshots. */
public final class ClientInstallService extends Service {
    public static final String PAUSE = "oldDream.pause";
    private static final String CHANNEL = "oldDream.client";
    private static final int NOTIFICATION = 335;
    public static final class Snapshot {
        public final String phase, detail;
        public final long done, total, bytesPerSecond;
        public final boolean running;
        Snapshot(String phase, String detail, long done, long total, long speed, boolean running) {
            this.phase=phase; this.detail=detail; this.done=done; this.total=total; bytesPerSecond=speed; this.running=running;
        }
        public int percent() { return total <= 0 ? 0 : (int)Math.min(100, done * 100.0 / total); }
    }
    public static volatile Snapshot snapshot = new Snapshot("idle", "", 0, 0, 0, false);
    private final AtomicBoolean paused = new AtomicBoolean();
    private static final AtomicBoolean ACTIVE = new AtomicBoolean();
    private boolean working;
    private int latestStartId;
    private PowerManager.WakeLock wakeLock;
    private String speedPhase = "";
    private long speedStart, speedBytes;

    public static File base() { return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "OldDream"); }
    public static File destination() { return new File(base(), "client"); }
    public static File cache() { return new File(base(), ".install"); }

    @Override public void onCreate() {
        super.onCreate();
        NotificationChannel channel = new NotificationChannel(CHANNEL, "旧梦WOW 客户端安装", NotificationManager.IMPORTANCE_LOW);
        channel.setSound(null, null);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        latestStartId = startId;
        if (intent != null && PAUSE.equals(intent.getAction())) {
            paused.set(true);
            Snapshot old = snapshot;
            snapshot = new Snapshot(old.phase, "正在暂停，保留已下载文件…", old.done, old.total, old.bytesPerSecond, working);
            if (!working) stopSelf();
            return START_NOT_STICKY;
        }
        if (working) return START_NOT_STICKY;
        if (!ACTIVE.compareAndSet(false, true)) {
            startForeground(NOTIFICATION, notification(snapshot));
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }
        working = true; paused.set(false);
        snapshot = new Snapshot("prepare", "正在连接下载服务器…", 0, 0, 0, true);
        startForeground(NOTIFICATION, notification(snapshot));
        wakeLock = ((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "oldDream:install");
        wakeLock.acquire();
        new Thread(() -> {
            Snapshot terminal;
            try {
                ClientInstaller.install(ClientInstaller.URL, cache(), destination(), paused, this::progress, ClientInstaller::isClientReady);
                PreferenceManager.getDefaultSharedPreferences(this).edit()
                    .putString(WowMobileActivity.PREF_GAME_FOLDER, destination().getPath()).commit();
                terminal = new Snapshot("complete", "客户端已就绪", 1, 1, 0, false);
            } catch (ClientInstaller.Paused e) {
                Snapshot old = snapshot;
                terminal = new Snapshot("paused", "已暂停，点击继续；解压阶段将重新开始，下载文件会保留。", old.done, old.total, 0, false);
            } catch (Exception e) {
                Snapshot old = snapshot;
                terminal = new Snapshot("error", e.getMessage() == null ? "安装失败，请重试。" : e.getMessage(), old.done, old.total, 0, false);
            }
            final Snapshot finished = terminal;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
                stopForeground(true);
                working = false;
                snapshot = finished;
                ACTIVE.set(false);
                stopSelfResult(latestStartId);
            });
        }, "oldDream-client-install").start();
        return START_NOT_STICKY;
    }
    private void progress(String phase, long done, long total, String detail) {
        long now = SystemClock.elapsedRealtime();
        if (!phase.equals(speedPhase)) { speedPhase=phase; speedStart=now; speedBytes=done; }
        long speed = now > speedStart ? (long)((done-speedBytes)*1000.0/(now-speedStart)) : 0;
        snapshot = new Snapshot(phase, detail, done, total, speed, true);
        getSystemService(NotificationManager.class).notify(NOTIFICATION, notification(snapshot));
    }
    private Notification notification(Snapshot state) {
        Intent open = new Intent(this, WowMobileActivity.class).putExtra("skip_auto_launch", true);
        PendingIntent content = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent pause = PendingIntent.getService(this, 1, new Intent(this, ClientInstallService.class).setAction(PAUSE), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("旧梦WOW · " + ("extract".equals(state.phase) ? "解压客户端" : "下载客户端"))
            .setContentText(state.total > 0 ? state.percent() + "% · " + state.detail : state.detail)
            .setContentIntent(content).setOnlyAlertOnce(true).setOngoing(true)
            .setProgress(100, state.percent(), state.total <= 0)
            .addAction(new Notification.Action.Builder(null, "暂停", pause).build()).build();
    }
    @Override public void onDestroy() { paused.set(true); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
