/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.eveningoutpost.dexdrip.services;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;

import com.eveningoutpost.dexdrip.Home;
import com.eveningoutpost.dexdrip.R;
import com.eveningoutpost.dexdrip.importedlibraries.usbserial.util.HexDump;
import com.eveningoutpost.dexdrip.models.ActiveBluetoothDevice;
import com.eveningoutpost.dexdrip.models.BgReading;
import com.eveningoutpost.dexdrip.models.JoH;
import com.eveningoutpost.dexdrip.models.Sensor;
import com.eveningoutpost.dexdrip.models.TransmitterData;
import com.eveningoutpost.dexdrip.models.UserError;
import com.eveningoutpost.dexdrip.models.UserError.Log;
import com.eveningoutpost.dexdrip.utilitymodels.CollectionServiceStarter;
import com.eveningoutpost.dexdrip.utilitymodels.Constants;
import com.eveningoutpost.dexdrip.utilitymodels.ForegroundServiceStarter;
import com.eveningoutpost.dexdrip.utilitymodels.HM10Attributes;
import com.eveningoutpost.dexdrip.utilitymodels.Inevitable;
import com.eveningoutpost.dexdrip.utilitymodels.Pref;
import com.eveningoutpost.dexdrip.utilitymodels.XbridgePlus;
import com.eveningoutpost.dexdrip.utils.BtCallBack;
import com.eveningoutpost.dexdrip.utils.CheckBridgeBattery;
import com.eveningoutpost.dexdrip.utils.DisconnectReceiver;
import com.eveningoutpost.dexdrip.utils.bt.ScanMeister;
import com.eveningoutpost.dexdrip.utils.framework.WakeLockTrampoline;
import com.eveningoutpost.dexdrip.xdrip;
import com.rits.cloning.Cloner;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import no.nordicsemi.android.ble.BleManager;
import no.nordicsemi.android.ble.data.Data;
import no.nordicsemi.android.ble.observer.ConnectionObserver;

import static android.bluetooth.BluetoothDevice.TRANSPORT_LE;
import static android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
import static android.bluetooth.BluetoothProfile.STATE_CONNECTED;
import static android.bluetooth.BluetoothProfile.STATE_CONNECTING;
import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTED;
import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTING;
import static com.eveningoutpost.dexdrip.models.JoH.msSince;
import static com.eveningoutpost.dexdrip.models.JoH.tsl;
import static com.eveningoutpost.dexdrip.utilitymodels.BgGraphBuilder.DEXCOM_PERIOD;
import static com.eveningoutpost.dexdrip.utilitymodels.Constants.MINUTE_IN_MS;
import static com.eveningoutpost.dexdrip.utilitymodels.Constants.SECOND_IN_MS;
import static com.eveningoutpost.dexdrip.utils.bt.Helper.getStatusName;
import static com.eveningoutpost.dexdrip.xdrip.gs;


public class DexCollectionService extends Service implements BtCallBack {
    private static final String TAG = DexCollectionService.class.getSimpleName();
    private static final boolean d = true;
    private static final String PREF_DEX_COLLECTION_POLLING = "pref_dex_collection_polling";
    private static final long POLLING_PERIOD = 5 * MINUTE_IN_MS - SECOND_IN_MS;
    //private static final long RETRY_PERIOD = DEXCOM_PERIOD - 35 * SECOND_IN_MS;
    private static final long RETRY_PERIOD = DEXCOM_PERIOD - 95 * SECOND_IN_MS;
    private static final long TOLERABLE_JITTER = 10000;
    public static final int MAX_BT_WDG = 20; // Bluetooth watchdog timer default (max)
    private static final UUID CCCD = UUID.fromString(HM10Attributes.CLIENT_CHARACTERISTIC_CONFIG);

    private static long last_time_seen = 0;
    public static String lastState = "Not running";
    public static TransmitterData last_transmitter_Data;
    private static PendingIntent serviceIntent;
    private static PendingIntent serviceFailoverIntent;
    private static volatile BluetoothGatt mBluetoothGatt;
    private static volatile int mStaticState = STATE_DISCONNECTING;
    private static byte[] immediateSend;
    private static String bondedState;
    private static int last_battery_level = -1;
    private static long retry_time = 0;
    private static long failover_time = 0;
    private static long poll_backoff = 0;
    private static volatile long retry_backoff = 0;
    private static volatile long last_connect_request = 0;
    private static volatile long last_connected = -1;
    private static volatile long descriptor_time = 0;
    private static volatile int descriptor_callback_failures = 0;
    private static int watchdog_count = 0;
    private static long max_wakeup_jitter = 0;
    private static volatile DISCOVERED servicesDiscovered = DISCOVERED.NULL;
    private static String static_last_hexdump;
    private static String static_last_sent_hexdump;
    private static int error133 = 0;
    private static long last_poll_sent = 0;
    private static long lastPacketTime;
    private static volatile ScanMeister scanMeister;
    private static volatile BluetoothDevice device;
    private static volatile BluetoothGattCharacteristic mCharacteristic;
    private static volatile BluetoothGattCharacteristic mCharacteristicSend;

