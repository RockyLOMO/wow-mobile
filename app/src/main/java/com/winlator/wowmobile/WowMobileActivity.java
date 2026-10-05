package com.winlator.wowmobile;

import android.Manifest;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import com.winlator.MainActivity;
import com.winlator.R;
import com.winlator.core.AppUtils;
import com.winlator.xenvironment.RootFS;
import com.winlator.xenvironment.RootFSInstaller;
import java.io.File;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.Executors;

/** Client bootstrap; existing installations launch once per cold app opening. */
public class WowMobileActivity extends AppCompatActivity {
    public static final String PREF_GAME_FOLDER = "wow_game_folder";
    private SharedPreferences preferences;
    private TextView status, detail, folder;
    private ProgressBar progress;
    private Button play, download, select, detect, settings, containerSettings;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean autoConsumed, launching, resumed;
    private int launchToken;
    private String launchError;
    private static boolean rootInstallStarted;
    private long rootWaitStart;
    private final Runnable ticker = new Runnable() {
        @Override public void run() { updateStatus(); if (resumed) handler.postDelayed(this, 500); }
    };
    @Override protected void onCreate(Bundle state) {
        AppUtils.setActivityTheme(this);
        super.onCreate(state);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        autoConsumed = state != null && state.getBoolean("autoConsumed");
        if (getIntent().getBooleanExtra("skip_auto_launch", false)) autoConsumed = true;
        if (getIntent().getBooleanExtra("resume_install", false)) autoConsumed = false;
        createView();
        requestPermissionsIfNeeded();
    }
    private void createView() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.setBackgroundColor(0xff081117);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL); panel.setGravity(Gravity.CENTER_HORIZONTAL);
        boolean landscape = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        int pad = (int)((landscape ? 12 : 24) * getResources().getDisplayMetrics().density);
        panel.setPadding(pad,pad,pad,pad);
        scroll.addView(panel);
        ImageView icon = new ImageView(this); icon.setImageResource(R.drawable.olddream_icon);
        panel.addView(icon,new LinearLayout.LayoutParams(pad*4,pad*4));
        text(panel, "旧梦WOW", 28, 0xffc9a66d);
        status = text(panel, "正在检查客户端…", 20, 0xffeeeeee);
        folder = text(panel, "", 14, 0xff99b6c4);
        detail = text(panel, "", 15, 0xffc9d4dc);
        progress = new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100); panel.addView(progress,new LinearLayout.LayoutParams(-1,pad));
        play = button(panel,"进入游戏", () -> { launchError=null; onPlay(); });
        download = button(panel,"下载完整客户端", this::downloadOrPause);
        detect = button(panel,"已手动复制，重新检测", () -> { launchError=null; autoConsumed=false; updateStatus(); });
        select = button(panel,"选择现有客户端目录", this::showFolderPicker);
        settings=button(panel,"游戏设置", () -> openSettings(WowSettingsActivity.class));
        containerSettings=button(panel,"容器设置", () -> openSettings(ContainerSettingsActivity.class));
        button(panel,"关闭应用", this::finishAndRemoveTask);
        icon.setOnLongClickListener(v -> { openSettings(MainActivity.class); return true; });
        setContentView(scroll);
    }
    private TextView text(LinearLayout panel, String value, int size, int color) {
        TextView view=new TextView(this); view.setText(value); view.setTextSize(size);
        view.setTextColor(color); view.setGravity(Gravity.CENTER); view.setPadding(0,8,0,8);
        panel.addView(view,new LinearLayout.LayoutParams(-1,-2)); return view;
    }
    private Button button(LinearLayout panel, String value, Runnable action) {
        Button button=new Button(this); button.setText(value); button.setTextSize(16);
        panel.addView(button,new LinearLayout.LayoutParams(-1,-2)); button.setOnClickListener(v -> action.run()); return button;
    }
    private void openSettings(Class<?> activity) {
        autoConsumed=true; launchToken++; launching=false;
        startActivity(new Intent(this,activity));
    }
    private boolean permissionGranted() {
        return ContextCompat.checkSelfPermission(this,Manifest.permission.WRITE_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED
            && ContextCompat.checkSelfPermission(this,Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;
    }
    private void requestPermissionsIfNeeded() {
        if (!permissionGranted()) ActivityCompat.requestPermissions(this,
            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.READ_EXTERNAL_STORAGE},10);
    }
    @Override public void onRequestPermissionsResult(int request,@NonNull String[] names,@NonNull int[] results) {
        super.onRequestPermissionsResult(request,names,results); updateStatus();
    }
    @Override protected void onSaveInstanceState(@NonNull Bundle state) {
        state.putBoolean("autoConsumed",autoConsumed); super.onSaveInstanceState(state);
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        if (intent.getBooleanExtra("skip_auto_launch",false)) { autoConsumed=true; launching=false; }
        if (intent.getBooleanExtra("resume_install",false)) autoConsumed=false;
        updateStatus();
    }
    @Override protected void onResume() { super.onResume(); resumed=true; handler.post(ticker); }
    @Override protected void onPause() { resumed=false; launchToken++; launching=false; handler.removeCallbacks(ticker); super.onPause(); }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }
    public GameFolder getGameFolder() {
        String saved=preferences.getString(PREF_GAME_FOLDER,null);
        File[] candidates = {saved==null ? ClientInstallService.destination() : new File(saved),
            ClientInstallService.destination(),new File(Environment.getExternalStorageDirectory(),"WoW335CN")};
        for (File candidate:candidates) if (ClientInstaller.isClientReady(candidate)) {
            if (!candidate.getPath().equals(saved)) preferences.edit().putString(PREF_GAME_FOLDER,candidate.getPath()).apply();
            return new GameFolder(candidate);
        }
        return null;
    }
    private void updateStatus() {
        if (!permissionGranted()) {
            status.setText("需要存储权限以读取客户端"); detail.setText("请允许存储权限后重新检测。");
            play.setEnabled(false); download.setEnabled(false); detect.setOnClickListener(v -> requestPermissionsIfNeeded()); return;
        }
        detect.setOnClickListener(v -> { launchError=null; autoConsumed=false; updateStatus(); });
        GameFolder game=getGameFolder();
        ClientInstallService.Snapshot state=ClientInstallService.snapshot;
        boolean ready=game!=null;
        folder.setText(ready ? game.root.getPath() : "客户端目录："+ClientInstallService.destination().getPath());
        folder.setOnLongClickListener(v -> { showFolderPicker(); return true; });
        select.setVisibility(ready ? View.GONE:View.VISIBLE); detect.setVisibility(ready ? View.GONE:View.VISIBLE);
        settings.setVisibility(ready ? View.VISIBLE:View.GONE); containerSettings.setVisibility(ready ? View.VISIBLE:View.GONE);
        settings.setEnabled(!launching && !state.running); containerSettings.setEnabled(!launching && !state.running);
        play.setVisibility(ready ? View.VISIBLE : View.GONE); play.setEnabled(!launching && !state.running);
        select.setEnabled(!state.running && !launching); detect.setEnabled(!state.running && !launching);
        download.setVisibility(ready && !state.running ? View.GONE : View.VISIBLE); download.setEnabled(!launching);
        progress.setVisibility(state.running || "paused".equals(state.phase) || launching ? View.VISIBLE:View.GONE);
        progress.setIndeterminate(launching || state.running && state.total<=0); progress.setProgress(state.percent());
        if (launching) { status.setText("正在进入游戏…"); detail.setText("分辨率 "+OldDreamIntegration.resolution(this)); return; }
        if (ready && !state.running) {
            status.setText(launchError==null ? "客户端已就绪" : "启动失败");
            detail.setText(launchError==null ? "分辨率 "+OldDreamIntegration.resolution(this)+" · 退出后可在这里调整设置。" : launchError);
            if (!autoConsumed && resumed && !state.running) { autoConsumed=true; handler.post(this::onPlay); }
            return;
        }
        if (state.running || "paused".equals(state.phase) || "error".equals(state.phase)) {
            status.setText(state.running ? ("extract".equals(state.phase) ? "正在解压客户端" : "正在下载客户端") : "paused".equals(state.phase) ? "安装已暂停" : "安装未完成");
            String rate=state.bytesPerSecond>0 ? String.format(Locale.CHINA," · %.1f MB/s · 约 %d 分钟",state.bytesPerSecond/1e6,(state.total-state.done)/state.bytesPerSecond/60) : "";
            detail.setText(String.format(Locale.CHINA,"%.1f%% · %.2f / %.2f GB%s\n%s",state.total>0 ? state.done*100.0/state.total:0,state.done/1e9,state.total/1e9,rate,state.detail));
            download.setText(state.running ? "暂停安装" : "继续安装");
        } else {
            status.setText("尚未找到完整客户端");
            long saved=new File(ClientInstallService.cache(),"client.zip.part").length();
            boolean archiveSaved=new File(ClientInstallService.cache(),"client.zip").isFile();
            detail.setText("复制完整 3.3.5a 简体中文客户端到上述目录，或下载并自动解压。\n下载约20 GB，建议预留45 GB空间。已有文件不会被覆盖。"+(archiveSaved ? "\n完整压缩包已保留，点击继续解压。" : saved>0 ? String.format(Locale.CHINA,"\n已保留 %.2f GB 下载，点击继续安装。",saved/1e9):""));
            download.setText(saved>0 || archiveSaved ? "继续安装":"下载完整客户端");
        }
    }
    private void downloadOrPause() {
        Intent intent=new Intent(this,ClientInstallService.class);
        if (ClientInstallService.snapshot.running) { intent.setAction(ClientInstallService.PAUSE); startService(intent); }
        else { autoConsumed=false; ContextCompat.startForegroundService(this,intent); }
        updateStatus();
    }
    private void onPlay() {
        if (launching || !resumed || !permissionGranted()) return;
        GameFolder game=getGameFolder(); if(game==null) { updateStatus(); return; }
        autoConsumed=true; launching=true; rootWaitStart=SystemClock.elapsedRealtime(); updateStatus();
        awaitRuntime(game,++launchToken);
    }
    private boolean validLaunch(int token) { return token==launchToken && launching && resumed && !isFinishing() && !isDestroyed(); }
    private void awaitRuntime(GameFolder game,int token) {
        if (!validLaunch(token)) return;
        RootFS runtime=RootFS.find(this);
        if (!runtime.isValid() || runtime.getVersion()<RootFSInstaller.LATEST_VERSION) {
            if (SystemClock.elapsedRealtime()-rootWaitStart>10*60*1000) { failed("运行环境准备较慢，请稍后重新进入游戏。"); return; }
            if (!rootInstallStarted) {
                rootInstallStarted=true;
                RootFSInstaller.install(this,success -> {
                    rootInstallStarted=false;
                    if (!success && validLaunch(token)) failed("运行环境准备失败，请重新进入游戏。");
                });
            }
            handler.postDelayed(() -> awaitRuntime(game,token),500); return;
        }
        rootInstallStarted=false;
        java.util.concurrent.ExecutorService worker=Executors.newSingleThreadExecutor();
        worker.execute(() -> {
            boolean ok=new Provisioner(this,game).provision();
            runOnUiThread(() -> {
                if (!validLaunch(token)) return;
                if(!ok) { failed("无法配置客户端，请检查目录权限。"); return; }
                WowContainerHelper helper=new WowContainerHelper(this);
                helper.ensureContainerAsync(game,container -> {
                    if(!validLaunch(token)) return;
                    if(container==null) { failed("无法准备游戏容器，请重试。"); return; }
                    File shortcut=helper.ensureShortcut(container,game);
                    launching=false; startActivity(helper.createLaunchIntent(container,shortcut));
                });
            });
            worker.shutdown();
        });
    }
    private void failed(String message) { launching=false; launchError=message; updateStatus(); }
    private void showFolderPicker() {
        GameFolder current = getGameFolder();
        File startDir = current != null && current.root.isDirectory() ?
            current.root : Environment.getExternalStorageDirectory();
        showFolderPickerAt(startDir);
    }

    private void showFolderPickerAt(final File dir) {
        final ArrayList<File> entries = new ArrayList<>();
        final ArrayList<String> names = new ArrayList<>();

        File parent = dir.getParentFile();
        boolean hasParent = parent != null && !dir.equals(Environment.getExternalStorageDirectory()) && parent.canRead();
        if (hasParent) {
            entries.add(parent);
            names.add("..");
        }

        File[] files = dir.listFiles();
        if (files != null) {
            ArrayList<File> dirs = new ArrayList<>();
            for (File file : files) if (file.isDirectory() && !file.getName().startsWith(".")) dirs.add(file);
            dirs.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            for (File file : dirs) {
                entries.add(file);
                names.add(file.getName());
            }
        }

        GameFolder candidate = new GameFolder(dir);
        String title = dir.getPath().replace(Environment.getExternalStorageDirectory().getPath(), getString(R.string.internal_storage));
        if (ClientInstaller.isClientReady(candidate.root)) title += " ✓";

        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        builder.setTitle(title);

        ListView listView = new ListView(this);
        listView.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names));
        builder.setView(listView);

        if (ClientInstaller.isClientReady(candidate.root)) {
            builder.setPositiveButton(R.string.wow_use_this_folder, (d, w) -> {
                preferences.edit().putString(PREF_GAME_FOLDER, dir.getPath()).apply();
                autoConsumed=false; launchError=null; updateStatus();
            });
        }
        builder.setNegativeButton(R.string.cancel, (d, w) -> {});

        final AlertDialog dialog = builder.create();
        listView.setOnItemClickListener((adapterView, view, position, id) -> {
            dialog.dismiss();
            showFolderPickerAt(entries.get(position));
        });
        dialog.show();
    }
}
