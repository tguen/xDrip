package com.eveningoutpost.dexdrip;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.eveningoutpost.dexdrip.calibrations.NativeCalibrationPipe;
import com.eveningoutpost.dexdrip.calibrations.PluggableCalibration;
import com.eveningoutpost.dexdrip.dagger.Injectors;
import com.eveningoutpost.dexdrip.databinding.ActivityHomeBinding;
import com.eveningoutpost.dexdrip.databinding.ActivityHomeShelfSettingsBinding;
import com.eveningoutpost.dexdrip.databinding.PopupInitialStatusHelperBinding;
import com.eveningoutpost.dexdrip.g5model.DexSyncKeeper;
import com.eveningoutpost.dexdrip.g5model.DexTimeKeeper;
import com.eveningoutpost.dexdrip.g5model.Ob1G5StateMachine;
import com.eveningoutpost.dexdrip.g5model.SensorDays;
import com.eveningoutpost.dexdrip.models.ActiveBgAlert;
import com.eveningoutpost.dexdrip.models.BgReading;
import com.eveningoutpost.dexdrip.models.BloodTest;
import com.eveningoutpost.dexdrip.models.Calibration;
import com.eveningoutpost.dexdrip.models.JoH;
import com.eveningoutpost.dexdrip.models.ProcessInitialDataQuality;
import com.eveningoutpost.dexdrip.models.Sensor;
import com.eveningoutpost.dexdrip.models.UserError;
import com.eveningoutpost.dexdrip.services.Ob1G5CollectionService;
import com.eveningoutpost.dexdrip.ui.BaseShelf;
import com.eveningoutpost.dexdrip.ui.MicroStatus;
import com.eveningoutpost.dexdrip.ui.MicroStatusImpl;
import com.eveningoutpost.dexdrip.ui.NumberGraphic;
import com.eveningoutpost.dexdrip.ui.UiPing;
import com.eveningoutpost.dexdrip.ui.dialog.DidYouCancelAlarm;
import com.eveningoutpost.dexdrip.ui.dialog.QuickSettingsDialogs;
import com.eveningoutpost.dexdrip.ui.graphic.ITrendArrow;
import com.eveningoutpost.dexdrip.ui.graphic.TrendArrowFactory;
import com.eveningoutpost.dexdrip.utilitymodels.*;
import com.eveningoutpost.dexdrip.utils.ActivityWithMenu;
import com.eveningoutpost.dexdrip.utils.DexCollectionType;
import com.eveningoutpost.dexdrip.utils.Preferences;
import com.eveningoutpost.dexdrip.utils.SdcardImportExport;
import com.google.android.material.snackbar.Snackbar;

import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import lecho.lib.hellocharts.gesture.ChartTouchHandler;
import lecho.lib.hellocharts.gesture.ZoomType;
import lecho.lib.hellocharts.listener.ViewportChangeListener;
import lecho.lib.hellocharts.model.Viewport;
import lecho.lib.hellocharts.view.LineChartView;
import lecho.lib.hellocharts.view.PreviewLineChartView;
import lombok.val;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static com.eveningoutpost.dexdrip.g5model.Ob1G5StateMachine.shortTxId;
import static com.eveningoutpost.dexdrip.models.JoH.msSince;
import static com.eveningoutpost.dexdrip.models.JoH.quietratelimit;
import static com.eveningoutpost.dexdrip.models.JoH.tsl;
import static com.eveningoutpost.dexdrip.services.Ob1G5CollectionService.getTransmitterID;
import static com.eveningoutpost.dexdrip.utilitymodels.ColorCache.X;
import static com.eveningoutpost.dexdrip.utilitymodels.ColorCache.getCol;
import static com.eveningoutpost.dexdrip.utilitymodels.Constants.HOUR_IN_MS;
import static com.eveningoutpost.dexdrip.utilitymodels.Constants.MINUTE_IN_MS;
import static com.eveningoutpost.dexdrip.utilitymodels.Constants.SECOND_IN_MS;
import static com.eveningoutpost.dexdrip.utils.DexCollectionType.DexcomG5;
import static com.eveningoutpost.dexdrip.xdrip.gs;


public class Home extends ActivityWithMenu implements ActivityCompat.OnRequestPermissionsResultCallback {
    public static final String CREATE_TREATMENT_NOTE = "CREATE_TREATMENT_NOTE";
    public static final String BLOOD_TEST_ACTION = "BLOOD_TEST_ACTION";
    public static final String HOME_FULL_WAKEUP = "HOME_FULL_WAKEUP";
    public static final String SHOW_NOTIFICATION = "SHOW_NOTIFICATION";
    public static final String BLUETOOTH_METER_CALIBRATION = "BLUETOOTH_METER_CALIBRATION";
    public static final int SENSOR_READY_ID = 4912;
    private static final int REQ_CODE_BATTERY_OPTIMIZATION = 1996;
    private static final float DEFAULT_CHART_HOURS = 2.5f;
    private static final String TAG = Home.class.getSimpleName();
    private static final boolean d = false;

    public static boolean activityVisible = false;
    public static boolean blockTouches = false;
    private static float hours = DEFAULT_CHART_HOURS;
    private static boolean is_holo = true;
    private static boolean reset_viewport = false;
    private static String nexttoast;
    private static Activity mActivity;

    public BgGraphBuilder bgGraphBuilder;
    public Viewport holdViewport = new Viewport();
    public LineChartView chart;
    public AlertDialog dialog;
    private boolean updateStuff;
    private boolean updatingPreviewViewport = false;
    private boolean updatingChartViewport = false;
    private long lastViewPortPan;
    private long lastDataTick;
    private boolean screen_forced_on = false;
    private boolean isG5Share;
    private BroadcastReceiver _broadcastReceiver;
    private BroadcastReceiver newDataReceiver;
    private PreviewLineChartView previewChart;
    private TextView dexbridgeBattery;
    private TextView uploaderBattery;
    private TextView sensorAge;
    private TextView currentBgValueText;
    private TextView notificationText;
    private boolean alreadyDisplayedBgInfoCommon = false;
    private String display_delta = "";
    private boolean small_width = false;
    private boolean small_height = false;
    private boolean small_screen = false;
    private AlertDialog helper_dialog;
    private AlertDialog status_helper_dialog;
    private PopupInitialStatusHelperBinding initial_status_binding;
    private ActivityHomeBinding binding;
    private ITrendArrow itr;
    private final UiPing ui = new UiPing();

    @Inject
    BaseShelf homeShelf;
    //@Inject
    MicroStatus microStatus;

    NanoStatus nanoStatus;
    NanoStatus expiryStatus;

    private ProcessInitialDataQuality.InitialDataQuality initialDataQuality;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mActivity = this;

