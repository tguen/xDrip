package com.eveningoutpost.dexdrip.utils;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.*;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.bytehamster.lib.preferencesearch.SearchConfiguration;
import com.bytehamster.lib.preferencesearch.SearchPreferenceResult;
import com.bytehamster.lib.preferencesearch.SearchPreferenceResultListener;
import com.eveningoutpost.dexdrip.BasePreferenceActivity;
import com.eveningoutpost.dexdrip.R;
import com.eveningoutpost.dexdrip.alert.Registry;
import com.eveningoutpost.dexdrip.calibrations.PluggableCalibration;
import com.eveningoutpost.dexdrip.cgm.dex.TxIdHelper;
import com.eveningoutpost.dexdrip.models.JoH;
import com.eveningoutpost.dexdrip.models.Profile;
import com.eveningoutpost.dexdrip.models.UserError;
import com.eveningoutpost.dexdrip.models.UserError.ExtraLogTags;
import com.eveningoutpost.dexdrip.models.UserError.Log;
import com.eveningoutpost.dexdrip.models.UserNotification;
import com.eveningoutpost.dexdrip.profileeditor.ProfileEditor;
import com.eveningoutpost.dexdrip.receiver.InfoContentProvider;
import com.eveningoutpost.dexdrip.services.G5BaseService;
import com.eveningoutpost.dexdrip.services.UiBasedCollector;
import com.eveningoutpost.dexdrip.services.broadcastservice.BroadcastService;
import com.eveningoutpost.dexdrip.ui.dialog.GenericConfirmDialog;
import com.eveningoutpost.dexdrip.utilitymodels.BgGraphBuilder;
import com.eveningoutpost.dexdrip.utilitymodels.CollectionServiceStarter;
import com.eveningoutpost.dexdrip.utilitymodels.Constants;
import com.eveningoutpost.dexdrip.utilitymodels.Experience;
import com.eveningoutpost.dexdrip.utilitymodels.Inevitable;
import com.eveningoutpost.dexdrip.utilitymodels.Intents;
import com.eveningoutpost.dexdrip.utilitymodels.Pref;
import com.eveningoutpost.dexdrip.xdrip;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.val;

import static com.eveningoutpost.dexdrip.EditAlertActivity.unitsConvert2Disp;
import static com.eveningoutpost.dexdrip.models.JoH.showNotification;
import static com.eveningoutpost.dexdrip.models.JoH.tolerantParseDouble;
import static com.eveningoutpost.dexdrip.services.Ob1G5CollectionService.clearDataWhenTransmitterIdEntered;
import static com.eveningoutpost.dexdrip.utilitymodels.Constants.OUT_OF_RANGE_GLUCOSE_ENTRY_ID;
import static com.eveningoutpost.dexdrip.utils.DexCollectionType.getBestCollectorHardwareName;
import static com.eveningoutpost.dexdrip.xdrip.gs;


/**
 * A {@link PreferenceActivity} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 * <p/>
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 */
public class Preferences extends BasePreferenceActivity implements SearchPreferenceResultListener {
    private static final String TAG = "jamorham PREFS";
    private static byte[] staticKey;
    private volatile AllPrefsFragment preferenceFragment;

    private static Preference units_pref;
    private static String static_units;
    private static Preference profile_insulin_sensitivity_default;
    private static Preference profile_carb_ratio_default;

    private static AllPrefsFragment pFragment;
    private BroadcastReceiver mibandStatusReceiver;

    // The following three variables enable us to create a common state from the input,
    // whether we scan from camera or a file, and continue with the same following
    // set of commands to avoid code duplication.
    private volatile String scanFormat = null; // The format of the scan
    private volatile String scanContents = null; // Text content of the scan coming either from camera or file
    private volatile byte[] scanRawBytes = null; // Raw bytes of the scan

    private void refreshFragments() {
        refreshFragments(null);
    }
    public static final double MIN_GLUCOSE_INPUT = 40; // The smallest acceptable input glucose value in mg/dL
    public static final double MAX_GLUCOSE_INPUT = 400; // The largest acceptable input glucose value in mg/dL

    private void refreshFragments(final String jumpTo) {
        this.preferenceFragment = new AllPrefsFragment(jumpTo);
        this.preferenceFragment.setParent(this);
        pFragment = this.preferenceFragment;
        getFragmentManager().beginTransaction().replace(android.R.id.content,
                this.preferenceFragment).commit();
    }

    public static List<String> getAllPreferenceKeys(final PreferenceGroup parent) {
        final List<Preference> source = getAllPreferences(parent);
        final List<String> results = new ArrayList<>(source.size());
        for (final Preference preference : source) {
            results.add(preference.getKey());
        }
        return results;
    }

