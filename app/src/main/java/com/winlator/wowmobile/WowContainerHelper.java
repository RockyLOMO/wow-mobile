package com.winlator.wowmobile;

import android.content.Context;
import android.content.Intent;

import androidx.preference.PreferenceManager;

import com.winlator.XServerDisplayActivity;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.container.GraphicsDrivers;
import com.winlator.core.AppUtils;
import com.winlator.core.Callback;
import com.winlator.core.FileUtils;
import com.winlator.core.StringUtils;
import com.winlator.core.WineUtils;
import com.winlator.inputcontrols.ControlsProfile;
import com.winlator.inputcontrols.InputControlsManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

/**
 * Manages the single Winlator container used to run WoW, plus the launch shortcut
 * that ties the container, the game exe and the touch controls profile together.
 */
public class WowContainerHelper {
    public static final String CONTAINER_NAME = "WoW";
    public static final String SHORTCUT_NAME = "World of Warcraft";
    public static final String CONTROLS_PROFILE_NAME = "WoW ConsolePortLK";
    public static final String NATIVE_PROFILE_NAME = "WoW Native Touch";
    public static final String PREF_NATIVE_TOUCH = "wow_native_touch";
    public static final String PREF_LONG_PRESS_MS = "wow_long_press_ms";
    public static final String PREF_CAMERA_SENSITIVITY = "wow_camera_sensitivity";
    public static final String GAME_DRIVE_LETTER = "F";
    public static final String DEFAULT_SCREEN_SIZE = WowResolution.DEFAULT;

    private final Context context;
    private final ContainerManager manager;

    public WowContainerHelper(Context context) {
        this.context = context;
        this.manager = new ContainerManager(context);
    }

    public Container getContainer() {
        for (Container container : manager.getContainers()) {
            if (CONTAINER_NAME.equals(container.getName())) return container;
        }
        return null;
    }

    /** Creates the WoW container with tuned defaults if it does not exist yet. */
    public void ensureContainerAsync(GameFolder gameFolder, Callback<Container> callback) {
        Container existing = getContainer();
        String screenSize = OldDreamIntegration.resolution(context);
        if (existing != null) {
            syncScreenSize(existing, screenSize);
            ensureGameDrive(existing, gameFolder);
            callback.call(existing);
            return;
        }

        try {
            JSONObject data = new JSONObject();
            data.put("name", CONTAINER_NAME);
            data.put("screenSize", screenSize);
            data.put("envVars", Container.DEFAULT_ENV_VARS);
            data.put("graphicsDriver", GraphicsDrivers.getDefaultDriver(context));
            data.put("dxwrapper", Container.DEFAULT_DXWRAPPER);
            data.put("audioDriver", Container.DEFAULT_AUDIO_DRIVER);
            data.put("wincomponents", Container.DEFAULT_WINCOMPONENTS);
            data.put("drives", Container.DEFAULT_DRIVES + GAME_DRIVE_LETTER+":"+gameFolder.root.getPath());
            data.put("startupSelection", Container.STARTUP_SELECTION_AGGRESSIVE);
            manager.createContainerAsync(data, callback);
        }
        catch (JSONException e) {
            callback.call(null);
        }
    }

    /** Keeps the Wine desktop and WoW render resolution identical. */
    public void syncScreenSize(Container container, String screenSize) {
        screenSize = WowResolution.normalize(screenSize);
        if (container != null && !screenSize.equals(container.getScreenSize())) {
            container.setScreenSize(screenSize);
            container.saveData();
        }
    }

    /** Keeps the F: drive pointing at the currently selected game folder. */
    private void ensureGameDrive(Container container, GameFolder gameFolder) {
        String drives = container.getDrives();
        String gameDrive = GAME_DRIVE_LETTER+":"+gameFolder.root.getPath();

        StringBuilder sb = new StringBuilder();
        boolean found = false;
        for (com.winlator.container.Drive drive : Container.drivesIterator(drives)) {
            if (drive.letter.equals(GAME_DRIVE_LETTER)) {
                sb.append(gameDrive);
                found = true;
            }
            else sb.append(drive.letter).append(":").append(drive.path);
        }
        if (!found) sb.append(gameDrive);

        String newDrives = sb.toString();
        if (!newDrives.equals(drives)) {
            container.setDrives(newDrives);
            container.saveData();
        }
    }

