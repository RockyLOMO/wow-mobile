package com.winlator.wowmobile;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.winlator.R;
import com.winlator.core.AppUtils;
import com.winlator.core.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;

/**
 * Edits WoW's own configuration (Config.wtf + realmlist.wtf) while the game is off.
 */
public class WowSettingsActivity extends AppCompatActivity {
    private static final String[] RESOLUTIONS = {"1280x720", "1280x1024", "1520x720", "1600x720", "1920x1080"};
    private static final String[] FARCLIP_LABELS = {"Near (fastest)", "Medium", "Far (slower)"};
    private static final String[] FARCLIP_VALUES = {"400", "727", "1000"};

    private GameFolder gameFolder;
    private Provisioner provisioner;
    private Spinner sRealmlist;
    private EditText etCustomRealmlist;
    private Spinner sResolution;
    private Spinner sFarclip;
    private Spinner sTouchMode;
    private Spinner sLongPress;
    private Spinner sCameraSensitivity;
    private ArrayList<String> realmlistItems;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppUtils.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.wow_settings_activity);
        setSupportActionBar(findViewById(R.id.Toolbar));
        getSupportActionBar().setTitle(R.string.wow_settings);

        String path = PreferenceManager.getDefaultSharedPreferences(this).getString(WowMobileActivity.PREF_GAME_FOLDER, null);
        if (path == null || !(gameFolder = new GameFolder(path)).isValid()) {
            AppUtils.showToast(this, R.string.wow_status_select_folder);
            finish();
            return;
        }
        provisioner = new Provisioner(this, gameFolder);

        sRealmlist = findViewById(R.id.SRealmlist);
        etCustomRealmlist = findViewById(R.id.ETCustomRealmlist);
        sResolution = findViewById(R.id.SResolution);
        sFarclip = findViewById(R.id.SFarclip);
        sTouchMode = findViewById(R.id.STouchMode);
        sLongPress = findViewById(R.id.SLongPress);
        sCameraSensitivity = findViewById(R.id.SCameraSensitivity);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        sTouchMode.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
            new String[]{getString(R.string.wow_touch_native), getString(R.string.wow_touch_legacy)}));
        sTouchMode.setSelection(prefs.getBoolean(WowContainerHelper.PREF_NATIVE_TOUCH, true) ? 0 : 1);
        sLongPress.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
            new String[]{"300 ms", "380 ms", "450 ms", "550 ms"}));
        int delay = prefs.getInt(WowContainerHelper.PREF_LONG_PRESS_MS, 380);
        sLongPress.setSelection(delay == 300 ? 0 : delay == 450 ? 2 : delay == 550 ? 3 : 1);
        sCameraSensitivity.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
            new String[]{"0.7x", "1.0x", "1.3x", "1.6x"}));
        float sensitivity = prefs.getFloat(WowContainerHelper.PREF_CAMERA_SENSITIVITY, 1f);
        sCameraSensitivity.setSelection(sensitivity == 0.7f ? 0 : sensitivity == 1.3f ? 2 : sensitivity == 1.6f ? 3 : 1);

        TextView tvLocale = findViewById(R.id.TVLocale);
        String locale = gameFolder.getLocale();
        tvLocale.setText(getString(R.string.wow_detected_locale)+": "+(locale != null ? locale : "?"));

        loadRealmlists();
        loadGraphics();

        findViewById(R.id.BTSave).setOnClickListener((v) -> save());
    }

    /** Active realm + any commented alternatives kept in realmlist.wtf + our default. */
    private void loadRealmlists() {
        LinkedHashSet<String> items = new LinkedHashSet<>();
        String active = provisioner.getActiveRealmlist();
        items.add(active);

        File file = gameFolder.getRealmlistFile();
        if (file != null && file.isFile()) {
            for (String line : FileUtils.readString(file).split("\n")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("#")) {
                    String uncommented = trimmed.replaceFirst("^#+ *", "");
                    if (uncommented.toLowerCase().startsWith("set realmlist")) {
                        String host = uncommented.substring("set realmlist".length()).trim();
                        if (!host.isEmpty()) items.add(host);
                    }
                }
            }
        }
        items.add(Provisioner.DEFAULT_REALMLIST);

        realmlistItems = new ArrayList<>(items);
        realmlistItems.add(getString(R.string.wow_custom_realmlist));
        sRealmlist.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, realmlistItems));
        sRealmlist.setSelection(realmlistItems.indexOf(active));

        sRealmlist.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                boolean custom = position == realmlistItems.size()-1;
                etCustomRealmlist.setVisibility(custom ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        etCustomRealmlist.setVisibility(View.GONE);
    }

    private void loadGraphics() {
        String resolution = WowResolution.normalize(provisioner.getConfigValue("gxResolution"));
        ArrayList<String> resolutions = new ArrayList<>(java.util.Arrays.asList(RESOLUTIONS));
        if (!resolutions.contains(resolution)) resolutions.add(resolution);
        sResolution.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, resolutions));
        sResolution.setSelection(resolutions.indexOf(resolution));

        sFarclip.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, FARCLIP_LABELS));
        String farclip = provisioner.getConfigValue("farclip");
        int farclipIndex = 1;
        for (int i = 0; i < FARCLIP_VALUES.length; i++) if (FARCLIP_VALUES[i].equals(farclip)) farclipIndex = i;
        sFarclip.setSelection(farclipIndex);
    }

    private void save() {
        String host;
        if (sRealmlist.getSelectedItemPosition() == realmlistItems.size()-1) {
            host = etCustomRealmlist.getText().toString().trim();
            if (host.isEmpty()) {
                AppUtils.showToast(this, R.string.wow_custom_realmlist);
                return;
            }
        }
        else host = realmlistItems.get(sRealmlist.getSelectedItemPosition());

        provisioner.ensureRealmlist(host);
        String resolution = (String)sResolution.getSelectedItem();
        provisioner.setConfigValue("gxResolution", resolution);
        WowContainerHelper helper = new WowContainerHelper(this);
        helper.syncScreenSize(helper.getContainer(), resolution);
        provisioner.setConfigValue("farclip", FARCLIP_VALUES[sFarclip.getSelectedItemPosition()]);
        int[] delays = {300, 380, 450, 550};
        float[] sensitivities = {0.7f, 1f, 1.3f, 1.6f};
        PreferenceManager.getDefaultSharedPreferences(this).edit()
            .putBoolean(WowContainerHelper.PREF_NATIVE_TOUCH, sTouchMode.getSelectedItemPosition() == 0)
            .putInt(WowContainerHelper.PREF_LONG_PRESS_MS, delays[sLongPress.getSelectedItemPosition()])
            .putFloat(WowContainerHelper.PREF_CAMERA_SENSITIVITY, sensitivities[sCameraSensitivity.getSelectedItemPosition()])
            .apply();

        AppUtils.showToast(this, R.string.wow_settings_saved);
        finish();
    }
}