    public static List<Preference> getAllPreferences(final PreferenceGroup parent) {
        final int preferenceCount = parent.getPreferenceCount();
        final List<Preference> results = new ArrayList<>(preferenceCount);
        for (int i = 0; i < preferenceCount; i++) {
            final Preference preference = parent.getPreference(i);
            results.add(preference);
            if (preference instanceof PreferenceGroup) {
                // recurse
                results.addAll(getAllPreferences((PreferenceGroup)preference));
            }
        }
        return results;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onSearchResultClicked(@NonNull SearchPreferenceResult searchPreferenceResult) {
        try {
            searchPreferenceResult.closeSearchPage(this);
            searchPreferenceResult.highlight(this.preferenceFragment, Color.YELLOW);
        } catch (RuntimeException e) {
            Log.wtf(TAG, "Got error trying to highlight search results: " + e);
            JoH.static_toast_long("" + e);
        }
    }


    public interface OnServiceTaskCompleted {
        void onTaskCompleted(byte[] result);
    }


    private void toast(final String msg) {
        try {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
                }
            });
            android.util.Log.d(TAG, "Toast msg: " + msg);
        } catch (Exception e) {
            android.util.Log.e(TAG, "Couldn't display toast: " + msg);
        }
    }

    private void installxDripPlusPreferencesFromQRCode(SharedPreferences prefs, String data) {
        Log.d(TAG, "installing preferences from QRcode");
        try {
            Map<String, String> prefsmap = QRcodeUtils.decodeString(data);
            if (prefsmap != null) {
                if (prefsmap.containsKey(getString(R.string.all_settings_wizard))) {
                    if (prefsmap.containsKey(getString(R.string.wizard_key))
                            && prefsmap.containsKey(getString(R.string.wizard_uuid))) {
//                        staticKey = CipherUtils.hexToBytes(prefsmap.get(getString(R.string.wizard_key)));
//                        new WebAppHelper(new ServiceCallback()).executeOnExecutor(xdrip.executor, getString(R.string.wserviceurl) + "/joh-getsw/" + prefsmap.get(getString(R.string.wizard_uuid)));
                    } else {
                        Log.d(TAG, "Incorrectly formatted wizard pref");
                    }
                    return;
                }

                val sb = getMapKeysString(prefsmap);
                val msg = getString(R.string.import_qr_code_warning) + sb;

                GenericConfirmDialog.show(this, gs(R.string.are_you_sure), msg, () -> {
                    final SharedPreferences.Editor editor = prefs.edit();
                    int changes = 0;
                    for (Map.Entry<String, String> entry : prefsmap.entrySet()) {
                        String key = entry.getKey();
                        String value = entry.getValue();
                        //            Log.d(TAG, "Saving preferences: " + key + " = " + value);
                        if (value.equals("true") || (value.equals("false"))) {
                            editor.putBoolean(key, Boolean.parseBoolean(value));
                            changes++;
                        } else if (!value.equals("null")) {
                            editor.putString(key, value);
                            changes++;
                        }
                    }
                    editor.apply();
                    refreshFragments();
                    ExtraLogTags.readPreference(Pref.getStringDefaultBlank("extra_tags_for_logging"));
                    Toast.makeText(getApplicationContext(), "Loaded " + Integer.toString(changes) + " preferences from QR code", Toast.LENGTH_LONG).show();
                    InfoContentProvider.ping("pref");
                });

            } else {
                android.util.Log.e(TAG, "Got null prefsmap during decode");
            }
        } catch (Exception e) {
            Log.e(TAG, "Got exception installing preferences");
        }

    }

    public static String getMapKeysString(final Map<String, ?> prefsmap) {
        val sb = new StringBuilder();
        val keysSet = prefsmap.keySet();
        val keyList = new ArrayList<>(keysSet);
        Collections.sort(keyList);
        for (val entry : keyList) {
            sb.append(entry);
            sb.append("\n");
        }
        return sb.toString();
    }


    public static Boolean getBooleanPreferenceViaContextWithoutException(Context context, String key, Boolean defaultValue) {
        try {
            return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(key, defaultValue);

        } catch (ClassCastException ex) {
            return defaultValue;
        }
    }


    @Override
    protected synchronized void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Let's reset variables just to be sure
        scanFormat = null;
        scanContents = null;
        scanRawBytes = null;

        if (requestCode == Constants.ZXING_FILE_REQ_CODE) { // If we are scanning an image file, not using the camera
            // The core of the following section, selecting the file, converting it into a bitmap, and then to a bitstream, is from:
            // https://stackoverflow.com/questions/55427308/scaning-qrcode-from-image-not-from-camera-using-zxing
            if (data == null || data.getData() == null) {
                Log.e("TAG", "No file was selected");
                return;
            }
            Uri uri = data.getData();
            try {
                InputStream inputStream = getContentResolver().openInputStream(uri);
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                if (bitmap == null) {
                    Log.e("TAG", "uri is not a bitmap," + uri.toString());
                    return;
                }
                int width = bitmap.getWidth(), height = bitmap.getHeight();
                int[] pixels = new int[width * height];
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
                bitmap.recycle();
                bitmap = null;
                RGBLuminanceSource source = new RGBLuminanceSource(width, height, pixels);
                BinaryBitmap bBitmap = new BinaryBitmap(new HybridBinarizer(source));
                MultiFormatReader reader = new MultiFormatReader();
                try {
                    Result result = reader.decode(bBitmap);
                    scanFormat = result.getBarcodeFormat().toString();
                    scanContents = result.getText(); // The text content  of the scanned file
                    scanRawBytes = result.getRawBytes();
                } catch (NotFoundException e) {
                    Log.e("TAG", "decode exception", e);
                }
            } catch (FileNotFoundException e) {
                Log.e("TAG", "can not open file" + uri.toString(), e);
            }
        } else if (requestCode == Constants.ZXING_CAM_REQ_CODE) { // If we are scanning from camera
            IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
            scanFormat = scanResult.getFormatName();
            scanContents = scanResult.getContents(); // The text content of the scan from camera
            scanRawBytes = scanResult.getRawBytes();
        }
        // We now have scan format, scan text content, and scan raw bytes in the corresponding variables.
        // Everything after this is applied whether we scanned with camera or from a file.

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (scanContents == null) { // If we have no scan content
            UserError.Log.d(TAG, "No scan results ");
            return;
        }

        if (scanFormat.equals("QR_CODE")) { // The scan is a QR code
            if (QRcodeUtils.hasDecoderMarker(scanContents)) {
                installxDripPlusPreferencesFromQRCode(prefs, scanContents);
                return;
            }
        }
        refreshFragments();
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        try {
            setTheme(R.style.OldAppTheme);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set theme");
        }
        super.onCreate(savedInstanceState);

        refreshFragments(getIntent() != null ? getIntent().getAction() : null);
        processExtraData();

        mibandStatusReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
            }
        };

        UiBasedCollector.onEnableCheckPermission(this);
    }

    @Override
    public void onStop() { // Everything here runs when xDrip is minimized or stopped.
        super.onStop();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        try {
            if (!prefs.getBoolean("engineering_mode", false)) { // If engineering mode has been disabled
                try {
                } catch (Exception e) {
                    //
                }
            }
        } catch (Exception e) {
            //
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getMenuInflater().inflate(R.menu.menu_preferences, menu);
        }
        return super.onCreateOptionsMenu(menu);
    }

    public void showSearch(MenuItem item) {
        if (JoH.ratelimit("preference-search-button",1)) {
            this.preferenceFragment.showSearchFragment();
        }
    }


    private final SharedPreferences.OnSharedPreferenceChangeListener uiPrefListener = UiBasedCollector.getListener(this);


    @Override
    protected void onResume()
    {
        super.onResume();
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(BroadcastService.prefListener);
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(uiPrefListener);
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(Registry.prefListener);
        LocalBroadcastManager.getInstance(this).registerReceiver(mibandStatusReceiver,
                new IntentFilter(Intents.PREFERENCE_INTENT));
    }

    @Override
    protected void onPause()
    {
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(BroadcastService.prefListener);
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(uiPrefListener);
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(Registry.prefListener);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mibandStatusReceiver);
        pFragment = null;
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        try {
//            PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(preferenceFragment.lockListener.prefListener);
        } catch (Exception e) {
            //
        }
        super.onDestroy();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