        if (!xdrip.checkAppContext(getApplicationContext())) {
            toast(gs(R.string.unusual_internal_context_problem__please_report));
            Log.wtf(TAG, "xdrip.checkAppContext FAILED!");
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                // not much to do
            }
            if (!xdrip.checkAppContext(getApplicationContext())) {
                toast(gs(R.string.cannot_start__please_report_context_problem));
                finish();
            }
        }

        super.onCreate(savedInstanceState);
        setTheme(R.style.AppThemeToolBarLite); // for toolbar mode

        Injectors.getHomeShelfComponent().inject(this);

        nanoStatus = new NanoStatus("collector", 1000);
        expiryStatus = new NanoStatus("s-expiry", 15000);

        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        binding.setVs(homeShelf);
        binding.setHome(this);
        binding.setUi(ui);
        binding.setNano(nanoStatus);
        binding.setExpiry(expiryStatus);
        setContentView(binding.getRoot());

        Toolbar mToolbar = binding.myToolbar;
        setSupportActionBar(mToolbar);

        final Home home = this;
        mToolbar.setLongClickable(true);
        mToolbar.setOnLongClickListener(v -> {
            // show configurator
            final ActivityHomeShelfSettingsBinding binding = ActivityHomeShelfSettingsBinding.inflate(getLayoutInflater());
            final Dialog dialog = new Dialog(home);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            binding.setVs(homeShelf);
            binding.setHome(home);
            homeShelf.set("arrow_configurator", false);
            dialog.setContentView(binding.getRoot());
            dialog.show();
            return false;
        });

        this.dexbridgeBattery = binding.textBridgeBattery;
        this.uploaderBattery = binding.uploaderbattery;
        this.sensorAge = binding.libstatus;
        this.currentBgValueText = binding.currentBgValueRealTime;

        uploaderBattery.setText("");
        sensorAge.setText("");

        if (BgGraphBuilder.isXLargeTablet(getApplicationContext())) {
            this.currentBgValueText.setTextSize(100);
        }
        this.notificationText = binding.notices;
        if (BgGraphBuilder.isXLargeTablet(getApplicationContext())) {
            this.notificationText.setTextSize(40);
        }

            if (Experience.installedForAtLeast(5 * Constants.MINUTE_IN_MS)) {
                checkBatteryOptimization();
            }

        final DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        int screen_width = dm.widthPixels;
        int screen_height = dm.heightPixels;
        if (screen_width <= 320) {
            small_width = true;
            small_screen = true;
        }
        if (screen_height <= 320) {
            small_height = true;
            small_screen = true;
        }
        //final int refdpi = 320;
        Log.d(TAG, "Width height: " + screen_width + " " + screen_height + " DPI:" + dm.densityDpi);

        JoH.fixActionBar(this);
        try {
            getSupportActionBar().setTitle(R.string.app_name);
        } catch (NullPointerException e) {
            Log.e(TAG, "Couldn't set title due to null pointer");
        }
        activityVisible = true;

        // handle incoming extras
        final Bundle bundle = getIntent().getExtras();
        processIncomingBundle(bundle);

        checkBadSettings();
        // lower priority

        currentBgValueText.setText(""); // clear any design prototyping default
    }

    private boolean checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            final String packageName = getPackageName();
            //Log.d(TAG, "Maybe ignoring battery optimization");
            final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(packageName) &&
                    !Pref.getBooleanDefaultFalse("requested_ignore_battery_optimizations_new") && !xdrip.isRunningTest()) {
                Log.d(TAG, "Requesting ignore battery optimization");

                if (((dialog == null) || (!dialog.isShowing()))
                        && (PersistentStore.incrementLong("asked_battery_optimization") < 40000)) {
                    JoH.show_ok_dialog(this, gs(R.string.please_allow_permission), gs(R.string.xdrip_needs_whitelisting_for_proper_performance), new Runnable() {

                        @RequiresApi(api = Build.VERSION_CODES.M)
                        @Override
                        public void run() {
                            try {
                                final Intent intent = new Intent();

                                // ignoring battery optimizations required for constant connection
                                // to peripheral device - eg CGM transmitter.
                                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                                intent.setData(Uri.parse("package:" + packageName));
                                startActivityForResult(intent, REQ_CODE_BATTERY_OPTIMIZATION);

                            } catch (ActivityNotFoundException e) {
                                JoH.static_toast_short(getString(R.string.no_battery_optimization_whitelisting));
                                UserError.Log.wtf(TAG, "Device does not appear to support battery optimization whitelisting!");
                            }
                        }
                    });
                } else {
                    JoH.static_toast_long(gs(R.string.this_app_needs_battery_optimization_whitelisting_or_it_will_not_work_well_please_reset_app_preferences));
                }
                return false;
            }
        }
        return true;
    }

    ////

    public static void updateStatusLine(String key, String value) {
        final Intent homeStatus = new Intent(Intents.HOME_STATUS_ACTION);
        homeStatus.putExtra(key, value);
        LocalBroadcastManager.getInstance(xdrip.getAppContext()).sendBroadcast(homeStatus);
        Log.d(TAG, "Home Status update: " + key + " / " + value);
    }

    private void checkBadSettings() {
        if (Pref.getBoolean("predictive_bg", false)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(gs(R.string.settings_issue));
            builder.setMessage(gs(R.string.you_have_an_old_experimental_glucose_prediction_setting_enabled__this_is_not_recommended_and_could_mess_things_up_badly__shall_i_disable_this_for_you));

            builder.setPositiveButton(gs(R.string.yes_please), (dialog, which) -> {
                dialog.dismiss();
                Pref.setBoolean("predictive_bg", false);
                toast(gs(R.string.setting_disabled_));
            });

            builder.setNegativeButton(R.string.no_i_really_know, (dialog, which) -> dialog.dismiss());

            AlertDialog alert = builder.create();
            alert.show();
        }
    }

    private void processIncomingBundle(Bundle bundle) {
        Log.d(TAG, "Processing incoming bundle");
        if (bundle != null) {
            if (bundle.getString(Home.HOME_FULL_WAKEUP) != null) {
                if (!JoH.isScreenOn()) {
                    final int timeout = 60000;
                    final PowerManager.WakeLock wl = JoH.getWakeLock("full-wakeup", timeout + 1000);
                    keepScreenOn();
                    final Timer t = new Timer();
                    t.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            dontKeepScreenOn();
                            JoH.releaseWakeLock(wl);
                            finish();
                        }
                    }, timeout);
                } else {
                    Log.d(TAG, "Screen is already on so not turning on");
                }
            } else if (bundle.getString(Home.SHOW_NOTIFICATION) != null) {
                final Intent notificationIntent = new Intent(this, Home.class);
                final int notification_id = bundle.getInt("notification_id");
                if ((notification_id == SENSOR_READY_ID) && (!Sensor.isActive() || BgReading.last() != null)) {
                    Log.e(TAG, "Sensor not in warm up period when notification due to fire");
                    return;
                }
                final PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                JoH.showNotification(bundle.getString(SHOW_NOTIFICATION), bundle.getString("notification_body"), pendingIntent, notification_id, true, true, true);
                UserError.Log.uel(TAG, bundle.getString("notification_body"));
            } else if (bundle.getString("numberIconTest") != null) {
                JoH.show_ok_dialog(
                        this,
                        gs(R.string.prepare_to_test),
                        gs(R.string.after_you_click_ok_look_for_a_number_icon_in_the_notification_area_if_you_see_123_then_the_test_has_succeeded__on_some_phones_this_test_will_crash_the_phone__after_30_seconds_we_will_shut_off_the_notification_if_your_phone_does_not_recover_after_this_then_hold_the_power_button_to_reboot_it),
                        () -> {
                            JoH.static_toast_long(gs(R.string.running_test_with_number_123));
                            NumberGraphic.testNotification("123");
                        });
            } else if (bundle.getString(Home.BLOOD_TEST_ACTION) != null) {
                Log.d(TAG, "BLOOD_TEST_ACTION");
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(gs(R.string.blood_test_action));
                builder.setMessage(gs(R.string.what_would_you_like_to_do));
                final String bt_uuid = bundle.getString(Home.BLOOD_TEST_ACTION + "2");
                if (bt_uuid != null) {
                    final BloodTest bt = BloodTest.byUUID(bt_uuid);
                    if (bt != null) {
                        builder.setNeutralButton(gs(R.string.nothing), (dialog, which) -> dialog.dismiss());

                        builder.setPositiveButton(gs(R.string.calibrate), (dialog, which) -> {
                            dialog.dismiss();
                            // TODO time should be absolute not relative!?!?
                            final long time_since = msSince(bt.timestamp);
                            Home.startHomeWithExtra(xdrip.getAppContext(), Home.BLUETOOTH_METER_CALIBRATION, BgGraphBuilder.unitized_string_static(bt.mgdl), Long.toString(time_since));
                            bt.addState(BloodTest.STATE_CALIBRATION);
                        });

                        builder.setNegativeButton(R.string.delete, (dialog, which) -> {
                            dialog.dismiss();
                            final AlertDialog.Builder builder1 = new AlertDialog.Builder(mActivity);
                            builder1.setTitle(gs(R.string.confirm_delete));
                            builder1.setMessage(gs(R.string.are_you_sure_you_want_to_delete_this_blood_test_result));
                            builder1.setPositiveButton(gs(R.string.yes_delete), (dialog12, which12) -> {
                                dialog12.dismiss();
                                bt.removeState(BloodTest.STATE_VALID);
                                NativeCalibrationPipe.removePendingCalibration((int) bt.mgdl);
                                staticRefreshBGCharts();
                                JoH.static_toast_short(gs(R.string.deleted));
                            });
                            builder1.setNegativeButton(gs(R.string.no), (dialog1, which1) -> dialog1.dismiss());
                            final AlertDialog alert = builder1.create();
                            alert.show();
                        });
                        final AlertDialog alert = builder.create();
                        alert.show();
                    } else {
                        JoH.static_toast_long(String.format(gs(R.string.could_not_find_blood_data), bt_uuid));
                    }
                }
            } else if (bundle.getString("confirmsnooze") != null) {
                switch (bundle.getString("confirmsnooze")) {
                    case "simpleconfirm":
                        DidYouCancelAlarm.dialog(this, AlertPlayer::defaultSnooze);
                        break;
                }
            }
        }
    }


    private static final int screenAlwaysOnFlags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON;

    private void keepScreenOn() {
        screen_forced_on = true;
        JoH.runOnUiThread(() -> getWindow().addFlags(screenAlwaysOnFlags));
    }

    private void dontKeepScreenOn() {
        JoH.runOnUiThread(() -> getWindow().clearFlags(screenAlwaysOnFlags));
        screen_forced_on = false;
    }


    public static void startHomeWithExtra(Context context, String extra, String text) {
        startHomeWithExtra(context, extra, text, "");
    }

    public static void startHomeWithExtra(Context context, String extra, String text, String even_more) {
        startHomeWithExtra(context, extra, text, even_more, "");
    }

    public static void startHomeWithExtra(Context context, String extra, String text, String even_more, String even_even_more) {
        Intent intent = new Intent(context, Home.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(extra, text);
        intent.putExtra(extra + "2", even_more);
        if (even_even_more.length() > 0) intent.putExtra(extra + "3", even_even_more);
        Log.e("xxxxx", "calling startActivity");
        context.startActivity(intent);
    }

    private boolean isAutoYPanEnabled() {
        // When true, xDrip auto-pans the Y-axis instead of extending the range for out-of-range readings.
        return Pref.getBoolean("auto_y_pan", true);
    }

    public void viewEventLog(MenuItem x) {
        startActivity(new Intent(this, EventLogActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("events", ""));
    }


    public static void toaststatic(final String msg) {
        nexttoast = msg;
        staticRefreshBGCharts();
    }

    public static void toaststaticnext(final String msg) {
        nexttoast = msg;
        UserError.Log.uel(TAG, "Home toast message next: " + msg);
    }

    public void toast(final String msg) {
        try {
            runOnUiThread(() -> Toast.makeText(mActivity, msg, Toast.LENGTH_LONG).show());
            Log.d(TAG, "toast: " + msg);
        } catch (Exception e) {
            Log.d(TAG, "Couldn't display toast: " + msg + " / " + e.toString());
        }
    }

    public static void toastStaticFromUI(final String msg) {
        try {
            Toast.makeText(mActivity, msg, Toast.LENGTH_LONG).show();
            Log.d(TAG, "toast: " + msg);
        } catch (Exception e) {
            toaststaticnext(msg);
            Log.d(TAG, "Couldn't display toast (rescheduling): " + msg + " / " + e.toString());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CODE_BATTERY_OPTIMIZATION) {
            staticRefreshBGCharts();
        }
    }

    @Override
    public String getMenuName() {
        return getString(R.string.home_screen);
    }


    public static void staticRefreshBGCharts() {
        staticRefreshBGCharts(false);
    }

    public static void staticRefreshBGChartsOnIdle() {
        Inevitable.task("refresh-home-charts", 1000, () -> staticRefreshBGCharts(false));
    }

    public static void staticRefreshBGCharts(boolean override) {
        reset_viewport = true;
        if (activityVisible || override) {
            Intent updateIntent = new Intent(Intents.ACTION_NEW_BG_ESTIMATE_NO_DATA);
            if (mActivity != null) {
                mActivity.sendBroadcast(updateIntent);
            }
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        // status line must only have current bwp/iob data
        nanoStatus.setRunning(true);
        expiryStatus.setRunning(true);

        if (BgGraphBuilder.isXLargeTablet(getApplicationContext())) {
            this.currentBgValueText.setTextSize(100);
            this.notificationText.setTextSize(40);
        } else if (BgGraphBuilder.isLargeTablet(getApplicationContext())) {
            this.currentBgValueText.setTextSize(70);
            this.notificationText.setTextSize(34); // 35 too big 33 works
        }

        _broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (intent.getAction() != null && intent.getAction().equals(Intent.ACTION_TIME_TICK)) {
                    if (msSince(lastDataTick) > SECOND_IN_MS * 30) {
                        Inevitable.task("process-time-tick", 300, () -> runOnUiThread(() -> {
                            updateCurrentBgInfo("time tick");
                        }));
                    }
                }
            }
        };
        newDataReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                lastDataTick = tsl();
                updateCurrentBgInfo("new data");
            }
        };

        registerReceiver(_broadcastReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));
        registerReceiver(newDataReceiver, new IntentFilter(Intents.ACTION_NEW_BG_ESTIMATE_NO_DATA));

        activityVisible = true;
        updateCurrentBgInfo("generic on resume");

        if (homeShelf.getDefaultFalse("source_wizard_auto")) {
            if (Experience.gotData()) {
                homeShelf.set("source_wizard", false);
                homeShelf.set("source_wizard_auto", false);
            }
        }
    }

    protected static class InterceptingGestureHandler extends GestureDetector {

        final Home context;
        final GestureDetector originalDetector;

        public InterceptingGestureHandler(Home context, GestureDetector originalDetector) {
            super(context, new GestureDetector.SimpleOnGestureListener() {
            });
            this.originalDetector = originalDetector;
            this.context = context;
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            if (ev.getAction() == MotionEvent.ACTION_MOVE) {
                if (JoH.quietratelimit("viewport-intercept",5)) {
                    UserError.Log.d("VIEWPORT", "Intercept gesture move event " + ev);
                }
                context.lastViewPortPan = tsl();
            }
            return originalDetector.onTouchEvent(ev);
        }
    }


    private void setupCharts(final String source) {

        UserError.Log.d(TAG, "VIEWPORT setupCharts source: " + source);

        bgGraphBuilder = new BgGraphBuilder(this);
        updateStuff = false;
        chart = findViewById(R.id.chart);

        if (BgGraphBuilder.isXLargeTablet(getApplicationContext())) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) chart.getLayoutParams();
            params.topMargin = 130;
            chart.setLayoutParams(params);
        }
        chart.setBackgroundColor(getCol(X.color_home_chart_background));
        chart.setZoomType(ZoomType.HORIZONTAL);
        chart.setMaxZoom(50f);

        // inject our gesture handler if it hasn't already been done
        try {
            val gestureDetector =  ChartTouchHandler.class.getDeclaredField("gestureDetector");
            gestureDetector.setAccessible(true);
            val chartTouchHandler = chart.getTouchHandler();
            val previewChartTouchHandler = previewChart.getTouchHandler();
            val activeDetector = (GestureDetector) gestureDetector.get(chartTouchHandler);
            val previewActiveDetector = (GestureDetector) gestureDetector.get(previewChartTouchHandler);
            if (!(activeDetector instanceof InterceptingGestureHandler)) {
                gestureDetector.set(chartTouchHandler, new InterceptingGestureHandler(this, activeDetector));
            } else {
                UserError.Log.d(TAG, "Already intercepting viewport");
            }
            if (!(previewActiveDetector instanceof InterceptingGestureHandler)) {
                gestureDetector.set(previewChartTouchHandler, new InterceptingGestureHandler(this, previewActiveDetector));
            }
        } catch (NullPointerException | NoSuchFieldException | IllegalAccessException | ClassCastException e) {
            UserError.Log.d(TAG, "Exception injecting touch handler: " + e);
        }

        chart.getChartData().setAxisXTop(null);

        previewChart = findViewById(R.id.chart_preview);

        chart.setLineChartData(bgGraphBuilder.lineData());
        chart.setOnValueTouchListener(bgGraphBuilder.getOnValueSelectTooltipListener(mActivity));

        previewChart.setBackgroundColor(getCol(X.color_home_chart_background));
        previewChart.setZoomType(ZoomType.HORIZONTAL);
        previewChart.setLineChartData(bgGraphBuilder.previewLineData(chart.getLineChartData()));
        previewChart.setViewportCalculationEnabled(true);
        previewChart.setViewportChangeListener(new PreviewChartViewportListener());
        chart.setViewportCalculationEnabled(true);
        chart.setViewportChangeListener(new MainChartViewPortListener());
        updateStuff = true;
        //setViewport();

        if (small_height) {
            previewChart.setVisibility(View.GONE);
            // TODO this doesn't look right, fix to some specific hours?
            // quick test
            Viewport moveViewPort = new Viewport(chart.getMaximumViewport());
            float tempwidth = (float) moveViewPort.width() / 4;
            holdViewport.left = moveViewPort.right - tempwidth;
            holdViewport.right = moveViewPort.right + (moveViewPort.width() / 24);
            if (!isAutoYPanEnabled()) {
                holdViewport.top = moveViewPort.top;
                holdViewport.bottom = moveViewPort.bottom;
            } else {
                Viewport v = bgGraphBuilder.computeYViewport();
                holdViewport.top = v.top;
                holdViewport.bottom = v.bottom;
            }
            chart.setCurrentViewport(holdViewport);
            previewChart.setCurrentViewport(holdViewport);
            UserError.Log.e(TAG, "SMALL HEIGHT VIEWPORT WARNING");
        } else {
            if (homeShelf.get("time_buttons") || homeShelf.get("time_locked_always")) {
                final long which_hour = PersistentStore.getLong("home-locked-hours"); // TODO use which time method
                if (which_hour > 0) {
                    hours = which_hour;
                } else {
                    hours = DEFAULT_CHART_HOURS;
                }
                setHoursViewPort(source);
            } else {
                hours = DEFAULT_CHART_HOURS;
                setHoursViewPort(source);
            }
            previewChart.setVisibility(homeShelf.get("chart_preview") ? View.VISIBLE : View.GONE);
        }
    }


    @Override
    public void onPause() {
        activityVisible = false;
        super.onPause();
        nanoStatus.setRunning(false);
        expiryStatus.setRunning(false);
        if (_broadcastReceiver != null) {
            try {
                unregisterReceiver(_broadcastReceiver);
            } catch (IllegalArgumentException e) {
                UserError.Log.e(TAG, "_broadcast_receiver not registered", e);
            }
        }
        if (newDataReceiver != null) {
            try {
                unregisterReceiver(newDataReceiver);
            } catch (IllegalArgumentException e) {
                UserError.Log.e(TAG, "newDataReceiver not registered", e);
            }
        }
    }


    public static boolean get_engineering_mode() {
        return Pref.getBooleanDefaultFalse("engineering_mode");
    }

    public static boolean get_holo() {
        return Home.is_holo;
    }

    private void setHoursViewPort(final String source) {

        boolean exactHoursSpecified = false;

        final Viewport maxViewPort = new Viewport(chart.getMaximumViewport());

        switch (source) {
            case "":
            case "timeButtonClick":
            case "timeButtonLongClick":
                exactHoursSpecified = true;
                break;

            case "new data":
            case "time tick":
                if (msSince(lastViewPortPan) < 45 * SECOND_IN_MS) {
                    UserError.Log.d(TAG, "Skipping VIEWPORT adjustment as panning and data just arrived: " + holdViewport.toString());
                    if (!isAutoYPanEnabled()) {
                        holdViewport.top = maxViewPort.top;
                        holdViewport.bottom = maxViewPort.bottom;
                    }
                    chart.setCurrentViewport(holdViewport); // reuse existing
                    return;
                }
                break;
        }

        // always show at least the ideal number of hours
        float ideal_hours_to_show = DEFAULT_CHART_HOURS;
        // ... and rescale to accommodate predictions if not locked
        if (! homeShelf.get("time_locked_always")) {
            ideal_hours_to_show += bgGraphBuilder.getPredictivehours();
        }
        float hours_to_show =  exactHoursSpecified ? hours : Math.max(hours, ideal_hours_to_show);

        UserError.Log.d(TAG, "VIEWPORT " + source + " moveviewport in setHours: asked " + hours + " vs auto " + ideal_hours_to_show + " = " + hours_to_show + " full chart width: " + bgGraphBuilder.hoursShownOnChart());

        double hour_width = maxViewPort.width() / bgGraphBuilder.hoursShownOnChart();
        holdViewport.left = maxViewPort.right - hour_width * hours_to_show;
        holdViewport.right = maxViewPort.right;
        if (!isAutoYPanEnabled()) {
            holdViewport.top = maxViewPort.top;
            holdViewport.bottom = maxViewPort.bottom;
        }

        // if locked, center display on current bg values, not predictions
        if (homeShelf.get("time_locked_always")) {
            holdViewport.left -= hour_width * bgGraphBuilder.getPredictivehours();
            holdViewport.right -= hour_width * bgGraphBuilder.getPredictivehours();
        }

        if (d) {
            UserError.Log.d(TAG, "HOLD VIEWPORT " + holdViewport);
            UserError.Log.d(TAG, "MAX VIEWPORT " + maxViewPort);
        }

        if (isAutoYPanEnabled()) {
            Viewport v = bgGraphBuilder.computeYViewport();
            holdViewport.top = v.top;
            holdViewport.bottom = v.bottom;
        }
        chart.setCurrentViewport(holdViewport);

    }

    private void updateCurrentBgInfo(final String source) {
        Log.d(TAG, "updateCurrentBgInfo from: " + source);

        if (!activityVisible) {
            Log.d(TAG, "Display not visible - not updating chart");
            return;
        }
        if (reset_viewport) {
            reset_viewport = false;
            //  holdViewport.set(0, 0, 0, 0);
            // if (chart != null) chart.setZoomType(ZoomType.HORIZONTAL);
            // TODO above reset viewport thing seems defunct now
        }
        setupCharts(source);
        initializeGraphicTrendArrow();
        final TextView notificationText = (TextView) findViewById(R.id.notices);
        final TextView lowPredictText = (TextView) findViewById(R.id.lowpredict);
        if (BgGraphBuilder.isXLargeTablet(getApplicationContext())) {
            notificationText.setTextSize(40);
            lowPredictText.setTextSize(30);
        }
        notificationText.setText("");
        notificationText.setTextColor(Color.RED);

        final DexCollectionType collector = DexCollectionType.getDexCollectionType();
        // TODO unify code using DexCollectionType methods

        isG5Share = CollectionServiceStarter.isBTG5(getApplicationContext());
        alreadyDisplayedBgInfoCommon = false; // reset flag
        if (isG5Share) {
            updateCurrentBgInfoCommon(collector, notificationText);
        }
        if (collector.isPassive()) {
            displayCurrentInfo();
            Inevitable.task("home-notifications-start", 5000, Notifications::start);
            // TODO add dexcollectiontype set handling for these
        }
        if (collector.equals(DexCollectionType.Disabled)) {
            notificationText.append(getString(R.string.__data_source_disabled));
        }
        if (Pref.getLong("alerts_disabled_until", 0) > new Date().getTime()) {
            notificationText.append("\n " + getString(R.string.all_alerts_currently_disabled));
        } else if (Pref.getLong("low_alerts_disabled_until", 0) > new Date().getTime()
                &&
                Pref.getLong("high_alerts_disabled_until", 0) > new Date().getTime()) {
            notificationText.append("\n " + getString(R.string.low_and_high_alerts_currently_disabled));
        } else if (Pref.getLong("low_alerts_disabled_until", 0) > new Date().getTime()) {
            notificationText.append("\n " + getString(R.string.low_alerts_currently_disabled));
        } else if (Pref.getLong("high_alerts_disabled_until", 0) > new Date().getTime()) {
            notificationText.append("\n " + getString(R.string.high_alerts_currently_disabled));
        }
        NavigationDrawerFragment navigationDrawerFragment = (NavigationDrawerFragment) getFragmentManager().findFragmentById(R.id.navigation_drawer);

        // DEBUG ONLY
        if ((BgGraphBuilder.last_noise > 0) && (Pref.getBoolean("show_noise_workings", false))) {
            notificationText.append("\nSensor Noise: " + JoH.qs(BgGraphBuilder.last_noise, 1));
            if ((BgGraphBuilder.best_bg_estimate > 0) && (BgGraphBuilder.last_bg_estimate > 0)) {
                final double estimated_delta = BgGraphBuilder.best_bg_estimate - BgGraphBuilder.last_bg_estimate;

                // TODO pull from BestGlucose? Check Slope + arrow etc TODO Original slope needs fixing when using plugin
                // notificationText.append("\nBG Original: " + bgGraphBuilder.unitized_string(BgReading.lastNoSenssor().calculated_value)
                try {
                    notificationText.append("\nBG Original: " + bgGraphBuilder.unitized_string(BgGraphBuilder.original_value)
                            + " \u0394 " + bgGraphBuilder.unitizedDeltaString(false, true, true)
                            + " " + BgReading.lastNoSenssor().slopeArrow());

                    notificationText.append("\nBG Estimate: " + bgGraphBuilder.unitized_string(BgGraphBuilder.best_bg_estimate)
                            + " \u0394 " + bgGraphBuilder.unitizedDeltaStringRaw(false, true, estimated_delta)
                            + " " + BgReading.slopeToArrowSymbol(estimated_delta / (BgGraphBuilder.DEXCOM_PERIOD / 60000)));
                } catch (NullPointerException e) {
                    //
                }
            }
        }

        // TODO we need to consider noise level?
        // when to raise the alarm
        lowPredictText.setText("");
        lowPredictText.setVisibility(View.INVISIBLE);
        if (BgGraphBuilder.low_occurs_at > 0) {

            double low_predicted_alarm_minutes;

            low_predicted_alarm_minutes = JoH.tolerantParseDouble(Pref.getString("low_predict_alarm_level", "50"), 50d);

            // TODO use tsl()

            final double now = JoH.ts();
            final double predicted_low_in_mins = (BgGraphBuilder.low_occurs_at - now) / 60000;

            if (predicted_low_in_mins > 1) {
                lowPredictText.append(getString(R.string.low_predicted) + "\n" + getString(R.string.in) + ": " + (int) predicted_low_in_mins + getString(R.string.space_mins));
                if (predicted_low_in_mins < low_predicted_alarm_minutes) {
                    lowPredictText.setTextColor(getCol(X.color_low_predicted_critical_note)); // low front getting too close!
                } else {
                    final double previous_predicted_low_in_mins = (BgGraphBuilder.previous_low_occurs_at - now) / 60000;
                    if ((BgGraphBuilder.previous_low_occurs_at > 0) && ((previous_predicted_low_in_mins + 5) < predicted_low_in_mins)) {
                        lowPredictText.setTextColor(Color.GREEN); // low front is getting further away
                    } else {
                        lowPredictText.setTextColor(Color.YELLOW); // low front is getting nearer!
                    }
                }
                lowPredictText.setVisibility(View.VISIBLE);
            }
            BgGraphBuilder.previous_low_occurs_at = BgGraphBuilder.low_occurs_at;
        }

        if (navigationDrawerFragment == null) Log.e("Runtime", "navigationdrawerfragment is null");

        try {
            navigationDrawerFragment.setUp(R.id.navigation_drawer, (DrawerLayout) findViewById(R.id.drawer_layout), getString(R.string.home_screen), this);
        } catch (Exception e) {
            Log.e("Runtime", "Exception with navigrationdrawerfragment: " + e.toString());
        }
        if (nexttoast != null) {
            toast(nexttoast);
            nexttoast = null;
        }

	    //if (isG5Share) showcasemenu(SHOWCASE_G5FIRMWARE); // nov 2016 firmware warning resolved 15/12/2016
        //showcasemenu(1); // 3 dot menu
    }

    private void updateCurrentBgInfoCommon(DexCollectionType collector, TextView notificationText) {
        if (alreadyDisplayedBgInfoCommon) return; // with bluetooth and wifi, skip second time
        alreadyDisplayedBgInfoCommon = true;

        boolean isSensorActive = Sensor.isActive();

        // automagically start an xDrip sensor session if G5 transmitter already has active sensor
        if (!isSensorActive && Ob1G5CollectionService.isG5SensorStarted() && !Sensor.stoppedRecently()) {
            JoH.static_toast_long(getString(R.string.auto_starting_sensor));
            Sensor.create(tsl() - HOUR_IN_MS * 3);
            isSensorActive = Sensor.isActive();
        }

        if (!isSensorActive) {
            // Define a variable (notConnectedToG6Yet) that is only true if Native G6 is chosen, but, transmitter days is unknown or not synced yet.
            boolean notConnectedToG6Yet = DexCollectionType.getDexCollectionType() == DexcomG5 && Pref.getBooleanDefaultFalse("ob1_g5_use_transmitter_alg") && Pref.getBooleanDefaultFalse("using_g6") && (DexTimeKeeper.getTransmitterAgeInDays(getTransmitterID()) == -1 || !DexSyncKeeper.isReady(getTransmitterID()));
            if (notConnectedToG6Yet || shortTxId()) { // Only if G6 has been selected and transmitter is not synced yet, or if G7 has been selected.
                notificationText.setText(R.string.wait_to_connect);
            } else { // Only if G6 is not selected or G6 transmitter is synced.
                notificationText.setText(R.string.now_start_your_sensor);
            }

            displayCurrentInfo(); // update the display even with no sensor to clear old data

            if ((dialog == null) || (!dialog.isShowing())) {
                if (!Experience.gotData() && Experience.backupAvailable() && JoH.ratelimit("restore-backup-prompt", 10)) {

                } else {
                    if (!Experience.gotData() && !QuickSettingsDialogs.isDialogShowing() && !notConnectedToG6Yet && JoH.ratelimit("start-sensor_prompt", 20)) {
                        // Show the start sensor prompt only if G6 is not selected or the G6 transmitter is synchronized.
                        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                        final Context context = this;
                        builder.setTitle(getString(R.string.start_sensor) + "?");
                        builder.setMessage(String.format(gs(R.string.start_sensor_confirmation), DexCollectionType.getBestCollectorHardwareName()));
                        builder.setNegativeButton(gs(R.string.change_settings), (dialog, which) -> {
                            dialog.dismiss();
                            startActivity(new Intent(context, Preferences.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        });
                        builder.setPositiveButton(R.string.start_sensor, (dialog, which) -> {
                            dialog.dismiss();
                            startActivity(new Intent(context, StartNewSensor.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        });
                        dialog = builder.create();
                        dialog.show();
                    }
                }
            }
            return;
        }

        if (!BgReading.doWeHaveRecentUsableData()) {
            // TODO check null handling?
            long startedAt = Sensor.currentSensor().started_at;
            long computedStartedAt = SensorDays.get().getStart();
            if (computedStartedAt > 0) {
                UserError.Log.d(TAG, "computedStartedAt " + JoH.dateTimeText(computedStartedAt));
                startedAt = Math.min(computedStartedAt, startedAt);
            }
            final long warmUpMs = SensorDays.get().getWarmupMs();
            final long now = tsl();
            if (startedAt + warmUpMs > now) {
                double waitTime = (startedAt + warmUpMs - now) / (double)MINUTE_IN_MS;
                // TODO better resource format string
                notificationText.setText(getString(R.string.please_wait_while_sensor_warms_up) + JoH.qs(waitTime, 0) + getString(R.string.minutes_with_bracket));
                showUncalibratedSlope();
                return;
            }
        }

        // TODO this logic needed a rework even a year ago, now its a lot more confused with the additional complexity of native mode
        if (Ob1G5CollectionService.isG5ActiveButUnknownState() && Calibration.latestValid(2).size() < 2) {
            // TODO use format string
            notificationText.setText(String.format(gs(R.string.state_not_currently_known), (Ob1G5StateMachine.usingG6() ? (shortTxId() ? "G7" : "G6") : "G5")));
            showUncalibratedSlope();
        } else {

            if (Ob1G5CollectionService.isG5WarmingUp() || (Ob1G5CollectionService.isPendingStart())) {
                notificationText.setText(R.string.sensor_is_still_warming_up_please_wait);
                showUncalibratedSlope();
            } else {
                final int calculatedBgReadingsCount = BgReading.latest(3).size();
                if ((calculatedBgReadingsCount > 2) || (Ob1G5CollectionService.onlyUsingNativeMode() && BgReading.latest(1).size() > 0)) {
                    // TODO potential to calibrate off stale data here
                    final List<Calibration> calibrations = Calibration.latestValid(2);
                    if (((calibrations != null) && (calibrations.size() > 1)) || Ob1G5CollectionService.onlyUsingNativeMode()) {
                        if (calibrations.size() > 1) {
                            if (calibrations.get(0).possible_bad != null && calibrations.get(0).possible_bad == true && calibrations.get(1).possible_bad != null && calibrations.get(1).possible_bad != true) {
                                notificationText.setText(R.string.possible_bad_calibration);
                            }
                        }
                        displayCurrentInfo();
                        if (screen_forced_on) dontKeepScreenOn();
                    } else {
                        if (BgReading.isDataSuitableForDoubleCalibration()) {
                            notificationText.setText(R.string.please_enter_two_calibrations_to_get_started);
                            showUncalibratedSlope();
                            Log.d(TAG, "Asking for calibration A: Uncalculated BG readings: " + BgReading.latest(2).size() + " / Calibrations size: " + calibrations.size());
                            promptForCalibration();
                            dontKeepScreenOn();
                        } else {
                            notificationText.setText(R.string.unusual_calibration_waiting);
                            if (Ob1G5CollectionService.isProvidingNativeGlucoseData()) {
                                displayCurrentInfo();
                                if (screen_forced_on) dontKeepScreenOn();
                            }
                        }
                    }
                } else {
                    UserError.Log.d(TAG, "NOT ENOUGH CALCULATED READINGS: " + calculatedBgReadingsCount);
                    if (!BgReading.isDataSuitableForDoubleCalibration() && (!Ob1G5CollectionService.usingNativeMode() || Ob1G5CollectionService.fallbackToXdripAlgorithm())) {
                        notificationText.setText(R.string.please_wait_need_two_readings_first);
                        showInitialStatusHelper();
                    } else {
                        List<Calibration> calibrations = Calibration.latest(2);
                        if (calibrations.size() < 2) {
                            if (BgReading.isDataSuitableForDoubleCalibration() || Ob1G5CollectionService.isG5WantingInitialCalibration()) {
                                notificationText.setText(R.string.please_enter_two_calibrations_to_get_started);
                                showUncalibratedSlope();
                                Log.d(TAG, "Asking for calibration B: Uncalculated BG readings: " + BgReading.latestUnCalculated(2).size() + " / Calibrations size: " + calibrations.size() + " quality: " + BgReading.isDataSuitableForDoubleCalibration());
                                if (!Ob1G5CollectionService.isPendingCalibration()) {
                                    promptForCalibration();
                                } else {
                                    notificationText.setText(R.string.transmitter_waiting_for_calibration);
                                }
                            } else {
                                if (!Ob1G5CollectionService.isG5SensorStarted()) {
                                    notificationText.setText(R.string.sensor_not_started);
                                    // TODO do we stop xDrip sensor session here?
                                } else {
                                    notificationText.setText(R.string.not_getting_readings);
                                }
                            }
                            dontKeepScreenOn();
                        }
                    }
                }
            }
        }
    }

    private synchronized void showInitialStatusHelper() {
        if (checkBatteryOptimization()) {
            initialDataQuality = ProcessInitialDataQuality.getInitialDataQuality(); // update
            if ((helper_dialog != null) && (helper_dialog.isShowing())) helper_dialog.dismiss();
            if ((status_helper_dialog != null) && (status_helper_dialog.isShowing())) {
                if (initial_status_binding != null)
                    initial_status_binding.setIdq(initialDataQuality); // update data
                return;
            }

            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.collecting_initial_readings);
            initial_status_binding = PopupInitialStatusHelperBinding.inflate(getLayoutInflater());
            initial_status_binding.setIdq(initialDataQuality);
            if (microStatus == null) microStatus = new MicroStatusImpl();
            initial_status_binding.setMs(microStatus);
            initial_status_binding.setPrefs(new PrefsViewImpl());
            builder.setView(initial_status_binding.getRoot());
            status_helper_dialog = builder.create();
            status_helper_dialog.setCanceledOnTouchOutside(true);
            try {
                status_helper_dialog.show();
            } catch (Exception e) {
                UserError.Log.e(TAG, "Could not display calibration prompt helper: " + e);
            }
            keepScreenOn();
        }
    }

    private synchronized void promptForCalibration() {
        if ((status_helper_dialog != null) && (status_helper_dialog.isShowing()))
            status_helper_dialog.dismiss();
        if ((helper_dialog != null) && (helper_dialog.isShowing())) return;
        if (JoH.ratelimit("calibrate-sensor_prompt", 10)) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            final Context context = this;
            builder.setTitle(gs(R.string.calibrate_sensor));
            builder.setMessage(gs(R.string.we_have_some_readings__next_we_need_the_first_calibration_blood_test__ready_to_calibrate_now));
            builder.setNegativeButton(gs(R.string.no), (dialog, which) -> {
                dialog.dismiss();
                helper_dialog = null;
            });
            builder.setPositiveButton(gs(R.string.calibrate), (dialog, which) -> {
                dialog.dismiss();
                helper_dialog = null;
                startActivity(new Intent(context, DoubleCalibrationActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            });
            helper_dialog = builder.create();
            try {
                helper_dialog.show();
            } catch (Exception e) {
                UserError.Log.e(TAG, "Could not display calibration prompt helper: " + e);
            }
            if (Pref.getBooleanDefaultFalse("play_sound_for_initial_calibration")) {
                if (JoH.ratelimit("play_calibration_sound", 280)) {
                    JoH.playSoundUri(JoH.getResourceURI(R.raw.reminder_default_notification));
                }
            }
            keepScreenOn();
        }
    }

    private void showUncalibratedSlope() {
        currentBgValueText.setText(BgReading.getSlopeArrowSymbolBeforeCalibration());
        currentBgValueText.setTextColor(getCol(X.color_predictive));
    }

    private void displayCurrentInfo() {
        DecimalFormat df = new DecimalFormat("#");
        df.setMaximumFractionDigits(0);

        if (DexCollectionType.hasBattery()) {
            final int bridgeBattery = Pref.getInt("bridge_battery", 0);

            if (bridgeBattery < 1) {
                //dexbridgeBattery.setText(R.string.waiting_for_packet);
                dexbridgeBattery.setVisibility(View.INVISIBLE);
            } else {
                    dexbridgeBattery.setText(getString(R.string.bridge_battery) + ": " + bridgeBattery + ((bridgeBattery < 200) ? "%" : "mV"));
            }
            if (bridgeBattery < 50) dexbridgeBattery.setTextColor(Color.YELLOW);
            if (bridgeBattery < 25) dexbridgeBattery.setTextColor(Color.RED);
            else dexbridgeBattery.setTextColor(Color.GREEN);
            dexbridgeBattery.setVisibility(View.VISIBLE);
        } else {
            dexbridgeBattery.setVisibility(View.INVISIBLE);
        }

            uploaderBattery.setVisibility(View.INVISIBLE);

        if (!Pref.getBoolean("display_bridge_battery", true)) {
            dexbridgeBattery.setVisibility(View.INVISIBLE);
            uploaderBattery.setVisibility(View.INVISIBLE);
        }

            sensorAge.setVisibility(View.GONE);
        if (blockTouches) {
            sensorAge.setText("SCANNING.. DISPLAY LOCKED!");
            sensorAge.setVisibility(View.VISIBLE);
            sensorAge.setTextColor(Color.GREEN);
        }

        if ((currentBgValueText.getPaintFlags() & Paint.STRIKE_THRU_TEXT_FLAG) > 0) {
            currentBgValueText.setPaintFlags(currentBgValueText.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            dexbridgeBattery.setPaintFlags(dexbridgeBattery.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
        }
        final BgReading lastBgReading = BgReading.lastNoSenssor();
        boolean predictive = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("predictive_bg", false);
        if (lastBgReading != null) {

            // detect broken data from G5 or other sources
            if ((lastBgReading.raw_data != 0) && (lastBgReading.raw_data * 2 == lastBgReading.filtered_data)) {
                if (JoH.ratelimit("g5-corrupt-data-warning", 1200)) {
                    final String msg = getString(R.string.transmitter_maybe_dead) + lastBgReading.raw_data;
                    toaststaticnext(msg);
                }
            }

            if (!displayCurrentInfoFromReading(lastBgReading, predictive)) {
                currentBgValueText.setText("");
                display_delta = "";
                if (itr != null) {
                    itr.update(null); // hide graphical arrow
                }
            }
        } else {
            currentBgValueText.setText(""); // clear value if no reading
            display_delta = "";
        }
    }

    // TODO consider moving this out of Home
    public static long stale_data_millis() {
        return (60000 * 11);
    }

    private boolean displayCurrentInfoFromReading(BgReading lastBgReading, boolean predictive) {
        double estimate = 0;
        double estimated_delta = 0;
        if (lastBgReading == null) return false;
        final BestGlucose.DisplayGlucose dg = BestGlucose.getDisplayGlucose();
        if (dg == null) return false;
        //String slope_arrow = lastBgReading.slopeArrow();
        String slope_arrow = dg.delta_arrow;
        String extrastring = "";
        boolean hide_slope = false;
        // when stale
        if ((new Date().getTime()) - stale_data_millis() - lastBgReading.timestamp > 0) { // TODO fix
            notificationText.setText(R.string.signal_missed);
            if (!predictive) {
                //  estimate = lastBgReading.calculated_value;
                estimate = dg.mgdl;
            } else {
                estimate = BgReading.estimated_bg(lastBgReading.timestamp + (6000 * 7));
            }
            currentBgValueText.setText(bgGraphBuilder.unitized_string(estimate));
            currentBgValueText.setPaintFlags(currentBgValueText.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            dexbridgeBattery.setPaintFlags(dexbridgeBattery.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            hide_slope = true;
        } else {
            // not stale
            if (notificationText.getText().length() == 0) {
                notificationText.setTextColor(Color.WHITE);
            }
            boolean bg_from_filtered = Pref.getBoolean("bg_from_filtered", false);
            if (!predictive) {
                //estimate = lastBgReading.calculated_value; // normal
                estimate = dg.mgdl;
                currentBgValueText.setTypeface(null, Typeface.NORMAL);

                // if noise has settled down then switch off filtered mode
                if ((bg_from_filtered) && (BgGraphBuilder.last_noise < BgGraphBuilder.NOISE_FORGIVE) && (Pref.getBoolean("bg_compensate_noise", false))) {
                    bg_from_filtered = false;
                    Pref.setBoolean("bg_from_filtered", false);
                }

                // TODO this should be partially already be covered by dg - recheck
                if (BestGlucose.compensateNoise()) {
                    estimate = BgGraphBuilder.best_bg_estimate; // this maybe needs scaling based on noise intensity
                    estimated_delta = BgGraphBuilder.best_bg_estimate - BgGraphBuilder.last_bg_estimate;
                    slope_arrow = BgReading.slopeToArrowSymbol(estimated_delta / (BgGraphBuilder.DEXCOM_PERIOD / 60000)); // delta by minute
                    currentBgValueText.setTypeface(null, Typeface.ITALIC);
                    extrastring = "\u26A0"; // warning symbol !

                    if ((BgGraphBuilder.last_noise > BgGraphBuilder.NOISE_HIGH) && (DexCollectionType.hasFiltered())) {
                        bg_from_filtered = true; // force filtered mode
                    }
                }

                if (bg_from_filtered) {
                    currentBgValueText.setPaintFlags(currentBgValueText.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
                    estimate = lastBgReading.filtered_calculated_value;
                } else {
                    currentBgValueText.setPaintFlags(currentBgValueText.getPaintFlags() & ~Paint.UNDERLINE_TEXT_FLAG);
                }
                String stringEstimate = bgGraphBuilder.unitized_string(estimate);
                if ((lastBgReading.hide_slope) || (bg_from_filtered)) {
                    slope_arrow = "";
                }
                currentBgValueText.setText(stringEstimate + (itr == null ? " " + slope_arrow : ""));
            } else {
                // old depreciated prediction
                estimate = BgReading.activePrediction();
                String stringEstimate = bgGraphBuilder.unitized_string(estimate);
                currentBgValueText.setText(stringEstimate + (itr == null ? " " + BgReading.activeSlopeArrow() : ""));
            }
            if (extrastring.length() > 0)
                currentBgValueText.setText(extrastring + currentBgValueText.getText());
        }
        int minutes = (int) (System.currentTimeMillis() - lastBgReading.timestamp) / (60 * 1000);

        if ((!small_width) || (notificationText.length() > 0)) notificationText.append("\n");
        if (!small_width) {
            final String fmt = getString(R.string.minutes_ago);
            notificationText.append(MessageFormat.format(fmt, minutes));
        } else {
            // small screen
            notificationText.append(minutes + getString(R.string.space_mins));
            currentBgValueText.setPadding(0, 0, 0, 0);
        }

        if (small_screen) {
            if (currentBgValueText.getText().length() > 4)
                currentBgValueText.setTextSize(25);
        }

        if (itr != null) {
            itr.update((lastBgReading.hide_slope || hide_slope) ? null : dg.slope * 60000);
        }

        // do we actually need to do this query here if we again do it in unitizedDeltaString
        List<BgReading> bgReadingList = BgReading.latest(2, false);
        if (bgReadingList != null && bgReadingList.size() == 2) {
            // same logic as in xDripWidget (refactor that to BGReadings to avoid redundancy / later inconsistencies)?

            //display_delta = bgGraphBuilder.unitizedDeltaString(true, true, is_follower);
            display_delta = dg.unitized_delta;
            if (BestGlucose.compensateNoise()) {
                //final double estimated_delta = BgGraphBuilder.best_bg_estimate - BgGraphBuilder.last_bg_estimate;
                display_delta = bgGraphBuilder.unitizedDeltaStringRaw(true, true, estimated_delta);
                addDisplayDelta();
                if (!Pref.getBoolean("show_noise_workings", false)) {
                    notificationText.append("\n" + String.format(gs(R.string.noise_workings_noise), bgGraphBuilder.noiseString(BgGraphBuilder.last_noise)));
                }
            } else {
                addDisplayDelta();
            }
        }
        if (bgGraphBuilder.unitized(estimate) <= bgGraphBuilder.lowMark) {
            currentBgValueText.setTextColor(getCol(ColorCache.X.color_low_bg_values));
        } else if (bgGraphBuilder.unitized(estimate) >= bgGraphBuilder.highMark) {
            currentBgValueText.setTextColor(getCol(ColorCache.X.color_high_bg_values));
        } else {
            currentBgValueText.setTextColor(getCol(ColorCache.X.color_inrange_bg_values));
        }

        // TODO this should be made more efficient probably
        if (Pref.getBooleanDefaultFalse("display_glucose_from_plugin") && (PluggableCalibration.getCalibrationPluginFromPreferences() != null)) {
            currentBgValueText.setText(getString(R.string.p_in_circle) + currentBgValueText.getText()); // adds warning P in circle icon
        }

        return true;
    }

    private void addDisplayDelta() {
        if (BgGraphBuilder.isXLargeTablet(getApplicationContext())) {
	        notificationText.append("  ");
	        notificationText.append(display_delta);
        }
        else {
            notificationText.append("\n");
            notificationText.append(display_delta);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (blockTouches) return true;
        try {
            return super.dispatchTouchEvent(ev);
        } catch (Exception e) {
            // !?
            return false;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_home, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onNewIntent(Intent intent) {
	    super.onNewIntent(intent);
	    Bundle bundle = intent.getExtras();
        processIncomingBundle(bundle);
    }

    public void settingsSDcardExport(MenuItem myitem) {
        startActivity(new Intent(getApplicationContext(), SdcardImportExport.class));
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
                if (JoH.quietratelimit("button-press", 5)) {
                    if (Pref.getBooleanDefaultFalse("buttons_silence_alert")) {
                        final ActiveBgAlert activeBgAlert = ActiveBgAlert.getOnly();
                        if (activeBgAlert != null) {
                            AlertPlayer.getPlayer().Snooze(xdrip.getAppContext(), -1);
                            JoH.static_toast_long(getString(R.string.snoozing_due_button_press));
                            UserError.Log.ueh(TAG, "Snoozing alert due to volume button press");
                        } else {
                            if (d) UserError.Log.d(TAG, "no active alert to snooze");
                        }
                    } else {
                        if (d) UserError.Log.d(TAG, "No action as preference is disabled");
                    }
                }
                break;
        }
        if (d) Log.d(TAG, "Keydown event: " + keyCode + " event: " + event.toString());
        return super.onKeyDown(keyCode, event);
    }

    public void initializeGraphicTrendArrow() {
        if (homeShelf.get("graphic_trend_arrow")) {
            if (itr == null) {
                itr = TrendArrowFactory.create(findViewById(R.id.trendArrow));
            }
        } else {
            if (itr != null) {
                itr.update(null);
                itr = null;
            }
        }
    }

    public static double convertToMgDlIfMmol(double value) {
        if (!Pref.getString("units", "mgdl").equals("mgdl")) {
            return value * com.eveningoutpost.dexdrip.utilitymodels.Constants.MMOLL_TO_MGDL;
        } else {
            return value; // no conversion needed
        }
    }

    public static void snackBar(int buttonString, String message, View.OnClickListener mOnClickListener, Activity activity) {

        // Store Snackbar in a variable
        Snackbar snackbar = Snackbar.make(
                        activity.findViewById(android.R.id.content),
                        message, Snackbar.LENGTH_LONG)
                .setAction(buttonString, mOnClickListener);

        // Disable ALL CAPS on the action button
        Button b = snackbar.getView().findViewById(com.google.android.material.R.id.snackbar_action);
        if (b != null) b.setAllCaps(false);

        // Maximum number of lines to wrap into
        TextView t = snackbar.getView().findViewById(com.google.android.material.R.id.snackbar_text);
        if (t != null) t.setMaxLines(5);

        snackbar.show();
    }

    // classes
    private class MainChartViewPortListener implements ViewportChangeListener {
        @Override
        public synchronized void onViewportChanged(Viewport newViewport) {
            if (!updatingPreviewViewport) {
                updatingChartViewport = true;
                previewChart.setZoomType(ZoomType.HORIZONTAL);
                previewChart.setCurrentViewport(newViewport);
                updatingChartViewport = false;
            }
        }
    }

    public class PreviewChartViewportListener implements ViewportChangeListener {
        @Override
        public synchronized void onViewportChanged(Viewport newViewport) {
            if (!updatingChartViewport) {
                updatingPreviewViewport = true;
                chart.setZoomType(ZoomType.HORIZONTAL);
                chart.setCurrentViewport(newViewport);
                updatingPreviewViewport = false;
            }
            if (updateStuff) {
                holdViewport.set(newViewport.left, newViewport.top, newViewport.right, newViewport.bottom);
                if (d) {
                    if (quietratelimit("viewport-updated-by-change", 1)) {
                        UserError.Log.d(TAG, "VIEWPORT UPDATED VIA CHANGE: " + holdViewport);
                    }
                }
            }
        }
    }

    public void showArrowConfigurator(View v, boolean show, boolean refresh) {

        if (homeShelf.get("arrow_configurator") && show) {
            show = false; // hide on second click of spanner
        }

        homeShelf.set("arrow_configurator", show);
        try {
            final View configurator = itr.getConfigurator();
            try {
                ((ViewGroup) configurator.getParent()).removeView(configurator);
            } catch (NullPointerException e) {
                // not yet assigned
            }
            final ViewGroup parent = (ViewGroup) v.getParent().getParent();
            parent.removeView(configurator);
            if (show && configurator != null) {
                parent.addView(configurator);
            }
        } catch (NullPointerException e) {
            // not instantiated
        }
        if (refresh) {
            staticRefreshBGCharts();
        }
    }

    public int arrowSelectionCurrent() {
        return TrendArrowFactory.getArrayPosition();
    }

    public void arrowSelectionChanged(AdapterView<?> parentView, View selectedItemView, int position, long id) {
        if (TrendArrowFactory.setType(position)) {
            showArrowConfigurator(selectedItemView, false, false);
            itr = null;
            initializeGraphicTrendArrow();
            showArrowConfigurator(selectedItemView, true, true);
        }
    }

    public static PendingIntent getHomePendingIntent() {
        return PendingIntent.getActivity(
            xdrip.getAppContext(),
            0,
            new Intent(xdrip.getAppContext(), Home.class),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // automatically restore settings backup if we just approved a request coming from that
	    if (requestCode == SdcardImportExport.TRIGGER_RESTORE_PERMISSIONS_REQUEST_STORAGE
                && permissions.length > 0 && grantResults.length > 0
                && permissions[0].equals(WRITE_EXTERNAL_STORAGE) && grantResults[0] == 0) {
            SdcardImportExport.restoreSettingsNow(this);
        }
    }

}
