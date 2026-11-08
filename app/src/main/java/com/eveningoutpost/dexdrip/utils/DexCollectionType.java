package com.eveningoutpost.dexdrip.utils;

import com.eveningoutpost.dexdrip.services.Ob1G5CollectionService;
import com.eveningoutpost.dexdrip.services.UiBasedCollector;
import com.eveningoutpost.dexdrip.utilitymodels.Pref;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import lombok.Getter;

import static com.eveningoutpost.dexdrip.g5model.Ob1G5StateMachine.shortTxId;


/**
 * Created by andy on 01/06/16.
 */
public enum DexCollectionType {

    None("None"),
    DexcomG5("DexcomG5"),
    DexcomG6("DexcomG6"), // currently pseudo
    UiBased("UiBased"),
    Disabled("Disabled"),
    ;

    @Getter
    String internalName;
    private static final Map<String, DexCollectionType> mapToInternalName;
    private static final HashSet<DexCollectionType> usesBluetooth = new HashSet<>();
    private static final HashSet<DexCollectionType> usesFiltered = new HashSet<>();
    private static final HashSet<DexCollectionType> isPassive = new HashSet<>();
    private static final HashSet<DexCollectionType> canNotStartStopOrCal = new HashSet<>();
    private static final HashSet<DexCollectionType> usesDexcomRaw = new HashSet<>();

    public static final String DEX_COLLECTION_METHOD = "dex_collection_method";

    public static boolean does_have_filtered = false; // TODO this could get messy with GC


    static {
        mapToInternalName = new HashMap<>();

        for (DexCollectionType dct : values()) {
            mapToInternalName.put(dct.internalName, dct);
        }

        Collections.addAll(usesBluetooth, DexcomG5);
        Collections.addAll(usesFiltered, DexcomG5); // Bluetooth and Wifi+Bluetooth need dynamic mode
        Collections.addAll(isPassive, UiBased);
        Collections.addAll(canNotStartStopOrCal, UiBased, Disabled); // Collectors that cannot start/stop sensor or submit calibration
        Collections.addAll(usesDexcomRaw, DexcomG5);
    }


    DexCollectionType(String name) {
        this.internalName = name;
    }


    public static DexCollectionType getType(String dexCollectionType) {

        if (mapToInternalName.containsKey(dexCollectionType))
            return mapToInternalName.get(dexCollectionType);
        else
            return None;
    }

    public static DexCollectionType getDexCollectionType() {
        return getType(Pref.getString(DEX_COLLECTION_METHOD, ""));
    }

    public static void setDexCollectionType(DexCollectionType t) {
        Pref.setString(DEX_COLLECTION_METHOD, t.internalName);
    }

    public static boolean isG7() {
        return getBestCollectorHardwareName().equals("G7");
    }

    public static boolean hasBluetooth() {
        return usesBluetooth.contains(getDexCollectionType());
    }

    public static boolean isNewerCollector() {
        return false;
    }

    public static boolean isAlwaysNativeCal() {
        return false;
    }

    public static boolean hasBattery() {
        return false;
    }

    public static boolean hasDexcomRaw() {
        return hasDexcomRaw(getDexCollectionType());
    }

    public static boolean hasDexcomRaw(DexCollectionType type) {
        return usesDexcomRaw.contains(type);
    }

    public static boolean isFlakey() {
        return getDexCollectionType() == DexCollectionType.DexcomG5;
    }

    public static boolean hasFiltered() {
        return does_have_filtered || usesFiltered.contains(getDexCollectionType());
    }

    public static Class<?> getCollectorServiceClass() {
        final DexCollectionType type = getDexCollectionType();
        switch (type) {
            case DexcomG5:
                return Ob1G5CollectionService.class;
            case UiBased:
                return UiBasedCollector.class;
            default:
                return null;
        }
    }

    // using reflection to access static methods, could cache if needed maybe

    public static Boolean getServiceRunningState() {
        final Boolean result = getPhoneServiceRunningState();
        // if phone running don't bother checking wear
        if ((result != null) && result) return true;
        return false;
    }

    public static Boolean getPhoneServiceRunningState() {
        try {
            // TODO handle wear collection
            final Method method = getCollectorServiceClass().getMethod("isRunning");
            return (Boolean) method.invoke(null);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean getLocalServiceCollectingState() {
        try {
            final Method method = getCollectorServiceClass().getMethod("isCollecting");
            return (boolean) method.invoke(null);
        } catch (Exception e) {
            return false; // default to not blocking a restart
        }
    }


    public static String getBestCollectorHardwareName() {
        final DexCollectionType dct = getDexCollectionType();
        switch (dct) {
            case DexcomG5:
                if (Ob1G5CollectionService.usingNativeMode()) {
                    return Ob1G5CollectionService.usingG6() ? (shortTxId() ? "G7" : "G6 Native") : "G5 Native";
                }
                return dct.name();
            default:
                return dct.name();
        }
    }

    public static int getBestBridgeBatteryPercent() {
        if (DexCollectionType.hasBattery()) {
            // TODO this logic needs double checking for multi collector types and others
                    return Pref.getInt("bridge_battery", -1);
        } else {
            return -2;
        }
    }

    public static String getBestBridgeBatteryPercentString() {
        final int battery = getBestBridgeBatteryPercent();
        if (battery > 0) {
            return "" + battery;
        } else {
            return "";
        }
    }

    public boolean isPassive() {
        return isPassive.contains(this);
    }
    public boolean canNotStartStopOrCal() {
        return canNotStartStopOrCal.contains(this);
    }

    public long getSamplePeriod() {
        return getCollectorSamplePeriod(this);
    }

    public static long getCollectorSamplePeriod(final DexCollectionType type) {
                return 300_000; // 5 minutes
    }

    public static long getCurrentSamplePeriod() {
        return getDexCollectionType().getSamplePeriod();
    }

    public static long getCurrentDeduplicationPeriod() {
        final long period = getDexCollectionType().getSamplePeriod();
        return period - (period / 6); // TODO this needs more validation
    }

}