//        addPreferencesFromResource(R.xml.pref_general);

    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        if (AllPrefsFragment.class.getName().equals(fragmentName)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean onIsMultiPane() {
        return isXLargeTablet(this);
    }

    private static boolean isXLargeTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    @Override
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.pref_headers, target);
    }

    @Override
    public void onNewIntent(Intent intent)
    {
        if (intent.getAction() != null) {
            try {
                refreshFragments(getIntent() != null ? getIntent().getAction() : null);
            } catch (Exception e) {
                //
            }
        }
        setIntent(intent);
        if (!processExtraData()) {
            super.onNewIntent(intent);
        }
    }

    private boolean processExtraData() {
        final Intent intent = getIntent();
        if (intent != null) {
            final Bundle bundle = intent.getExtras();
            if (bundle != null) {
                final String str = bundle.getString("refresh");
                if (str != null) {
                    refreshProfileRatios();
                    return true;
                }
            }
        }
        return false;
    }

    private static void refreshProfileRatios() {
        profile_carb_ratio_default.setTitle(format_carb_ratio(profile_carb_ratio_default.getTitle().toString(), ProfileEditor.minMaxCarbs(ProfileEditor.loadData(false))));
        profile_insulin_sensitivity_default.setTitle(format_insulin_sensitivity(profile_insulin_sensitivity_default.getTitle().toString(), ProfileEditor.minMaxSens(ProfileEditor.loadData(false))));
    }

    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();
            if (preference instanceof ListPreference) {
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);
                preference.setSummary(
                        index >= 0
                                ? listPreference.getEntries()[index]
                                : null);

            } else if (preference instanceof RingtonePreference) {
                // For ringtone preferences, look up the correct display value
                // using RingtoneManager.
                if (TextUtils.isEmpty(stringValue)) {
                    // Empty values correspond to 'silent' (no ringtone).
                    preference.setSummary(R.string.pref_ringtone_silent);

                } else {
                    Ringtone ringtone = RingtoneManager.getRingtone(
                            preference.getContext(), Uri.parse(stringValue));

                    if (ringtone == null) {
                        // Clear the summary if there was a lookup error.
                        preference.setSummary(null);
                    } else {
                        // Set the summary to reflect the new ringtone display
                        // name.
                        String name = ringtone.getTitle(preference.getContext());
                        preference.setSummary(name);
                    }
                }

            } else {
                // For all other preferences, set the summary to the value's
                // simple string representation.
                preference.setSummary(stringValue);
            }
            return true;
        }
    };
    private static Preference.OnPreferenceChangeListener sBindNumericPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();
            if (isNumeric(stringValue)) {
                preference.setSummary(stringValue);
                return true;
            }
            return false;
        }
    };

    private static Preference.OnPreferenceChangeListener sBindNumericUnitizedPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() { // This listener adds glucose unit in addition to the value to the summary and rejects out-of-range inputs
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();
            if (isNumeric(stringValue)) {
                final boolean domgdl = Pref.getString("units", "mgdl").equals("mgdl"); // Identify which unit is chosen
                double submissionMgdl = domgdl ? tolerantParseDouble(stringValue) : tolerantParseDouble(stringValue) * Constants.MMOLL_TO_MGDL;
                if (submissionMgdl > MAX_GLUCOSE_INPUT || submissionMgdl < MIN_GLUCOSE_INPUT) {
                    JoH.static_toast_long(xdrip.gs(R.string.the_value_must_be_between_min_and_max, unitsConvert2Disp(domgdl, MIN_GLUCOSE_INPUT), unitsConvert2Disp(domgdl, MAX_GLUCOSE_INPUT)));
                    return false; // Reject input if out of range
                }
                preference.setSummary(stringValue + "  " + (domgdl ? "mg/dl" : "mmol/l")); // Set the summary to show the value followed by the chosen unit
                return true; // Accept input as it is numeric and in range
            }
            return false; // Reject input if not numeric
        }
    };
    private static Preference.OnPreferenceChangeListener sBindPreferenceTitleAppendToValueListenerUpdateChannel = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {

            preference.setTitle(preference.getTitle().toString().replaceAll("  \\([a-z0-9A-Z]+\\)$", "") + "  (" + value.toString() + ")");
            return true;
        }
    };

    private static Preference.OnPreferenceChangeListener sBindPreferenceTitleAppendToIntegerValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {

            boolean do_update = false;
            // detect not first run
            if (preference.getTitle().toString().contains("(")) {
                do_update = true;
            }

            preference.setTitle(preference.getTitle().toString().replaceAll("  \\([a-z0-9A-Z]+\\)$", "") + "  (" + value.toString() + ")");
            if (do_update) {
                preference.getEditor().putInt(preference.getKey(), (int)value).apply(); // update prefs now
            }
            return true;
        }
    };

    private static Preference.OnPreferenceChangeListener sBindPreferenceTitleAppendToStringValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {

            boolean do_update = false;
            // detect not first run
            if (preference.getTitle().toString().contains("(")) {
                do_update = true;
            }

            preference.setTitle(preference.getTitle().toString().replaceAll("  \\([a-z0-9A-Z.]+\\)$", "") + "  (" + value.toString() + ")");
            if (do_update) {
                preference.getEditor().putString(preference.getKey(), (String) value).apply(); // update prefs now
            }
            return true;
        }
    };

    private static Preference.OnPreferenceChangeListener sBindPreferenceTitleAppendToMacValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {

            boolean do_update = false;
            // detect not first run
            if (preference.getTitle().toString().contains("(")) {
                do_update = true;
            }
            String title =  preference.getTitle().toString().replaceAll("  \\([a-z0-9A-Z.:]+\\)$", "");
            if (!value.toString().isEmpty())
                title = title + "  (" + value.toString() + ")";
            preference.setTitle(title);
            if (do_update) {
                preference.getEditor().putString(preference.getKey(), (String) value).apply(); // update prefs now
            }
            return true;
        }
    };



    private static String format_carb_ratio(String oldValue, String newValue) {
        return oldValue.replaceAll(" \\(.*\\)$", "") + "  (" + newValue + "g per Unit)";
    }

    private static String format_carb_absorption_rate(String oldValue, String newValue) {
        return oldValue.replaceAll(" \\(.*\\)$", "") + "  (" + newValue + "g per hour)";
    }

    private static String format_insulin_sensitivity(String oldValue, String newValue) {
        try {
            return oldValue.replaceAll("  \\(.*\\)$", "") + "  (" + newValue + " " + static_units + " per U)";
        } catch (Exception e) {
            return "ERROR - Invalid number";
        }
    }


    private static void bindPreferenceSummaryToValue(Preference preference) {
        try {
            preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);
            sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                    PreferenceManager
                            .getDefaultSharedPreferences(preference.getContext())
                            .getString(preference.getKey(), ""));
        } catch (Exception e) {
            Log.e(TAG, "Got exception binding preference summary: " + e.toString());
        }
    }

    private static void bindPreferenceTitleAppendToValueUpdateChannel(Preference preference) {
        try {
            preference.setOnPreferenceChangeListener(sBindPreferenceTitleAppendToValueListenerUpdateChannel);
            sBindPreferenceTitleAppendToValueListenerUpdateChannel.onPreferenceChange(preference,
                    PreferenceManager
                            .getDefaultSharedPreferences(preference.getContext())
                            .getString(preference.getKey(), ""));
        } catch (Exception e) {
            Log.e(TAG, "Got exception binding preference title: " + e.toString());
        }
    }

    private static void bindPreferenceTitleAppendToStringValue(Preference preference) {
        try {
            preference.setOnPreferenceChangeListener(sBindPreferenceTitleAppendToStringValueListener);
            sBindPreferenceTitleAppendToStringValueListener.onPreferenceChange(preference,
                    PreferenceManager
                            .getDefaultSharedPreferences(preference.getContext())
                            .getString(preference.getKey(), ""));
        } catch (Exception e) {
            Log.e(TAG, "Got exception binding preference title: " + e.toString());
        }
    }

    private static void bindPreferenceTitleAppendToMacValue(Preference preference) {
        try {
            preference.setOnPreferenceChangeListener(sBindPreferenceTitleAppendToMacValueListener);
            sBindPreferenceTitleAppendToMacValueListener.onPreferenceChange(preference,
                    PreferenceManager
                            .getDefaultSharedPreferences(preference.getContext())
                            .getString(preference.getKey(), ""));
        } catch (Exception e) {
            Log.e(TAG, "Got exception binding preference title: " + e.toString());
        }
    }


    private static void bindPreferenceTitleAppendToIntegerValue(Preference preference) {
        try {
            preference.setOnPreferenceChangeListener(sBindPreferenceTitleAppendToIntegerValueListener);
            sBindPreferenceTitleAppendToIntegerValueListener.onPreferenceChange(preference,
                    PreferenceManager
                            .getDefaultSharedPreferences(preference.getContext())
                            .getInt(preference.getKey(), 0));
        } catch (Exception e) {
            Log.e(TAG, "Got exception binding preference title: " + e.toString());
        }
    }

    private static void bindPreferenceTitleAppendToIntegerValueFromLogSlider(Preference preference, NamedSliderProcessor ref, String name, boolean unitize) {

        final Preference.OnPreferenceChangeListener listener = new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object value) {

                boolean do_update = false;
                // detect not first run
                if (preference.getTitle().toString().contains("(")) {
                    do_update = true;
                }
                final int result = ref.interpolate(name, (int)value);

                preference.setTitle(preference.getTitle().toString().replaceAll("  \\([a-z0-9A-Z \\.]+\\)$", "") + "  (" + (unitize ? BgGraphBuilder.unitized_string_static_no_interpretation_short(result) : result) + ")");
                if (do_update) {
                    preference.getEditor().putInt(preference.getKey(), (int) value).apply(); // update prefs now
                }
                return true;
            }
        };

        try {
            preference.setOnPreferenceChangeListener(listener);
            listener.onPreferenceChange(preference,
                    PreferenceManager
                            .getDefaultSharedPreferences(preference.getContext())
                            .getInt(preference.getKey(), 0));
        } catch (Exception e) {
            Log.e(TAG, "Got exception binding preference title: " + e.toString());
        }
    }

    private static void bindPreferenceSummaryAppendToIntegerValueFromLogSlider(Preference preference, NamedSliderProcessor ref, String name, boolean unitize) {

        final Preference.OnPreferenceChangeListener listener = new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object value) {

                boolean do_update = false;
                // detect not first run
                if (preference.getSummary().toString().contains("(")) {
                    do_update = true;
                }
                final int result = ref.interpolate(name, (int)value);

                preference.setSummary(preference.getSummary().toString().replaceAll("  \\([a-z0-9A-Z \\.]+\\)$", "") + "  (" + (unitize ? BgGraphBuilder.unitized_string_static_no_interpretation_short(result) : result) + ")");
                if (do_update) {
                    preference.getEditor().putInt(preference.getKey(), (int) value).apply(); // update prefs now
                }
                return true;
            }
        };

        try {
            preference.setOnPreferenceChangeListener(listener);
            listener.onPreferenceChange(preference,
                    PreferenceManager
                            .getDefaultSharedPreferences(preference.getContext())
                            .getInt(preference.getKey(), 0));
        } catch (Exception e) {
            Log.e(TAG, "Got exception binding preference summary: " + e.toString());
        }
    }


    private static void bindPreferenceSummaryToValueAndEnsureNumeric(Preference preference) {
        preference.setOnPreferenceChangeListener(sBindNumericPreferenceSummaryToValueListener);
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(), ""));
    }

    private static void bindPreferenceSummaryToUnitizedValueAndEnsureNumeric(Preference preference) { // Use this to show the value as well as the corresponding glucose unit as the summary, and reject out-of-range inputs
        preference.setOnPreferenceChangeListener(sBindNumericUnitizedPreferenceSummaryToValueListener);
        sBindNumericUnitizedPreferenceSummaryToValueListener.onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(), ""));
    }

    public static void applyPrefSettingRange(String pref_key, String def, Double min, Double max) { // Correct a preference glucose setting if the value is out of range
        val notificationId = OUT_OF_RANGE_GLUCOSE_ENTRY_ID;
        String mySettingString = Pref.getString(pref_key, def);
        final boolean doMgdl = (Pref.getString("units", "mgdl").equals("mgdl"));
        double mySettingMgdl = doMgdl ? tolerantParseDouble(mySettingString) : tolerantParseDouble(mySettingString) * Constants.MMOLL_TO_MGDL; // The preference value in mg/dL
        if (mySettingMgdl > max) { // If the preference value is greater than max
            if (!doMgdl && mySettingString.equals(def)) { // If the setting value in mmol/L is the same as the default, which is in mg/dL, we correct the value next.
                // This will only happen if user has chosen mmol/L and updates to a version that has a new preference setting with default in mg/dL
                UserError.Log.d(TAG, "Setting  " + pref_key + "  to default converted to mmol/L");
                Pref.setString(pref_key, JoH.qs(tolerantParseDouble(def) * Constants.MGDL_TO_MMOLL, 1)); // Set the preference to the default value converted to mmol/L
            } else { // The preference has been set to a value greater than the max allowed.  Let's fix it and notify.
                // This will only happen if user has entered a preference setting value out of range before the listener range limit update has been merged.
                mySettingString = doMgdl ? max + "" : JoH.qs(max * Constants.MGDL_TO_MMOLL, 1) + "";
                Pref.setString(pref_key, mySettingString); // Set the preference to max
                UserError.Log.uel(TAG, xdrip.gs(R.string.pref_was_greater_than_max, pref_key)); // Inform the user that xDrip is changing the setting value
                showNotification(pref_key, xdrip.gs(R.string.setting_pref_to_max), null, notificationId, null, false, false, null, null, null, true);
            }
        } else if (mySettingMgdl < min) { // If the preference value is less than min, correct it and notify.
            // This will only happen if user has entered a preference setting value out of range before the listener range limit update has been merged.
            mySettingString = doMgdl ? min + "" : JoH.qs(min * Constants.MGDL_TO_MMOLL, 1) + "";
            Pref.setString(pref_key, mySettingString); // Set the preference to min
            UserError.Log.uel(TAG, xdrip.gs(R.string.pref_was_less_than_min, pref_key)); // Inform the user that xDrip is changing the setting value
            showNotification(pref_key, xdrip.gs(R.string.setting_pref_to_min), null, notificationId, null, false, false, null, null, null, true);

        }
    }

    @RequiredArgsConstructor
    public static class AllPrefsFragment extends PreferenceFragment {

        final String jumpTo;

        @Setter
        Preferences parent;
        SharedPreferences prefs;
        SearchConfiguration searchConfiguration;

        // default constructor is required in addition on some platforms
        public AllPrefsFragment() {
            this(null);
        }

        private void setSummary(String pref_name) {
     /*       try {
                // is there a cleaner way to bind these values when setting programatically?
                final String pref_val = this.prefs.getString(pref_name, "");
                findPreference(pref_name).setSummary(pref_val);
                EditTextPreference thispref = (EditTextPreference) findPreference(pref_name);
                thispref.setText(pref_val);
            } catch (Exception e) {
                Log.e(TAG, "Exception during setSummary: " + e.toString());
            }
            */
            setSummary_static(this, pref_name);
        }

        private static void setSummary_static(AllPrefsFragment allPrefsFragment, String pref_name) {
            try {
                // is there a cleaner way to bind these values when setting programatically?
                final String pref_val = allPrefsFragment.prefs.getString(pref_name, "");
                allPrefsFragment.findPreference(pref_name).setSummary(pref_val);
                EditTextPreference thispref = (EditTextPreference) allPrefsFragment.findPreference(pref_name);
                thispref.setText(pref_val);
            } catch (Exception e) {
                Log.e(TAG, "Exception during setSummary: " + e.toString());
            }
        }



        @SuppressLint("ApplySharedPref")
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            this.prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            final DexCollectionType collectionType = DexCollectionType.getType(this.prefs.getString("dex_collection_method", "BluetoothWixel"));

            static_units = this.prefs.getString("units", "mgdl");
            addPreferencesFromResource(R.xml.pref_license);
            addPreferencesFromResource(R.xml.pref_general);
            bindPreferenceSummaryToValueAndEnsureNumeric(findPreference("highValue"));
            bindPreferenceSummaryToValueAndEnsureNumeric(findPreference("lowValue"));
            units_pref = findPreference("units");
            bindPreferenceSummaryToValue(units_pref);

            addPreferencesFromResource(R.xml.pref_notifications);
            bindPreferenceSummaryToValue(findPreference("bg_alert_profile"));
            bindPreferenceSummaryToValue(findPreference("calibration_notification_sound"));
            bindPreferenceSummaryToValueAndEnsureNumeric(findPreference("calibration_snooze"));
            bindPreferenceSummaryToValueAndEnsureNumeric(findPreference("bg_unclear_readings_minutes"));
            bindPreferenceSummaryToValueAndEnsureNumeric(findPreference("disable_alerts_stale_data_minutes"));
            bindPreferenceSummaryToValue(findPreference("falling_bg_val"));
            bindPreferenceSummaryToValue(findPreference("rising_bg_val"));
            bindPreferenceSummaryToValue(findPreference("other_alerts_sound"));
            bindPreferenceSummaryToUnitizedValueAndEnsureNumeric(findPreference("persistent_high_threshold"));
            bindPreferenceSummaryToUnitizedValueAndEnsureNumeric(findPreference("forecast_low_threshold"));

            addPreferencesFromResource(R.xml.pref_data_source);

            addPreferencesFromResource(R.xml.pref_data_sync);
            setupBarcodeConfigScanner();
            setupBarcodeShareScanner();
            setupQrFromFile();

            addPreferencesFromResource(R.xml.pref_advanced_settings);
            addPreferencesFromResource(R.xml.xdrip_plus_prefs);

            bindPreferenceSummaryToValue(findPreference("persistent_high_threshold_mins"));
            bindPreferenceSummaryToValue(findPreference("persistent_high_repeat_mins"));

            bindPreferenceTitleAppendToValueUpdateChannel(findPreference("update_channel"));



            profile_insulin_sensitivity_default = findPreference("profile_insulin_sensitivity_default");
            profile_carb_ratio_default = findPreference("profile_carb_ratio_default");
            refreshProfileRatios();


            findPreference("disable_all_sync").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    prefs.edit().putBoolean("disable_all_sync", (boolean) newValue).commit();
                    SdcardImportExport.hardReset();
                    return true;
                }

            });

            // this gets cached in a static final field at the moment so needs hard reset
            findPreference("g5-battery-warning-level").setOnPreferenceChangeListener((preference, newValue) -> {
                prefs.edit().putString("g5-battery-warning-level", (String) newValue).commit();
                G5BaseService.resetTransmitterBatteryStatus();
                SdcardImportExport.hardReset();
                return true;
            });

            final Preference collectionMethod = findPreference("dex_collection_method");
            final Preference runInForeground = findPreference("run_service_in_foreground");
            final Preference g5nonraw = findPreference("g5_non_raw_method");
            final Preference wifiRecievers = findPreference("wifi_recievers_addresses");
            final Preference predictiveBG = findPreference("predictive_bg");
            final Preference interpretRaw = findPreference("interpret_raw");
            final Preference bfappid = findPreference("bugfender_appid");
            final Preference nfcSettings = findPreference("xdrip_plus_nfc_settings");
            final Preference bluereadersettings = findPreference("xdrip_blueReader_advanced_settings");
            //DexCollectionType collectionType = DexCollectionType.getType(findPreference("dex_collection_method").)

            final ListPreference currentCalibrationPlugin = (ListPreference)findPreference("current_calibration_plugin");
            final PreferenceCategory collectionCategory = (PreferenceCategory) findPreference("collection_category");

            //CareLink Follow preferences
            //final Preference carelinkFollowUser = findPreference("clfollow_user");
            //final Preference carelinkFollowPass = findPreference("clfollow_pass");
            final Preference carelinkFollowCountry = findPreference("clfollow_country");
            final Preference carelinkFollowPatient = findPreference("clfollow_patient");
            final Preference carelinkFollowLogin = findPreference("clfollow_login");
            final Preference carelinkFollowGracePeriod = findPreference("clfollow_grace_period");
            final Preference carelinkFollowMissedPollInterval = findPreference("clfollow_missed_poll_interval");
            final Preference carelinkFollowDownloadFingerBGs = findPreference("clfollow_download_finger_bgs");
            final Preference carelinkFollowDownloadBoluses = findPreference("clfollow_download_boluses");
            final Preference carelinkFollowDownloadMeals = findPreference("clfollow_download_meals");
            final Preference carelinkFollowDownloadNotifications = findPreference("clfollow_download_notifications");
            //Add CL prefs for CLFollower
            {
                try {
                    //collectionCategory.removePreference(carelinkFollowUser);
                    //collectionCategory.removePreference(carelinkFollowPass);
                    collectionCategory.removePreference(carelinkFollowCountry);
                    collectionCategory.removePreference(carelinkFollowPatient);
                    collectionCategory.removePreference(carelinkFollowLogin);
                    collectionCategory.removePreference(carelinkFollowGracePeriod);
                    collectionCategory.removePreference(carelinkFollowMissedPollInterval);
                    collectionCategory.removePreference(carelinkFollowDownloadFingerBGs);
                    collectionCategory.removePreference(carelinkFollowDownloadBoluses);
                    collectionCategory.removePreference(carelinkFollowDownloadMeals);
                    collectionCategory.removePreference(carelinkFollowDownloadNotifications);
                } catch (Exception e) {
                    //
                }
            }


            final Preference scanShare = findPreference("scan_share2_barcode");
            final EditTextPreference transmitterId = (EditTextPreference) findPreference("dex_txid");
           // final Preference closeGatt = findPreference("close_gatt_on_ble_disconnect");

            final Preference pebbleSync2 = findPreference("broadcast_to_pebble_type");
            final Preference pebbleSync1 = findPreference("broadcast_to_pebble");

            // Pebble Trend - START
            final Preference watchIntegration = findPreference("watch_integration");
            final PreferenceCategory watchCategory = (PreferenceCategory) findPreference("pebble_integration");
            //final ListPreference pebbleType = (ListPreference) findPreference("watch_integration");
            final Preference pebbleTrend = findPreference("pebble_display_trend");
            final Preference pebbleFilteredLine = findPreference("pebble_filtered_line");
            final Preference pebbleHighLine = findPreference("pebble_high_line");
            final Preference pebbleLowLine = findPreference("pebble_low_line");
            final Preference pebbleTrendPeriod = findPreference("pebble_trend_period");
            final Preference pebbleDelta = findPreference("pebble_show_delta");
            final Preference pebbleDeltaUnits = findPreference("pebble_show_delta_units");
            final Preference pebbleShowArrows = findPreference("pebble_show_arrows");
            final Preference pebbleVibrateNoSignal = findPreference("pebble_vibrate_no_signal");
            final Preference pebbleVibrateNoBluetooth = findPreference("pebble_vibrate_no_bluetooth");
            final Preference pebbleTinyDots = findPreference("pebble_tiny_dots");
            final EditTextPreference pebbleSpecialValue = (EditTextPreference) findPreference("pebble_special_value");
            bindPreferenceSummaryToValueAndEnsureNumeric(pebbleSpecialValue);
            final Preference pebbleSpecialText = findPreference("pebble_special_text");
            bindPreferenceSummaryToValue(pebbleSpecialText);
            // Pebble Trend - END

            bindPreferenceSummaryToValue(findPreference("node_wearG5"));//KS
            bindPreferenceSummaryToValue(findPreference("wear_logs_prefix"));
            bindPreferenceSummaryToValue(findPreference("disable_wearG5_on_missedreadings_level"));

            final Preference useCustomSyncKey = findPreference("use_custom_sync_key");
            final Preference CustomSyncKey = findPreference("custom_sync_key");

            final PreferenceCategory flairCategory = (PreferenceCategory) findPreference("xdrip_plus_display_colorset9_android5plus");
            //final PreferenceScreen updateScreen = (PreferenceScreen) findPreference("xdrip_plus_update_settings");
            final PreferenceScreen loggingScreen = (PreferenceScreen) findPreference("xdrip_logging_adv_settings");
            final PreferenceScreen motionScreen = (PreferenceScreen) findPreference("xdrip_plus_motion_settings");
            final PreferenceScreen nfcScreen = (PreferenceScreen) findPreference("xdrip_plus_nfc_settings");
            final PreferenceCategory otherCategory = (PreferenceCategory) findPreference("other_category");
            final PreferenceScreen calibrationAlertsScreen = (PreferenceScreen) findPreference("calibration_alerts_screen");
            final PreferenceCategory alertsCategory = (PreferenceCategory) findPreference("alerts_category");
            final Preference disableAlertsStaleDataMinutes = findPreference("disable_alerts_stale_data_minutes");
            final PreferenceScreen calibrationSettingsScreen = (PreferenceScreen) findPreference("xdrip_plus_calibration_settings");
            final PreferenceScreen colorScreen = (PreferenceScreen) findPreference("xdrip_plus_color_settings");
            final Preference old_school_calibration_mode = findPreference("old_school_calibration_mode");
            final Preference extraTagsForLogs = findPreference("extra_tags_for_logging");
            final Preference enableBF = findPreference("enable_bugfender");
            final PreferenceCategory displayCategory = (PreferenceCategory) findPreference("xdrip_plus_display_category");


            disableAlertsStaleDataMinutes.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if (!isNumeric(newValue.toString())) {
                        return false;
                    }
                    if ((Integer.parseInt(newValue.toString())) < 10) {
                        Toast.makeText(preference.getContext(),
                                "Value must be at least 10 minutes", Toast.LENGTH_LONG).show();
                        return false;
                    }
                    preference.setSummary(newValue.toString());
                    return true;
                }
            });


            units_pref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {

                    handleUnitsChange(preference, newValue, pFragment);
                   /* try {
                        final Double highVal = Double.parseDouble(AllPrefsFragment.this.prefs.getString("highValue", "0"));
                        final Double lowVal = Double.parseDouble(AllPrefsFragment.this.prefs.getString("lowValue", "0"));
                        final Double default_insulin_sensitivity = Double.parseDouble(AllPrefsFragment.this.prefs.getString("profile_insulin_sensitivity_default", "54"));
                        final Double default_target_glucose = Double.parseDouble(AllPrefsFragment.this.prefs.getString("plus_target_range", "100"));

                        static_units = newValue.toString();
                        if (newValue.toString().equals("mgdl")) {
                            if (highVal < 36) {
                                AllPrefsFragment.this.prefs.edit().putString("highValue", Long.toString(Math.round(highVal * Constants.MMOLL_TO_MGDL))).apply();
                                AllPrefsFragment.this.prefs.edit().putString("profile_insulin_sensitivity_default", Long.toString(Math.round(default_insulin_sensitivity * Constants.MMOLL_TO_MGDL))).apply();
                                AllPrefsFragment.this.prefs.edit().putString("plus_target_range", Long.toString(Math.round(default_target_glucose * Constants.MMOLL_TO_MGDL))).apply();
                                ProfileEditor.convertData(Constants.MMOLL_TO_MGDL);
                                Profile.invalidateProfile();
                              }
                            if (lowVal < 36) {
                                AllPrefsFragment.this.prefs.edit().putString("lowValue", Long.toString(Math.round(lowVal * Constants.MMOLL_TO_MGDL))).apply();
                                AllPrefsFragment.this.prefs.edit().putString("profile_insulin_sensitivity_default", Long.toString(Math.round(default_insulin_sensitivity * Constants.MMOLL_TO_MGDL))).apply();
                                AllPrefsFragment.this.prefs.edit().putString("plus_target_range", Long.toString(Math.round(default_target_glucose * Constants.MMOLL_TO_MGDL))).apply();
                                ProfileEditor.convertData(Constants.MMOLL_TO_MGDL);
                                Profile.invalidateProfile();
                            }

                        } else {
                            if (highVal > 35) {
                                AllPrefsFragment.this.prefs.edit().putString("highValue", JoH.qs(highVal * Constants.MGDL_TO_MMOLL, 1)).apply();
                                AllPrefsFragment.this.prefs.edit().putString("profile_insulin_sensitivity_default", JoH.qs(default_insulin_sensitivity * Constants.MGDL_TO_MMOLL, 2)).apply();
                                AllPrefsFragment.this.prefs.edit().putString("plus_target_range", JoH.qs(default_target_glucose * Constants.MGDL_TO_MMOLL,1)).apply();
                                ProfileEditor.convertData(Constants.MGDL_TO_MMOLL);
                                Profile.invalidateProfile();
                            }
                            if (lowVal > 35) {
                                AllPrefsFragment.this.prefs.edit().putString("lowValue", JoH.qs(lowVal * Constants.MGDL_TO_MMOLL, 1)).apply();
                                AllPrefsFragment.this.prefs.edit().putString("profile_insulin_sensitivity_default", JoH.qs(default_insulin_sensitivity * Constants.MGDL_TO_MMOLL, 2)).apply();
                                AllPrefsFragment.this.prefs.edit().putString("plus_target_range", JoH.qs(default_target_glucose * Constants.MGDL_TO_MMOLL,1)).apply();
                                ProfileEditor.convertData(Constants.MGDL_TO_MMOLL);
                                Profile.invalidateProfile();
                            }
                        }
                        preference.setSummary(newValue.toString());
                        setSummary("highValue");
                        setSummary("lowValue");
                        if (profile_insulin_sensitivity_default != null) {
                            Log.d(TAG, "refreshing profile insulin sensitivity default display");
                            profile_insulin_sensitivity_default.setTitle(format_insulin_sensitivity(profile_insulin_sensitivity_default.getTitle().toString(), ProfileEditor.minMaxSens(ProfileEditor.loadData(false))));

//                            do_format_insulin_sensitivity(profile_insulin_sensitivity_default, AllPrefsFragment.this.prefs, false, null);
                        }
                        Profile.reloadPreferences(AllPrefsFragment.this.prefs);

                    } catch (Exception e) {
                        Log.e(TAG, "Got excepting processing high/low value preferences: " + e.toString());
                    }*/
                    return true;
                }
            });

            bindPreferenceSummaryToValue(findPreference("low_predict_alarm_level"));
            Profile.validateTargetRange();
            bindPreferenceSummaryToValue(findPreference("plus_target_range"));


            Log.d(TAG, collectionType.name());