    /** Writes the launch shortcut binding exe + controls profile, then returns it. */
    public File ensureShortcut(Container container, GameFolder gameFolder) {
        File desktopDir = new File(container.getUserDir(), "Desktop");
        if (!desktopDir.isDirectory()) desktopDir.mkdirs();

        File shortcutFile = new File(desktopDir, SHORTCUT_NAME+".desktop");
        String dosPath = WineUtils.unixToDOSPath(gameFolder.getWowExe().getPath(), container);

        // Shortcut.unescapeDOSPath applies its unescape pass twice, so the stored
        // Exec path needs two rounds of escaping to survive parsing.
        String content = "[Desktop Entry]\n" +
            "Name="+SHORTCUT_NAME+"\n" +
            "Exec=wine "+StringUtils.escapeDOSPath(StringUtils.escapeDOSPath(dosPath))+"\n" +
            "StartupWMClass=wow.exe\n" +
            "\n[Extra Data]\n";

        int profileId = getControlsProfileId();
        if (profileId > 0) content += "controlsProfile="+profileId+"\n";
        // Keep the same aspect ratio on every device rather than stretching the game.
        content += "preserveAspectRatio=1\n";
        content += "oldDream=1\n";

        FileUtils.writeString(shortcutFile, content);
        return shortcutFile;
    }

    private int getControlsProfileId() {
        InputControlsManager inputControlsManager = new InputControlsManager(context);
        boolean nativeTouch = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(PREF_NATIVE_TOUCH, true);
        String profileName = nativeTouch ? NATIVE_PROFILE_NAME : CONTROLS_PROFILE_NAME;
        for (ControlsProfile profile : inputControlsManager.getProfiles()) {
            if (profileName.equals(profile.getName())) {
                if (nativeTouch) localizeNativeLabels(profile.id);
                return profile.id;
            }
        }
        if (nativeTouch) {
            try {
                JSONObject data = new JSONObject(FileUtils.readString(context, "inputcontrols/profiles/controls-6.icp"));
                ControlsProfile profile = inputControlsManager.importProfile(data);
                if (profile != null) return profile.id;
            }
            catch (JSONException ignored) {}
        }
        return 0;
    }

    /** Migrate bundled positions and labels, preserving custom positions and names. */
    private void localizeNativeLabels(int id) {
        File file = ControlsProfile.getProfileFile(context, id);
        try {
            JSONObject data = new JSONObject(FileUtils.readString(file));
            org.json.JSONArray elements = data.getJSONArray("elements");
            boolean changed = false;
            for (int i = 0; i < elements.length(); i++) {
                JSONObject element = elements.getJSONObject(i);
                String binding = element.getJSONArray("bindings").optString(0);
                String label = element.optString("text");
                double x = element.optDouble("x");
                if ("D_PAD".equals(element.optString("type")) && Math.abs(x - 0.09) < 0.0001) {
                    android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
                    ((android.view.WindowManager)context.getSystemService(Context.WINDOW_SERVICE))
                        .getDefaultDisplay().getRealMetrics(metrics);
                    int width = Math.max(metrics.widthPixels, metrics.heightPixels);
                    int snap = width / 100;
                    int maxWidth = (width / snap) * snap;
                    int center = (int)Math.ceil((int)(snap * 7 * element.optDouble("scale", 1.4)) + snap * 0.125);
                    element.put("x", center / (double)maxWidth);
                    changed = true;
                }
                if (("KEY_SPACE".equals(binding) || "KEY_TAB".equals(binding)) &&
                    (Math.abs(x - 0.94) < 0.0001 || Math.abs(x - 0.81) < 0.0001)) {
                    // Right edges sit about two pixels from the enlarged four-column block on S10.
                    element.put("x", "KEY_SPACE".equals(binding) ? 1272.0 / 1515 : 1258.0 / 1515);
                    double y = element.optDouble("y");
                    if ("KEY_SPACE".equals(binding) && Math.abs(y - 0.76) < 0.0001) element.put("y", 482.0 / 720);
                    if ("KEY_TAB".equals(binding) && Math.abs(y - 0.59) < 0.0001) element.put("y", 0.52);
                    changed = true;
                }
                if ("KEY_SPACE".equals(binding) && "JUMP".equals(label)) { element.put("text", "跳跃"); changed = true; }
                if ("KEY_TAB".equals(binding) && "TGT".equals(label)) { element.put("text", "目标"); changed = true; }
            }
            if (changed) FileUtils.writeString(file, data.toString());
        } catch (JSONException ignored) {}
    }

    public Intent createLaunchIntent(Container container, File shortcutFile) {
        Intent intent = new Intent(context, XServerDisplayActivity.class);
        intent.putExtra("container_id", container.id);
        intent.putExtra("shortcut_path", shortcutFile.getPath());
        return intent;
    }
}