    private final boolean use_polling = Pref.getBooleanDefaultFalse(PREF_DEX_COLLECTION_POLLING);
    private final UUID xDripDataService = UUID.fromString(HM10Attributes.HM_10_SERVICE);
    private final UUID xDripDataCharacteristic = UUID.fromString(HM10Attributes.HM_RX_TX);
    private final String DEFAULT_BT_PIN = getDefaultPin();
    public DexCollectionService dexCollectionService;
    private SharedPreferences prefs;
    private BluetoothAdapter mBluetoothAdapter;
    private volatile InternalManager bleManager;
    private String mDeviceAddress;
    private volatile long delay_offset = 0;
    private final Cloner cloner = new Cloner();
    private final BroadcastReceiver mPairingRequestRecevier = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Received pairing request");
            if (!JoH.doPairingRequest(context, this, intent, mDeviceAddress, DEFAULT_BT_PIN)) {
                UserError.Log.d(TAG, "Pairing request marked as failed, reducing settings to avoid auto-pairing");
                unRegisterPairingReceiver();
            }
        }
    };
    private ForegroundServiceStarter foregroundServiceStarter;
    private volatile int mConnectionState = BluetoothProfile.STATE_DISCONNECTING;
    private byte[] lastdata = null;

    private boolean useBleManager = false;
    private synchronized void initBleManager() {
        if (bleManager == null && useBleManager) { // TODO filter for libre only???
            Log.d(TAG, "Initializing BLE manager");
            bleManager = new InternalManager(xdrip.getAppContext());
            bleManager.setConnectionObserver(connectionObserver);
        }
    }

    public SharedPreferences.OnSharedPreferenceChangeListener prefListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            if (key.compareTo("run_service_in_foreground") == 0) {
                Log.d("FOREGROUND", "run_service_in_foreground changed!");
                if (prefs.getBoolean("run_service_in_foreground", false)) {
                    foregroundServiceStarter = new ForegroundServiceStarter(getApplicationContext(), dexCollectionService);
                    foregroundServiceStarter.start();
                    Log.d(TAG, "Moving to foreground");
                } else {
                    dexCollectionService.stopForeground(true);
                    Log.d(TAG, "Removing from foreground");
                }
            }
            if (key.equals("dex_collection_method") || key.equals("dex_txid")) {
                //if the input method or ID changed, accept any new package once even if they seem duplicates
                Log.d(TAG, "collection method or txID changed - setting lastdata to null");
                lastdata = null;
            }
            if (key.equals("bluetooth_use_blemanager")) {
                try {
                    stopSelf();
                    CollectionServiceStarter.restartCollectionServiceBackground();
                } catch (Exception e) {
                    UserError.Log.wtf(TAG, "Failed to start collector: " + e);
                }

            }
        }
    };


    private synchronized void handleConnectedStateChange() {
        mConnectionState = STATE_CONNECTED;
        last_connected = tsl();
        scanMeister.stop();
        if ((servicesDiscovered == DISCOVERED.NULL) || Pref.getBoolean("always_discover_services", true)) {
            Log.d(TAG, "Requesting to discover services: previous: " + servicesDiscovered);
            servicesDiscovered = DISCOVERED.PENDING;
        }
        ActiveBluetoothDevice.connected();

        if (JoH.ratelimit("attempt-connection", 30)) {
            checkConnection(); // refresh status info
        }
        if (servicesDiscovered != DISCOVERED.COMPLETE) {
            if (mBluetoothGatt != null) {
                if (JoH.ratelimit("dexcollect-discover-services", 1)) {
                    Log.d(TAG, "Calling discoverServices");
                    mBluetoothGatt.discoverServices();
                } else {
                    Log.d(TAG, "Discover services duplicate blocked by rate limit");
                }

            } else {
                UserError.Log.d(TAG, "Wanted to discover services but gatt was null!");
            }
        } else {
            Log.d(TAG, "Services already discovered");
            checkImmediateSend();
        }

        if (mBluetoothGatt == null) {
            //gregorybel: no needs to continue if Gatt is null!
            UserError.Log.e(TAG, "gregorybel: force disconnect!");
            handleDisconnectedStateChange();
        }
    }

    @Override
    public void btCallback(String address, String status) {

            UserError.Log.d(TAG, "Processing callback: " + address + " :: " + status);
            if (address.equals(mDeviceAddress)) {
                switch (status) {
                    case "DISCONNECTED":
                        if (!useBleManager) {
                        handleDisconnectedStateChange();
                        } else {
                            Log.d(TAG, "Ignoring callback as we are using bleManager");
                        }
                        break;
                    case "SCAN_FOUND":
                        connectIfNotConnected(address);
                        break;
                    case "SCAN_TIMEOUT":
                        status("Scan timed out");
                        setRetryTimer();
                        break;
                    case "SCAN_FAILED":
                        status("Scan Failed!");
                        break;

                    default:
                        UserError.Log.e(TAG, "Unknown status callback for: " + address + " with " + status);
                }
            } else {
                UserError.Log.d(TAG, "Ignoring: " + status + " for " + address + " as we are using: " + mDeviceAddress);
            }

    }

    private synchronized void handleDisconnectedStateChange() {
        if (JoH.ratelimit("handle-disconnected-state-change", 2)) {
            mConnectionState = STATE_DISCONNECTED;
            ActiveBluetoothDevice.disconnected();

            if (!getTrustAutoConnect()) {
                if (mBluetoothGatt != null) {
                    UserError.Log.d(TAG, "Sending disconnection");
                    try {
                        mBluetoothGatt.disconnect();
                    } catch (Exception e) {
                        //
                    }
                }
            }

            // TODO should we allow close when trusting auto-connect?
            if (prefs.getBoolean("close_gatt_on_ble_disconnect", true)) {
                if (mBluetoothGatt != null) {
                    Log.i(TAG, "onConnectionStateChange: mBluetoothGatt is not null, closing.");
                    if (JoH.ratelimit("refresh-gatt", 60)) {
                        Log.d(TAG, "Refresh result state close: " + JoH.refreshDeviceCache(TAG, mBluetoothGatt));
                    }
                    mBluetoothGatt.close();
                    mBluetoothGatt = null;
                    mCharacteristic = null;
                    servicesDiscovered = DISCOVERED.NULL;
                } else {
                    Log.d(TAG, "mBluetoothGatt is null so not closing");
                }
                lastdata = null;
            } else {
                UserError.Log.d(TAG, "Not closing gatt on bluetooth disconnect");
            }
            Log.i(TAG, "onConnectionStateChange: Disconnected from GATT server.");
            setRetryTimer();
        } else {
            UserError.Log.d(TAG, "Ignoring duplicate disconnected state change");
        }
    }

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public synchronized void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            final PowerManager.WakeLock wl = JoH.getWakeLock("bluetooth-gatt", MINUTE_IN_MS);

            if (status == 133) {
                error133++;
            } else {
                error133 = 0;
            }
            try {
                if (Pref.getBoolean("bluetooth_excessive_wakelocks", true)) {
                  /*  PowerManager powerManager = (PowerManager) mContext.getSystemService(POWER_SERVICE);
                    PowerManager.WakeLock wakeLock2 = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                            "DexCollectionService");
                    wakeLock2.acquire(45000);*/
                    final PowerManager.WakeLock wakeLock2 = JoH.getWakeLock("DexCollectionExcessive", 45 * SECOND_IN_MS);

                }
                UserError.Log.d(TAG, "onState change status = " + status); // temporary debug
                // TODO are implementations really bad enough that we need to check that gatt.getDevice() matches the
                // requested device also?

                switch (newState) {
                    case BluetoothProfile.STATE_CONNECTED:
                        if (status == 0) {
                            Log.i(TAG, "onConnectionStateChange: Connected to GATT server. ");
                            handleConnectedStateChange();
                        } else {
                            Log.i(TAG, "Not accepting CONNECTED change as status code is: " + status);
                            if (status == 133) {
                                Log.i(TAG, "Treating as disconnected due to error 133 count: " + error133);
                                handleDisconnectedStateChange();
                            }
                        }
                        break;
                    case BluetoothProfile.STATE_DISCONNECTED:
                        Log.i(TAG, "onConnectionStateChange: State disconnected.");
                        handleDisconnectedStateChange();
                        break;
                }
            } catch (Exception e) {
                UserError.Log.wtf(TAG, "Caught exception in Gatt callback " + e);
                UserError.Log.wtf(TAG, e);
            } finally {
                mStaticState = mConnectionState;
                JoH.releaseWakeLock(wl);
            }
        }


        @Override
        public synchronized void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(TAG, "Services discovered start");
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "onServicesDiscovered received: " + status);
                return;
            }
            final PowerManager.WakeLock wl = JoH.getWakeLock("bluetooth-onservices", MINUTE_IN_MS);
            Log.d(TAG, "onServicesDiscovered received status: " + status);

            final BluetoothGattService gattService = mBluetoothGatt.getService(xDripDataService);
            if (gattService == null) {
                {
                    Log.w(TAG, "onServicesDiscovered: xdrip service " + xDripDataService + " not found"); //TODO the selection of nrf is not active at the beginning,so this error will be trown one time unneeded, mey to be optimized.
                    // TODO this should be reworked to be an efficient selector
                    listAvailableServices(mBluetoothGatt);
                }
                // try next
            } else {
                final BluetoothGattCharacteristic gattCharacteristic = gattService.getCharacteristic(xDripDataCharacteristic);
                if (gattCharacteristic == null) {
                    Log.w(TAG, "onServicesDiscovered: characteristic " + xDripDataCharacteristic + " not found");
                    JoH.releaseWakeLock(wl);
                    Log.d(TAG, "onServicesDiscovered: returning due to null xDrip characteristic");
                    return;
                }

                mCharacteristic = gattCharacteristic;
                final int charaProp = gattCharacteristic.getProperties();
                if ((charaProp & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                    mBluetoothGatt.setCharacteristicNotification(gattCharacteristic, true);

//                    if (!static_use_blukon) {
                        try {
                            // TODO use CCCD?
                            final BluetoothGattDescriptor bdescriptor = gattCharacteristic.getDescriptor(UUID.fromString(HM10Attributes.CLIENT_CHARACTERISTIC_CONFIG));
                            Log.i(TAG, "Bluetooth Notification Descriptor found: " + bdescriptor.getUuid());
                            bdescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            descriptor_time = tsl();
                            mBluetoothGatt.writeDescriptor(bdescriptor);
                        } catch (Exception e) {
                            Log.e(TAG, "Error setting notification value descriptor: " + e);
                        }
//                    }
	                mCharacteristicSend = mCharacteristic;
                } else {
                    Log.w(TAG, "onServicesDiscovered: characteristic " + xDripDataCharacteristic + " not found");
                }
            }

            // TODO is this duplicated in some situations?

            Inevitable.task("dex-descrpiptor-retry", 2000, () -> {
                try {
                    UserError.Log.d(TAG, "Writing descriptor inside delayed discover services");
                    final BluetoothGattDescriptor descriptor = mCharacteristic.getDescriptor(CCCD);
                    if (descriptor != null) {
                        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        descriptor_time = tsl();
                        if (!mBluetoothGatt.writeDescriptor(descriptor)) {
                            Log.d(TAG, "Failed to write descriptor!");
                        } else {
                            Inevitable.task("dex_check_descriptor_write", 2000, new Runnable() {
                                @Override
                                public void run() {
                                    if (descriptor_time != 0) {
                                        Log.e(TAG, "Descriptor write did not callback! since: " + JoH.dateTimeText(descriptor_time));
                                        descriptor_time = 0;
                                            try {
                                                if (!mBluetoothGatt.writeDescriptor(descriptor)) {
                                                    Log.d(TAG, "Failed to write descriptor in check callback!");
                                                }
                                            } catch (Exception e) {
                                                UserError.Log.e(TAG, "Exception during callback check retry: " + e);
                                            }
                                    }
                                }
                            });
                        }
                    }
                } catch (NullPointerException e) {
                    Log.e(TAG, "Got null pointer trying to set CCCD descriptor");
                }
            });

            try {
                final int charaProp = mCharacteristic.getProperties();
                    if ((charaProp & BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                        JoH.runOnUiThreadDelayed(() -> {
                            try {
                                Log.d(TAG, "Reading characteristic: " + mCharacteristic.getUuid().toString());
                                mBluetoothGatt.readCharacteristic(mCharacteristic);
                            } catch (NullPointerException e) {
                                Log.e(TAG, "Got null pointer trying to readCharacteristic");
                            }
                        }, 300);
                    }
            } catch (NullPointerException e) {
                UserError.Log.d(TAG, "mCharacteristic was null when attempting to get properties!");
            }

            Log.d(TAG, "Services discovered end");
            servicesDiscovered = DISCOVERED.COMPLETE;
            // waitFor(300);
            Log.d(TAG, "Services discovered release");
            checkImmediateSend(); // TODO maybe find a better home for this
            JoH.releaseWakeLock(wl);
        }

        @Override
        public void onCharacteristicWrite(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final int status) {
            try {
                final String msg = "OnCharacteristic WRITE: "
                        + " status: " + getStatusName(status) + " data: " + JoH.bytesToHex(characteristic.getValue()) + " uuid: " + characteristic.getUuid();
                if (status == 0) {
                    Log.d(TAG, msg);
                } else {
                    UserError.Log.e(TAG, msg);
                }
            } catch (Exception e) {
                UserError.Log.wtf(TAG, "Got exception trying to display data: " + e);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            onCharacteristicChanged(gatt, characteristic);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {

            final PowerManager.WakeLock wakeLock1 = JoH.getWakeLock("DexCollectionService", 60000);
            try {
                final byte[] data = characteristic.getValue();

                if (data != null && data.length > 0) {
                    setSerialDataToTransmitterRawData(data, data.length);

                    final String hexdump = HexDump.dumpHexString(data);
                    //if (!hexdump.contains("0x00000000 00      ")) {
                    if (data.length > 1 || data[0] != 0x00) {
                        static_last_hexdump = hexdump;
                    }
                    if (d) Log.i(TAG, "onCharacteristicChanged entered " + hexdump);

                }
                lastdata = data;

                Inevitable.task("dex-set-failover", 1000, () -> {
                    setFailoverTimer(); // restart the countdown
                    // intentionally left hanging wakelock for 5 seconds after we receive something
                    final PowerManager.WakeLock wakeLock2 = JoH.getWakeLock("DexCollectionLinger", 5000);
                });

            } finally {
               /* if (Pref.getBoolean("bluetooth_frequent_reset", false)) {
                    Log.e(TAG, "Resetting bluetooth due to constant reset option being set!");
                    JoH.restartBluetooth(getApplicationContext(), 5000);
                }*/
                JoH.releaseWakeLock(wakeLock1);
            }
        }

        @Override
        public synchronized void onDescriptorWrite(BluetoothGatt gatt, final BluetoothGattDescriptor descriptor,
                                                   int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "onDescriptorWrite: Wrote GATT Descriptor successfully.");
                descriptor_callback_failures = 0;
            } else {
                Log.d(TAG, "onDescriptorWrite: Error writing GATT Descriptor: " + status);
                if (descriptor_callback_failures == 0) {
                    descriptor_callback_failures++;

                    // TODO not sure if we want this retry here..
                    try {
                        if (!mBluetoothGatt.writeDescriptor(descriptor)) {
                            Log.d(TAG, "Failed to write descriptor in on descriptor write retry");
                        } else {
                            UserError.Log.d(TAG, "Tried to write descriptor again inside onDescriptorWrite");
                        }
                    } catch (Exception e) {
                        UserError.Log.e(TAG, "Exception during callback retry: " + e);
                    }
                }
            }
            descriptor_time = 0;
        }
    };

    private static String getDefaultPin() {
        return HM10Attributes.HM_DEFAULT_BT_PIN;
    }

    @SuppressLint("ObsoleteSdkInt")
    private boolean shouldServiceRun() {
        return false;
    }

    // remember needs proguard exclusion due to access by reflection
    public static boolean isCollecting() {
        return msSince(lastPacketTime) < (MINUTE_IN_MS * 5);
    }

    private static void status(String msg) {
        lastState = msg + " " + JoH.hourMinuteString();
    }


    private static String getStateStr(int mConnectionState) {
        mStaticState = mConnectionState;
        switch (mConnectionState) {
            case STATE_CONNECTED:
                return "CONNECTED";
            case STATE_CONNECTING:
                return "CONNECTING";
            case STATE_DISCONNECTED:
                return "DISCONNECTED";
            case STATE_DISCONNECTING:
                return "DISCONNECTING";
            default:
                return "UNKNOWN STATE!";
        }
    }

    private static Integer convertSrc(final String Src) {
        Integer res = 0;
        String tmpSrc = Src.toUpperCase();
        res |= getSrcValue(tmpSrc.charAt(0)) << 20;
        res |= getSrcValue(tmpSrc.charAt(1)) << 15;
        res |= getSrcValue(tmpSrc.charAt(2)) << 10;
        res |= getSrcValue(tmpSrc.charAt(3)) << 5;
        res |= getSrcValue(tmpSrc.charAt(4));
        return res;
    }

    private static int getSrcValue(char ch) {
        int i;
        char[] cTable = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'J', 'K', 'L', 'M', 'N', 'P', 'Q', 'R', 'S', 'T', 'U', 'W', 'X', 'Y'};
        for (i = 0; i < cTable.length; i++) {
            if (cTable[i] == ch) break;
        }
        return i;
    }


    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {

        if (scanMeister == null) {
            scanMeister = new ScanMeister()
                    .applyKnownWorkarounds()
                    .addCallBack(this, TAG);
        }

        useBleManager = Pref.getBooleanDefaultFalse("bluetooth_use_blemanager");
        initBleManager();

        foregroundServiceStarter = new ForegroundServiceStarter(getApplicationContext(), this);
        foregroundServiceStarter.start();
        //mContext = getApplicationContext();
        dexCollectionService = this;
        prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        listenForChangeInSettings();
        //bgToSpeech = BgToSpeech.setupTTS(mContext); //keep reference to not being garbage collected

        cloner.dontClone(
                android.bluetooth.BluetoothDevice.class,
                android.bluetooth.BluetoothGattService.class
        );
        final IntentFilter pairingRequestFilter = new IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST);
        pairingRequestFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY - 1);
        Log.i(TAG, "onCreate: STARTING SERVICE: pin code: " + DEFAULT_BT_PIN);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final PowerManager.WakeLock wl = JoH.getWakeLock("dexcollect-service", 2 * MINUTE_IN_MS);
        lastPacketTime = 0; // reset
        if (retry_time > 0 && failover_time > 0) {
            final long requested_wake_time = Math.min(retry_time, failover_time);
            final long wakeup_jitter = msSince(requested_wake_time);
            final String jitter_string = JoH.niceTimeScalar(wakeup_jitter);
            if (!jitter_string.startsWith("0 ")) {
                Log.d(TAG, "Wake up jitter: " + jitter_string);
            }
            JoH.persistentBuggySamsungCheck();
            if ((wakeup_jitter > TOLERABLE_JITTER) && (!JoH.buggy_samsung) && (JoH.isSamsung())) {
                UserError.Log.wtf(TAG, "Enabled wake workaround due to jitter of: " + JoH.niceTimeScalar(wakeup_jitter));
                JoH.setBuggySamsungEnabled();
                max_wakeup_jitter = 0;
            } else {
                max_wakeup_jitter = Math.max(max_wakeup_jitter, wakeup_jitter);
            }
        }
        retry_time = 0;
        failover_time = 0;
        status("Started");
        if (shouldServiceRun()) {
            setFailoverTimer();
        } else {
            status("Stopping");
            stopSelf();
            JoH.releaseWakeLock(wl);
            return START_NOT_STICKY;
        }
        lastdata = null;
        DisconnectReceiver.addCallBack(this, TAG);
        checkConnection();
        watchdog();
        JoH.releaseWakeLock(wl);
        return START_STICKY;
    }

    private void unRegisterPairingReceiver() {
        try {
            unregisterReceiver(mPairingRequestRecevier);
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error unregistering pairing receiver: " + e);
        }
    }

    @Override
    public void onDestroy() {
        status("Shutdown");
        super.onDestroy();
        Log.d(TAG, "onDestroy entered");
        close();
        foregroundServiceStarter.stop();

        unRegisterPairingReceiver();

        DisconnectReceiver.removeCallBack(TAG);

        if (scanMeister != null) {
            scanMeister.removeCallBack(TAG);
            scanMeister.stop();
        }

        if (bleManager != null) {
            Log.d(TAG, "onDestroy: Stopping BLE manager");
            bleManager.disconnect().enqueue();
            bleManager.close();
            bleManager = null;
        }

        if (shouldServiceRun()) {//Android killed service
            setRetryTimer();
            status("Stopped, attempting restart");
        } else {//onDestroy triggered by CollectionServiceStart.stopBtService
            Log.d(TAG, "onDestroy stop Alarm serviceIntent");
            JoH.cancelAlarm(this, serviceIntent);
            Log.d(TAG, "onDestroy stop Alarm serviceFailoverIntent");
            JoH.cancelAlarm(this, serviceFailoverIntent);
            status("Service full stop");
            retry_time = 0;
            failover_time = 0;
        }

        retry_backoff = 0;
        poll_backoff = 0;
        servicesDiscovered = DISCOVERED.NULL;

        try {
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener);
        } catch (Exception e) {
            //
        }

        Log.i(TAG, "SERVICE STOPPED");
    }

    public void listenForChangeInSettings() {
        prefs.registerOnSharedPreferenceChangeListener(prefListener);
    }


    public void setRetryTimer() {
        mStaticState = mConnectionState;
        if (shouldServiceRun()) {
            //final long retry_in = (Constants.SECOND_IN_MS * 25);
            final long retry_in = whenToRetryNext();
            Log.d(TAG, "setRetryTimer: Restarting in: " + (retry_in / SECOND_IN_MS) + " seconds - last connected: "+JoH.dateTimeText(last_connected));
            //serviceIntent = PendingIntent.getService(this, Constants.DEX_COLLECTION_SERVICE_RETRY_ID, new Intent(this, this.getClass()), 0);
            serviceIntent = WakeLockTrampoline.getPendingIntent(this.getClass(), Constants.DEX_COLLECTION_SERVICE_RETRY_ID);
            retry_time = JoH.wakeUpIntent(this, retry_in, serviceIntent);
        } else {
            Log.d(TAG, "Not setting retry timer as service should not be running");
        }
    }

    public synchronized void setFailoverTimer() {
        if (shouldServiceRun()) {
            final long retry_in = use_polling ? whenToPollNext() : (MINUTE_IN_MS * 6);
            Log.d(TAG, "setFailoverTimer: Fallover Restarting in: " + (retry_in / (MINUTE_IN_MS)) + " minutes");
            //serviceFailoverIntent = PendingIntent.getService(this, Constants.DEX_COLLECTION_SERVICE_FAILOVER_ID, new Intent(this, this.getClass()), 0);
            serviceFailoverIntent = WakeLockTrampoline.getPendingIntent(this.getClass(), Constants.DEX_COLLECTION_SERVICE_FAILOVER_ID);
            failover_time = JoH.wakeUpIntent(this, retry_in, serviceFailoverIntent);
            retry_time = 0; // only one alarm will run
        } else {
            stopSelf();
        }
    }

    private long whenToRetryNext() {
        final long poll_time = Math.max((SECOND_IN_MS * 10) + retry_backoff, RETRY_PERIOD - msSince(lastPacketTime));
        if (retry_backoff < (MINUTE_IN_MS)) {
            retry_backoff += SECOND_IN_MS;
        }
        Log.d(TAG, "Scheduling next retry in: " + JoH.niceTimeScalar(poll_time) + " @ " + JoH.dateTimeText(poll_time + tsl()) + " period diff: " + (RETRY_PERIOD - msSince(lastPacketTime)));
        return poll_time;
    }

    private long whenToPollNext() {
        final long poll_time = Math.max((SECOND_IN_MS * 5) + poll_backoff, POLLING_PERIOD - msSince(lastPacketTime));
        if (poll_backoff < (MINUTE_IN_MS * 6)) {
            poll_backoff += SECOND_IN_MS;
        }
        Log.d(TAG, "Scheduling next poll in: " + JoH.niceTimeScalar(poll_time) + " @ " + JoH.dateTimeText(poll_time + tsl()) + " period diff: " + (POLLING_PERIOD - msSince(lastPacketTime)));
        return poll_time;
    }

    synchronized void checkConnection() {
        status("Attempting connection");
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            status("No bluetooth manager");
            setRetryTimer();
            return;
        }

        mBluetoothAdapter = bluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            status("No bluetooth adapter");
            setRetryTimer();
            return;
        }

        if (!mBluetoothAdapter.isEnabled()) {
            mConnectionState = STATE_DISCONNECTED; // can't be connected if BT is disabled
            if (Pref.getBoolean("automatically_turn_bluetooth_on", true)) {
                Log.i(TAG, "Turning bluetooth on as appears disabled");
                status("Turning bluetooth on");
                JoH.setBluetoothEnabled(getApplicationContext(), true);
            } else {
                Log.d(TAG, "Not automatically turning on bluetooth due to preferences");
            }
        }

        if (device != null) {
            boolean found = false;
            for (BluetoothDevice bluetoothDevice : bluetoothManager.getConnectedDevices(BluetoothProfile.GATT)) {
                if (bluetoothDevice.getAddress().equals(device.getAddress())) {
                    found = true;
                    if (mConnectionState != STATE_CONNECTED) {
                        UserError.Log.d(TAG, "Detected state change by checking connected devices");
                        handleConnectedStateChange();
                    }
                    break;
                }
            }
            if (!found) {
                if (mConnectionState == STATE_CONNECTED) {
                    UserError.Log.d(TAG, "Marking disconnected as not in list of connected devices");
                    mConnectionState = STATE_DISCONNECTED; // not in connected list so should be disconnected we think
                }

            }
        } else {
            UserError.Log.d(TAG, "Device is null in checkConnection");
            mConnectionState = STATE_DISCONNECTED; // can't be connected if we don't know the device
        }

        Log.i(TAG, "checkConnection: Connection state: " + getStateStr(mConnectionState));
        if (mConnectionState == STATE_DISCONNECTED || mConnectionState == STATE_DISCONNECTING) {
            final ActiveBluetoothDevice btDevice = ActiveBluetoothDevice.first();
            if (btDevice != null) {
                final String deviceAddress = btDevice.address;
                mDeviceAddress = deviceAddress;
                try {
                    if (mBluetoothAdapter.isEnabled() && mBluetoothAdapter.getRemoteDevice(deviceAddress) != null) {
                        if (bleManager != null || useScanning()) {
                            status(gs(R.string.scanning) + (Home.get_engineering_mode() ? ": " + deviceAddress : ""));
                            Log.i(TAG, "scanning for addresses " + deviceAddress);
                            scanMeister.setAddress(deviceAddress)
                                    .addCallBack(this, TAG)
                                    .setTimeout(65)
                                    .scan();
                        } else {
                            status("Connecting" + (Home.get_engineering_mode() ? ": " + deviceAddress : ""));
                            Log.i(TAG, "Connecting to addresses " + deviceAddress);
                            connect(deviceAddress);
                        }
                        mStaticState = mConnectionState;
                        return;
                    }
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "IllegalArgumentException: " + e);
                }
            }
        } else if (mConnectionState == STATE_CONNECTING) {
            mStaticState = mConnectionState;
            if (msSince(last_connect_request) > (getTrustAutoConnect() ? SECOND_IN_MS * 3600 : SECOND_IN_MS * 30)) {
                Log.i(TAG, "Connecting for too long, shutting down");
                retry_backoff = 0;
                close();
            }
        } else if (mConnectionState == STATE_CONNECTED) { //WOOO, we are good to go, nothing to do here!
            status("Last Connected");
            Log.i(TAG, "checkConnection: Looks like we are already connected, ready to receive");
            retry_backoff = 0;
            mStaticState = mConnectionState;
            if (use_polling && (msSince(lastPacketTime) >= POLLING_PERIOD)) {
                pollForData();
            }

            return;
        }
        setRetryTimer();
    }

    private synchronized void checkImmediateSend() {
        if (immediateSend != null) {
            Log.d(TAG, "Sending immediate data: " + JoH.bytesToHex(immediateSend));
            sendBtMessage(immediateSend);
            immediateSend = null;
        }
    }

    private synchronized void pollForData() {
        if (JoH.ratelimit("poll-for-data", 5)) {
            new Thread() {
                @Override
                public void run() {
                    Log.d(TAG, "Polling for data");
                    int wait_counter = 0;
                    while (servicesDiscovered != DISCOVERED.COMPLETE && wait_counter < 10) {
                        Log.d(TAG, "Waiting for service discovery: " + servicesDiscovered + " count: " + wait_counter);
                        try {
                            Thread.sleep(200); // delay for wakeup readiness
                        } catch (InterruptedException e) {
                            //
                        }
                        wait_counter++;
                    }
                    if (servicesDiscovered == DISCOVERED.NULL) {
                        Log.e(TAG, "Failed to discover services!");
                        try {
                            if (JoH.ratelimit("rediscover-services", 30)) {
                                Log.d(TAG, "Refresh result: " + JoH.refreshDeviceCache(TAG, mBluetoothGatt));
                                mBluetoothGatt.discoverServices();
                            }
                        } catch (Exception e) {
                            Log.d(TAG, "Exception discovering services: " + e);
                        }
                    }
                    last_poll_sent = tsl();
                    if ((msSince(lastPacketTime) > Home.stale_data_millis()) && (JoH.ratelimit("poll-request-part-b", 15))) {
                        Log.e(TAG, "Stale data so requesting backfill");
                        sendBtMessage(XbridgePlus.sendLast15BRequestPacket());
                    } else {
                        sendBtMessage(XbridgePlus.sendDataRequestPacket());
                    }
                }
            }.start();
        }
    }

    /**
     * Displays all services and characteristics for debugging purposes.
     *
     * @param bluetoothGatt BLE gatt profile.
     */
    private void listAvailableServices(BluetoothGatt bluetoothGatt) {
        Log.d(TAG, "Listing available services:");
        for (BluetoothGattService service : bluetoothGatt.getServices()) {
            Log.d(TAG, "Service: " + service.getUuid().toString());
            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                Log.d(TAG, "|-- Characteristic: " + characteristic.getUuid().toString());
            }
        }
    }

    private boolean sendBtMessage(byte[] buffer) {
        return sendBtMessage(JoH.bArrayAsBuffer(buffer));
    }

    private synchronized boolean sendBtMessage(final ByteBuffer message) {
        // TODO affirm send happened
        //check mBluetoothGatt is available
        Log.i(TAG, "sendBtMessage: entered");
        final byte[] value = message.array();

        static_last_sent_hexdump = HexDump.dumpHexString(value);

        if (bleManager != null) {
            Log.d(TAG, "sendBtMessage: using bleManager: " + static_last_sent_hexdump);
            bleManager.safeWriteCharacteristic(mCharacteristicSend, new Data(value));
            return true;
        }

        if (mBluetoothGatt == null) {
            Log.w(TAG, "sendBtMessage: lost connection");
            if (JoH.ratelimit("sendbtmessagelost", 60)) {
                mConnectionState = STATE_DISCONNECTED;
                setRetryTimer();
            }
            return false;
        }


        Log.i(TAG, "sendBtMessage: sending message: " + static_last_sent_hexdump);

        // BLUCON NULL HERE? HOW TO RESOLVE?
        if (mCharacteristic == null) {
            status("Error: mCharacteristic was null in sendBtMessage");
            Log.e(TAG, lastState);
            servicesDiscovered = DISCOVERED.NULL;
            return false;
        }

        if (mCharacteristicSend != null && mCharacteristicSend != mCharacteristic) {
            return writeChar(mCharacteristicSend, value);
        }

        return writeChar(mCharacteristic, value);
    }

    private synchronized boolean writeChar(final BluetoothGattCharacteristic localmCharacteristic, final byte[] value) {
        if (value == null) {
            UserError.Log.e(TAG, "Value null in write char");
            return false;
        }
        if (localmCharacteristic == null) {
            UserError.Log.e(TAG, "localmCharacteristic null in write char");
            return false;
        }
        localmCharacteristic.setValue(value);
        final boolean result = mBluetoothGatt != null && mBluetoothGatt.writeCharacteristic(localmCharacteristic);
        if (!result) {
            UserError.Log.d(TAG, "Error writing characteristic: " + localmCharacteristic.getUuid() + " " + JoH.bytesToHex(value));

            final BluetoothGattCharacteristic resendCharacteristic = cloner.shallowClone(localmCharacteristic);

            if (JoH.quietratelimit("dexcol-resend-offset", 2)) {
                delay_offset = 0;
            } else {
                delay_offset += 100;
                if (d) UserError.Log.e(TAG, "Delay offset now: " + delay_offset);
            }

            JoH.getWakeLock("dexcol-resend-linger", 1000); // dangling wakelock to ensure awake for resend
            JoH.runOnUiThreadDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        boolean result = mBluetoothGatt != null && mBluetoothGatt.writeCharacteristic(resendCharacteristic);
                        if (!result) {
                            UserError.Log.e(TAG, "Error writing characteristic: (2nd try) " + resendCharacteristic.getUuid() + " " + JoH.bytesToHex(value));
                        } else {
                            UserError.Log.d(TAG, "Succeeded writing characteristic: (2nd try) " + resendCharacteristic.getUuid() + " " + JoH.bytesToHex(value));
                        }
                    } catch (Exception e) {
                        UserError.Log.wtf(TAG, "Exception during 2nd try write: " + e + " " + resendCharacteristic.getUuid() + " " + JoH.bytesToHex(value));
                    }
                }
            }, 500 + delay_offset);
        }
        return result;
    }

    public synchronized boolean connectIfNotConnected(final String address) {
        UserError.Log.d(TAG, "connectIfNotConnected!!! " + address);
        // check connected!
        if (mConnectionState != STATE_CONNECTED) {
            return connect(address);
        } else {
            UserError.Log.d(TAG, "Already connected");
            return false;
        }
    }

    public synchronized boolean connect(final String address) {
        Log.i(TAG, "connect: going to connect to device at address: " + address);

        if (mBluetoothAdapter == null || address == null) {
            Log.i(TAG, "connect: BluetoothAdapter not initialized or unspecified address.");
            setRetryTimer();
            return false;
        }

        if (bleManager != null) {
            device = getDeviceFromMac(address);
            if (device != null) {
                Log.d(TAG,"connect: using bleManager");
                bleManager.connect(device)
                        .useAutoConnect(false) // todo trust autoconnect?
                        .retry(3, 200)
                        .enqueue();
                return true;
            } else {
                Log.d(TAG, "connect: bleManager could not create device from mac: " + address);
                return false;
            }
        }

        // close and re-open the connection if preference set or device has changed
        final boolean should_close = Pref.getBooleanDefaultFalse("close_gatt_on_ble_disconnect")
                || (device == null || !address.equalsIgnoreCase(device.getAddress())
                || ((msSince(last_connect_request) > (MINUTE_IN_MS * 15) && (JoH.pratelimit("dex-collect-full-close", 600)))));

        closeCycle(should_close);

        device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            setRetryTimer();
            return false;
        }

        setRetryTimer();
        if (mBluetoothGatt == null) {
            Log.i(TAG, "connect: Trying to create a new connection.");

            mBluetoothGatt = device.connectGatt(getApplicationContext(), getTrustAutoConnect(), mGattCallback, TRANSPORT_LE);

        } else {
            Log.i(TAG, "connect: Trying to re-use connection.");
            mBluetoothGatt.connect();
        }
        mConnectionState = STATE_CONNECTING;
        last_connect_request = tsl();
        return true;
    }

    private static boolean getTrustAutoConnect() {
        return Pref.getBoolean("bluetooth_trust_autoconnect", true);
    }

    private void closeCycle(boolean should_close) {
        if (mBluetoothGatt != null) {
            try {
                if (JoH.ratelimit("refresh-gatt", 60)) {
                    Log.d(TAG, "Refresh result close: " + JoH.refreshDeviceCache(TAG, mBluetoothGatt));
                }
                if (should_close) {
                    Log.i(TAG, "connect: mBluetoothGatt isn't null, Closing.");
                    mBluetoothGatt.close();
                } else {
                    Log.i(TAG, "preserving existing connection");
                }
            } catch (NullPointerException e) {
                Log.wtf(TAG, "Concurrency related null pointer in connect");
            } finally {
                if (should_close) mBluetoothGatt = null;
            }
        }
    }


    public synchronized void close() {
        Log.i(TAG, "close: Closing Connection - setting state DISCONNECTED");
        if (mBluetoothGatt == null) {
            Log.i(TAG, "not closing as bluetooth gatt is null");
            //  return;
        } else {
            if (JoH.ratelimit("refresh-gatt", 180)) {
                Log.d(TAG, "Refresh result state close: " + JoH.refreshDeviceCache(TAG, mBluetoothGatt));
            }

            try {
                mBluetoothGatt.close();
            } catch (NullPointerException e) {
                Log.wtf(TAG, "Concurrency related null pointer in close");
            }

        }

        setRetryTimer();
        mBluetoothGatt = null;
        mCharacteristic = null;
        mConnectionState = STATE_DISCONNECTED;
        servicesDiscovered = DISCOVERED.NULL;
        last_connect_request = 0;
    }

    public synchronized void setSerialDataToTransmitterRawData(byte[] buffer, int len) {

        last_time_seen = tsl();
        watchdog_count = 0;

        {
            long timestamp = new Date().getTime();
            if (((buffer.length > 0) && (buffer[0] == 0x07 || buffer[0] == 0x11 || buffer[0] == 0x15))) {
                if ((buffer.length == 1) && (buffer[0] == 0x00)) {
                    return; // null packet
                }
                Log.i(TAG, "setSerialDataToTransmitterRawData: Dealing with Dexbridge packet!");
                int DexSrc;
                int TransmitterID;
                String TxId;
                ByteBuffer tmpBuffer = ByteBuffer.allocate(len);
                tmpBuffer.order(ByteOrder.LITTLE_ENDIAN);
                tmpBuffer.put(buffer, 0, len);
                ByteBuffer txidMessage = ByteBuffer.allocate(6);
                txidMessage.order(ByteOrder.LITTLE_ENDIAN);
                if (buffer[0] == 0x07 && buffer[1] == -15) {
                    //We have a Beacon packet.  Get the TXID value and compare with dex_txid
                    Log.i(TAG, "setSerialDataToTransmitterRawData: Received Beacon packet.");
                    //DexSrc starts at Byte 2 of a Beacon packet.
                    DexSrc = tmpBuffer.getInt(2);
                    TxId = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString("dex_txid", "00000");
                    TransmitterID = convertSrc(TxId);
                    if (TxId.compareTo("00000") != 0 && Integer.compare(DexSrc, TransmitterID) != 0) {
                        Log.w(TAG, "setSerialDataToTransmitterRawData: TXID wrong.  Expected " + TransmitterID + " but got " + DexSrc);
                        txidMessage.put(0, (byte) 0x06);
                        txidMessage.put(1, (byte) 0x01);
                        txidMessage.putInt(2, TransmitterID);
                        sendBtMessage(txidMessage);
                    }
                    return;
                }
                if ((buffer[0] == 0x11 || buffer[0] == 0x15) && buffer[1] == 0x00) {
                    //we have a data packet.  Check to see if the TXID is what we are expecting.
                    Log.i(TAG, "setSerialDataToTransmitterRawData: Received Data packet");
                    if (len >= 0x11) {
                        //DexSrc starts at Byte 12 of a data packet.
                        DexSrc = tmpBuffer.getInt(12);
                        TxId = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString("dex_txid", "00000");
                        TransmitterID = convertSrc(TxId);
                        if (Integer.compare(DexSrc, TransmitterID) != 0) {
                            Log.w(TAG, "TXID wrong.  Expected " + TransmitterID + " but got " + DexSrc);
                            txidMessage.put(0, (byte) 0x06);
                            txidMessage.put(1, (byte) 0x01);
                            txidMessage.putInt(2, TransmitterID);
                            sendBtMessage(txidMessage);
                        }
                        Pref.setInt("bridge_battery", ByteBuffer.wrap(buffer).get(11));
                        // PreferenceManager.getDefaultSharedPreferences(mContext).edit().putInt("bridge_battery", ByteBuffer.wrap(buffer).get(11)).apply();
                        last_battery_level = Pref.getInt("bridge_battery", -1);
                        //All is OK, so process it.
                        //first, tell the wixel it is OK to sleep.
                        Log.d(TAG, "setSerialDataToTransmitterRawData: Sending Data packet Ack, to put wixel to sleep");
                        ByteBuffer ackMessage = ByteBuffer.allocate(2);
                        ackMessage.put(0, (byte) 0x02);
                        ackMessage.put(1, (byte) 0xF0);
                        sendBtMessage(ackMessage);
                        //duplicates are already filtered in TransmitterData.create - so no need to filter here
                        poll_backoff = 0;
                        gotValidPacket();
                        Log.v(TAG, "setSerialDataToTransmitterRawData: Creating TransmitterData at " + timestamp);
                        processNewTransmitterData(TransmitterData.create(buffer, len, timestamp), timestamp);
                        CheckBridgeBattery.checkBridgeBattery();
                    }
                }
            } else {
                processNewTransmitterData(TransmitterData.create(buffer, len, timestamp), timestamp);
            }
        }
    }

    private void gotValidPacket() {
        retry_backoff = 0;
        lastPacketTime = tsl();
    }

    private synchronized void processNewTransmitterData(TransmitterData transmitterData, long timestamp) {
        if (transmitterData == null) {
            return;
        }

        final Sensor sensor = Sensor.currentSensor();
        if (sensor == null) {
            Log.i(TAG, "setSerialDataToTransmitterRawData: No Active Sensor, Data only stored in Transmitter Data");
            return;
        }

        //sensor.latest_battery_level = (sensor.latest_battery_level != 0) ? Math.min(sensor.latest_battery_level, transmitterData.sensor_battery_level) : transmitterData.sensor_battery_level;
        sensor.latest_battery_level = transmitterData.sensor_battery_level; // allow level to go up and down
        sensor.save();

        last_transmitter_Data = transmitterData;
        Log.d(TAG, "BgReading.create: new BG reading at " + timestamp + " with a timestamp of " + transmitterData.timestamp);
        BgReading.create(transmitterData.raw_data, transmitterData.filtered_data, this, transmitterData.timestamp);
    }

    private synchronized boolean areWeBonded(String hunt_address) {
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "mBluetoothAdapter is null");
            return true; // failsafe
        }
        final Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if ((pairedDevices != null) && (pairedDevices.size() > 0)) {
            for (BluetoothDevice device : pairedDevices) {
                final String address = device.getAddress();

                if (address != null) {
                    if (hunt_address.equals(address)) {
                        Log.d(TAG, hunt_address + " is bonded");
                        bondedState = hunt_address;
                        return true;
                    }
                }
            }
        }
        Log.d(TAG, hunt_address + " is not bonded");
        bondedState = "";
        return false;
    }

    private void watchdog() {
        if (last_time_seen == 0) return;
        if (prefs.getBoolean("bluetooth_watchdog", false)) {

            int bt_wdg_timer = Pref.getStringToInt("bluetooth_watchdog_timer", MAX_BT_WDG);

            if ((msSince(last_time_seen)) > bt_wdg_timer * MINUTE_IN_MS) {
                Log.d(TAG, "Use BT Watchdog timer=" + bt_wdg_timer);
                if (!JoH.isOngoingCall()) {
                    Log.e(TAG, "Watchdog triggered, attempting to reset bluetooth");
                    status("Watchdog triggered");
                    JoH.restartBluetooth(getApplicationContext());
                    last_time_seen = tsl();
                    watchdog_count++;
                    if (watchdog_count > 5) last_time_seen = 0;
                } else {
                    Log.e(TAG, "Delaying watchdog reset as phone call is ongoing.");
                }
            }
        }
    }

    private static boolean useScanning() {
        // TODO check location services
        return Pref.getBooleanDefaultFalse("bluetooth_use_scan");
    }

    private enum DISCOVERED {
        NULL,
        PENDING,
        COMPLETE
    }

    private class InternalManager extends BleManager {
        public InternalManager(@NonNull Context ctx) {
            super(ctx);
            Log.d(TAG, "InternalManager: Creating new InternalManager");
        }


        @Override
        protected void initialize() {
            Log.d(TAG, "internal manager initialize");
//            bleManager.setNotificationCallback(mCharacteristic).with((device, data) -> {
//                receivedLibreData(data.getValue());
//            });
            bleManager.enableNotifications(mCharacteristic).enqueue();

        }

        protected void safeWriteCharacteristic(@Nullable final BluetoothGattCharacteristic characteristic,
                                               @Nullable final Data data) {
            if (characteristic != null) {
                writeCharacteristic(characteristic, data, WRITE_TYPE_DEFAULT).enqueue();
            } else {
                Log.d(TAG, "Null characteristic passed to safeWriteCharacteristic, skipping write request.");
            }
        }
    }

    private static BluetoothDevice getDeviceFromMac(String mac) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) return null;
        return adapter.getRemoteDevice(mac);
    }

    private final ConnectionObserver connectionObserver = new ConnectionObserver() {

        @Override
        public void onDeviceConnecting(@NonNull BluetoothDevice device) {
            Log.d(TAG, "onDeviceConnecting: " + device.getAddress());
        }

        @Override
        public void onDeviceConnected(@NonNull BluetoothDevice device) {
            Log.d(TAG, "onDeviceConnected: " + device.getAddress());
            mConnectionState = STATE_CONNECTED;
            last_connected = tsl();
            ActiveBluetoothDevice.connected();

        }

        @Override
        public void onDeviceFailedToConnect(@NonNull BluetoothDevice device, int reason) {
            Log.d(TAG, "onDeviceFailedToConnect: " + device.getAddress());
            setRetryTimer();
        }

        @Override
        public void onDeviceReady(@NonNull BluetoothDevice device) {
            Log.d(TAG, "onDeviceReady: " + device.getAddress());
//            libreFirstSend();
        }

        @Override
        public void onDeviceDisconnecting(@NonNull BluetoothDevice device) {
            Log.d(TAG, "onDeviceDisconnecting: " + device.getAddress());
            mConnectionState = STATE_DISCONNECTING;
        }

        @Override
        public void onDeviceDisconnected(@NonNull BluetoothDevice device, int reason) {
            Log.d(TAG, "onDeviceDisconnected: " + device.getAddress());
            mConnectionState = STATE_DISCONNECTED;
            ActiveBluetoothDevice.disconnected();
            setRetryTimer();
        }
    };
}