//            if (collectionType != DexCollectionType.DexcomShare) {
                collectionCategory.removePreference(scanShare);
                otherCategory.removePreference(interpretRaw);
                alertsCategory.addPreference(calibrationAlertsScreen);
//            }

//            if (collectionType != DexCollectionType.Medtrum) {
                try {
                    collectionCategory.removePreference(findPreference("medtrum_use_native"));
                    collectionCategory.removePreference(findPreference("medtrum_a_hex"));
                } catch (Exception e) {
                    //
                }
//            }

            if (getBestCollectorHardwareName().equals("G7")) { // Remove battery options, from G5/G6/Dex1/G7 Debug Settings, if we are using G7 or One+
                try {
                    PreferenceScreen screen = (PreferenceScreen) findPreference("xdrip_plus_g5_extra_settings");
                    Preference pref = getPreferenceManager().findPreference("dex_battery_category");
                    screen.removePreference(pref);
                } catch (Exception e) {
                    UserError.Log.wtf(TAG, "Failed to remove G7 battery options");
                }
            }

            //Remove CL prefs for NON CLFollower
//            if (collectionType != DexCollectionType.CLFollow) {
                try {
                    collectionCategory.removePreference(carelinkFollowCountry);
                    collectionCategory.removePreference(carelinkFollowPatient);
                    //collectionCategory.removePreference(carelinkFollowPass);
                    //collectionCategory.removePreference(carelinkFollowUser);
                    collectionCategory.removePreference(carelinkFollowGracePeriod);
                    collectionCategory.removePreference(carelinkFollowMissedPollInterval);
                    collectionCategory.removePreference(carelinkFollowDownloadFingerBGs);
                    collectionCategory.removePreference(carelinkFollowDownloadMeals);
                    collectionCategory.removePreference(carelinkFollowDownloadNotifications);
                } catch (Exception e) {
                    //
                }
//            }


            final boolean engineering_mode = this.prefs.getBoolean("engineering_mode",false);

            if (!engineering_mode) {
                try {
                    displayCategory.removePreference(findPreference("bg_compensate_noise_ultrasensitive"));
                } catch (Exception e) {
                    //
                }
            }

            //if (engineering_mode) {
                // populate the list
                PluggableCalibration.setListPreferenceData(currentCalibrationPlugin);

                currentCalibrationPlugin.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        PluggableCalibration.invalidateCache(); // current
                        PluggableCalibration.invalidateCache(newValue.toString()); // next
                        PluggableCalibration.invalidatePluginCache(); // reset the object cache
                        return true;
                    }
                });
            //}

//            if (!DexCollectionType.hasLibre(collectionType)) {
                collectionCategory.removePreference(nfcSettings);
//            }

//            if (!DexCollectionService.getBestLimitterHardwareName().equals("BlueReader")) {
                collectionCategory.removePreference(bluereadersettings);
//            }


            try {

                try {
//                    if (DexCollectionType.getDexCollectionType() != DexCollectionType.LibreReceiver) {
//                        collectionCategory.removePreference(libre2settings);
//                    }
                } catch (NullPointerException e) {
                    Log.wtf(TAG, "Nullpointer Libre2Settings: ", e);
                }

                try {
//                    if (!DexCollectionType.hasWifi()) {
                        final String receiversIpAddresses = this.prefs.getString("wifi_recievers_addresses", "").trim();
                        // only hide if non wifi wixel mode and value not previously set to cope with
                        // dynamic mode changes. jamorham
                        if (receiversIpAddresses.equals("")) {
                            collectionCategory.removePreference(wifiRecievers);
                        }
//                    }
                } catch (NullPointerException e) {
                    Log.wtf(TAG, "Nullpointer wifireceivers ", e);
                }

//                if ((collectionType != DexCollectionType.DexbridgeWixel)
//                        && (collectionType != DexCollectionType.WifiDexBridgeWixel)) {
                    try {
                        collectionCategory.removePreference(transmitterId);
                        // collectionCategory.removePreference(closeGatt);
                    } catch (NullPointerException e) {
                        Log.wtf(TAG, "Nullpointer removing txid ", e);
                    }
//                }

	            // don't show web deposit unless in engineering mode
                if (!Pref.getBooleanDefaultFalse("engineering_mode")) {
                    try {
                        final PreferenceScreen screen = (PreferenceScreen) findPreference("cloud_data_sync");
                        screen.removePreference(findPreference("cloud_storage_web_deposit"));
                    } catch (Exception e) {
                        //
                    }
                }


                final PreferenceScreen g5_settings_screen = (PreferenceScreen) findPreference("xdrip_plus_g5_extra_settings");
                if (collectionType == DexCollectionType.DexcomG5) {
                    try {
                        collectionCategory.addPreference(transmitterId);

                        collectionCategory.addPreference(g5_settings_screen);
                        //collectionCategory.addPreference(g5nonraw);
                        //collectionCategory.addPreference(scanConstantly);
                        //collectionCategory.addPreference(reAuth);
                        //collectionCategory.addPreference(reBond);
                        //collectionCategory.addPreference(runOnMain);
                    } catch (NullPointerException e) {
                        Log.wtf(TAG, "Null pointer adding G5 prefs ", e);
                    }
                } else {
                    try {
                        // collectionCategory.removePreference(transmitterId);

                        collectionCategory.removePreference(g5_settings_screen);
                       // collectionCategory.removePreference(scanConstantly);
                       // collectionCategory.removePreference(g5nonraw);
                       // collectionCategory.removePreference(reAuth);
                       // collectionCategory.removePreference(reBond);
                       // collectionCategory.removePreference(runOnMain);
                    } catch (NullPointerException e) {
                        Log.wtf(TAG, "Null pointer removing G5 prefs ", e);
                    }
                }

                if (!engineering_mode) {
                    try {
                        if (!Experience.gotData()) getPreferenceScreen().removePreference(motionScreen);
                        calibrationSettingsScreen.removePreference(old_school_calibration_mode);
                    } catch (NullPointerException e) {
                        Log.wtf(TAG, "Nullpointer with engineering mode s ", e);
                    }
                }
                if ((!engineering_mode) || (!this.prefs.getBoolean("enable_bugfender", false))) {
                    loggingScreen.removePreference(bfappid);
                }

            } catch (NullPointerException e) {
                Log.wtf(TAG, "Got null pointer exception removing pref: ", e);
            }

            if (engineering_mode || this.prefs.getString("update_channel", "").matches("alpha|nightly")) {
                ListPreference update_channel = (ListPreference) findPreference("update_channel");
                update_channel.setEntryValues(getResources().getStringArray(R.array.UpdateChannelE));
                update_channel.setEntries(getResources().getStringArray(R.array.UpdateChannelDetailE));
            }

            final DecimalFormat df = new DecimalFormat("#.#");

            if (this.prefs.getString("units", "mgdl").compareTo("mmol") != 0) {
                df.setMaximumFractionDigits(0);
                pebbleSpecialValue.setDefaultValue("99");
                if (pebbleSpecialValue.getText().compareTo("5.5") == 0) {
                    pebbleSpecialValue.setText(df.format(Double.valueOf(pebbleSpecialValue.getText()) * Constants.MMOLL_TO_MGDL));
                }
            } else {
                df.setMaximumFractionDigits(1);
                pebbleSpecialValue.setDefaultValue("5.5");
                if (pebbleSpecialValue.getText().compareTo("99") == 0) {
                    pebbleSpecialValue.setText(df.format(Double.valueOf(pebbleSpecialValue.getText()) / Constants.MMOLL_TO_MGDL));
                }
            }

            try {
                findPreference("calibration_notifications").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        // clear any pending alerts
                        final UserNotification userNotification = UserNotification.lastCalibrationAlert();
                        if (userNotification != null) {
                            userNotification.delete();
                        }
                        return true;
                    }
                });
            } catch (Exception e) {
                //
            }

            bindPreferenceSummaryToValue(collectionMethod);

            bindPreferenceTitleAppendToStringValue(findPreference("retention_days_bg_reading"));

            extraTagsForLogs.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    ExtraLogTags.readPreference((String)newValue);
                    return true;
                }
            });

            bindPreferenceSummaryToValue(transmitterId); // duplicated below but this sets initial value
            transmitterId.getEditText().setFilters(new InputFilter[]{new InputFilter.AllCaps()});
            transmitterId.getEditText().post(() -> {
                try {
                    // position to end of input text
                    transmitterId.getEditText().setSelection(transmitterId.getEditText().getText().length());
                } catch (Exception e) {
                    UserError.Log.d(TAG, "Could not set selection for transmitter id: " + e);
                }
            });
            TxIdHelper.attachValidator(transmitterId.getEditText());
            transmitterId.setOnPreferenceChangeListener((preference, newValue) -> {

                TxIdHelper.handleTransmitterEntry((String)newValue, getActivity(), transmitterId1 -> {
                    Pref.setString(preference.getKey(), transmitterId1); // save the accepted value
                    ((EditTextPreference)preference).setText(transmitterId1); // update active value

                    new Thread(() -> { // TODO this logic is duplicated and could be centralized
                        Log.d(TAG, "Trying to restart collector due to tx id change");
                        clearDataWhenTransmitterIdEntered(transmitterId1);
                        CollectionServiceStarter.restartCollectionServiceBackground();
                    }).start();

                    sBindPreferenceSummaryToValueListener.onPreferenceChange(preference, transmitterId1);
                });

                return false; // don't allow by default - handled by callback
            });

            // when changing collection method
            collectionMethod.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {

                    DexCollectionType collectionType = DexCollectionType.getType((String) newValue);

                    if (collectionType != DexCollectionType.DexcomShare) { // NOT USING SHARE
//                        collectionCategory.removePreference(shareKey);
                        collectionCategory.removePreference(scanShare);
                        otherCategory.removePreference(interpretRaw);
                        otherCategory.addPreference(predictiveBG);
                        alertsCategory.addPreference(calibrationAlertsScreen);
                    } else {
//                        collectionCategory.addPreference(shareKey);
                        collectionCategory.addPreference(scanShare);
                        otherCategory.addPreference(interpretRaw);
                        otherCategory.removePreference(predictiveBG);
                        alertsCategory.removePreference(calibrationAlertsScreen);
                        AllPrefsFragment.this.prefs.edit().putBoolean("calibration_notifications", false).apply();
                    }

//                    if (!DexCollectionType.hasLibre(collectionType)) {
                        collectionCategory.removePreference(nfcSettings);
//                    }


                    // jamorham always show wifi receivers option if populated as we may switch modes dynamically
                    if (!DexCollectionType.hasWifi()) {
                        String receiversIpAddresses;
                        receiversIpAddresses = AllPrefsFragment.this.prefs.getString("wifi_recievers_addresses", "");
                        if (receiversIpAddresses == null || receiversIpAddresses.trim().equals("")) {
                            collectionCategory.removePreference(wifiRecievers);
                        } else {
                            collectionCategory.addPreference(wifiRecievers);
                        }
                    } else {
                        collectionCategory.addPreference(wifiRecievers);
                    }

//                    if ((collectionType != DexCollectionType.DexbridgeWixel)
//                            && (collectionType != DexCollectionType.WifiDexBridgeWixel)) {
                        collectionCategory.removePreference(transmitterId);
//                    }

                    if (collectionType == DexCollectionType.DexcomG5) {
                        collectionCategory.addPreference(transmitterId);
                        // TODO add debug menu
                    }


                    String stringValue = newValue.toString();
                    if (preference instanceof ListPreference) {
                        ListPreference listPreference = (ListPreference) preference;
                        int index = listPreference.findIndexOfValue(stringValue);
                        preference.setSummary(
                                index >= 0
                                        ? listPreference.getEntries()[index]
                                        : null);

                    } else if (preference instanceof RingtonePreference) {
                        if (TextUtils.isEmpty(stringValue)) {
                            preference.setSummary(R.string.pref_ringtone_silent);

                        } else {
                            Ringtone ringtone = RingtoneManager.getRingtone(
                                    preference.getContext(), Uri.parse(stringValue));
                            if (ringtone == null) {
                                preference.setSummary(null);
                            } else {
                                String name = ringtone.getTitle(preference.getContext());
                                preference.setSummary(name);
                            }
                        }
                    } else {
                        preference.setSummary(stringValue);
                    }
                    CollectionServiceStarter.restartCollectionServiceBackground();

                    // This generically refreshes the fragment which may well nullify some of the ui logic above as it does a complete rebuild
                    Inevitable.task("refresh-prefs", 100, new Runnable() {
                        @Override
                        public void run() {
                            try {
                                JoH.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (parent != null) parent.refreshFragments();
                                    }
                                });
                            } catch (Exception e) {
                                Log.e(TAG, "Got exception refreshing fragments: " + e);
                            }
                        }
                    });
                    return true;
                }
            });
            removeLegacyPreferences();
            jumpToScreen(jumpTo);
        }

       private void removeLegacyPreferences() {
           //  removePreferenceFromCategory("use_ob1_g5_collector_service", "ob1_options");
           //  removePreferenceFromCategory("ob1_g5_fallback_to_xdrip", "ob1_options");
           //  removePreferenceFromCategory("always_unbond_G5", "ob1_options");
           //  removePreferenceFromCategory("always_get_new_keys", "ob1_options");
           // removePreferenceFromCategory("run_ble_scan_constantly", "ob1_options");
           // removePreferenceFromCategory("run_G5_ble_tasks_on_uithread", "ob1_options");
       }

        private void showSearchFragment() {
            // lazy instantiation
            if (searchConfiguration == null) {
                try {
                    searchConfiguration = new SearchConfiguration(getActivity());
                    searchConfiguration.setBreadcrumbsEnabled(true);

                    searchConfiguration.getKeysList().addAll(getAllPreferenceKeys(this.getPreferenceScreen()));
                    searchConfiguration.index(R.xml.pref_general);
                    searchConfiguration.index(R.xml.pref_notifications);
                    searchConfiguration.index(R.xml.pref_data_source);
                    searchConfiguration.index(R.xml.pref_data_sync);
                    searchConfiguration.index(R.xml.pref_advanced_settings);
                    searchConfiguration.index(R.xml.xdrip_plus_prefs);
                } catch (NullPointerException e) {
                    Log.e(TAG, "Cannot find searchPreference item: " + e);
                }
            }
            searchConfiguration.showSearchFragment();

        }


        private void setupBarcodeConfigScanner() {
            findPreference("auto_configure").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    new AndroidBarcode(getActivity()).scan();
                    return true;
                }
            });
        }


        private void setupBarcodeShareScanner() {
            findPreference("scan_share2_barcode").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    new AndroidBarcode(getActivity()).scan();
                    return true;
                }
            });
        }

        private void setupQrFromFile() {
            findPreference("qr_code_from_file").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) { // Listener for scanning QR code from file
//                    new QrCodeFromFile(getActivity()).scanFile();
                    return true;
                }
            });
        }

        public void jumpToScreen(final String screenKey) {
            if (screenKey == null) return;
            UserError.Log.d(TAG, "jump to screen: " + screenKey);
            PreferenceScreen subPreferenceScreen = (PreferenceScreen) findPreference(screenKey);
            final AllPrefsFragment fragment = this;
            JoH.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    clickScreen(fragment, subPreferenceScreen);
                }
            });
        }

        private static void clickScreen(final PreferenceFragment fragment, final PreferenceScreen screen) {
            if (screen == null) return;
            if (fragment.getPreferenceScreen() != screen) {
                try {
                    final Method method = screen.getClass().getDeclaredMethod("onClick");
                    method.setAccessible(true);
                    method.invoke(screen);
                } catch (Exception e) {
                    android.util.Log.e(TAG, "" + e);
                }
            } else {
                android.util.Log.d(TAG, "Already on that screen");
            }
        }

    }

    public static void handleUnitsChange(Preference preference, Object newValue, AllPrefsFragment allPrefsFragment) {
        try {
            SharedPreferences preferences;
            if (preference!= null) {
                preferences = preference.getSharedPreferences();
            } else {
                preferences = PreferenceManager.getDefaultSharedPreferences(xdrip.getAppContext());
            }

            final Double highVal = Double.parseDouble(preferences.getString("highValue", "0"));
            final Double lowVal = Double.parseDouble(preferences.getString("lowValue", "0"));
            final Double default_insulin_sensitivity = Double.parseDouble(preferences.getString("profile_insulin_sensitivity_default", "54"));
            final Double default_target_glucose = Double.parseDouble(preferences.getString("plus_target_range", "100"));
            final Double persistent_high_Val = Double.parseDouble(preferences.getString("persistent_high_threshold", "0"));
            final Double forecast_low_Val = Double.parseDouble(preferences.getString("forecast_low_threshold", "0"));


            static_units = newValue.toString();
            if (newValue.toString().equals("mgdl")) {
                if (highVal < 36) {
                    ProfileEditor.convertData(Constants.MMOLL_TO_MGDL);
                    preferences.edit().putString("highValue", Long.toString(Math.round(highVal * Constants.MMOLL_TO_MGDL))).apply();
                    preferences.edit().putString("profile_insulin_sensitivity_default", Long.toString(Math.round(default_insulin_sensitivity * Constants.MMOLL_TO_MGDL))).apply();
                    preferences.edit().putString("plus_target_range", Long.toString(Math.round(default_target_glucose * Constants.MMOLL_TO_MGDL))).apply();
                    Profile.invalidateProfile();
                }
                if (persistent_high_Val < 36) {
                    ProfileEditor.convertData(Constants.MMOLL_TO_MGDL);
                    preferences.edit().putString("persistent_high_threshold", Long.toString(Math.round(persistent_high_Val * Constants.MMOLL_TO_MGDL))).apply();
                    Profile.invalidateProfile();
                }
                if (forecast_low_Val < 36) {
                    ProfileEditor.convertData(Constants.MMOLL_TO_MGDL);
                    preferences.edit().putString("forecast_low_threshold", Long.toString(Math.round(forecast_low_Val * Constants.MMOLL_TO_MGDL))).apply();
                    Profile.invalidateProfile();
                }
                if (lowVal < 36) {
                    ProfileEditor.convertData(Constants.MMOLL_TO_MGDL);
                    preferences.edit().putString("lowValue", Long.toString(Math.round(lowVal * Constants.MMOLL_TO_MGDL))).apply();
                    preferences.edit().putString("profile_insulin_sensitivity_default", Long.toString(Math.round(default_insulin_sensitivity * Constants.MMOLL_TO_MGDL))).apply();
                    preferences.edit().putString("plus_target_range", Long.toString(Math.round(default_target_glucose * Constants.MMOLL_TO_MGDL))).apply();
                    Profile.invalidateProfile();
                }

            } else {
                if (highVal > 35) {
                    ProfileEditor.convertData(Constants.MGDL_TO_MMOLL);
                    preferences.edit().putString("highValue", JoH.qs(highVal * Constants.MGDL_TO_MMOLL, 1)).apply();
                    preferences.edit().putString("profile_insulin_sensitivity_default", JoH.qs(default_insulin_sensitivity * Constants.MGDL_TO_MMOLL, 2)).apply();
                    preferences.edit().putString("plus_target_range", JoH.qs(default_target_glucose * Constants.MGDL_TO_MMOLL,1)).apply();
                    Profile.invalidateProfile();
                }
                if (persistent_high_Val > 35) {
                    ProfileEditor.convertData(Constants.MGDL_TO_MMOLL);
                    preferences.edit().putString("persistent_high_threshold", JoH.qs(persistent_high_Val * Constants.MGDL_TO_MMOLL, 1)).apply();
                    Profile.invalidateProfile();
                }
                if (forecast_low_Val > 35) {
                    ProfileEditor.convertData(Constants.MGDL_TO_MMOLL);
                    preferences.edit().putString("forecast_low_threshold", JoH.qs(forecast_low_Val * Constants.MGDL_TO_MMOLL, 1)).apply();
                    Profile.invalidateProfile();
                }
                if (lowVal > 35) {
                    ProfileEditor.convertData(Constants.MGDL_TO_MMOLL);
                    preferences.edit().putString("lowValue", JoH.qs(lowVal * Constants.MGDL_TO_MMOLL, 1)).apply();
                    preferences.edit().putString("profile_insulin_sensitivity_default", JoH.qs(default_insulin_sensitivity * Constants.MGDL_TO_MMOLL, 2)).apply();
                    preferences.edit().putString("plus_target_range", JoH.qs(default_target_glucose * Constants.MGDL_TO_MMOLL,1)).apply();
                    Profile.invalidateProfile();
                }
            }
            if (preference != null) preference.setSummary(newValue.toString());
            if (allPrefsFragment != null) {
                allPrefsFragment.setSummary("highValue");
                allPrefsFragment.setSummary("lowValue");
                allPrefsFragment.setSummary("persistent_high_threshold");
                allPrefsFragment.setSummary("forecast_low_threshold");
            }
            if (profile_insulin_sensitivity_default != null) {
                Log.d(TAG, "refreshing profile insulin sensitivity default display");
                profile_insulin_sensitivity_default.setTitle(format_insulin_sensitivity(profile_insulin_sensitivity_default.getTitle().toString(), ProfileEditor.minMaxSens(ProfileEditor.loadData(false))));

//                            do_format_insulin_sensitivity(profile_insulin_sensitivity_default, AllPrefsFragment.this.prefs, false, null);
            }
            Profile.reloadPreferences(preferences);

        } catch (Exception e) {
            Log.e(TAG, "Got excepting processing high/low value preferences: " + e.toString());
        }
    }

    public static boolean isNumeric(String str) {
        try {
            Double.parseDouble(str);
        } catch (NumberFormatException nfe) {
            return false;
        }
        return true;
    }
}
