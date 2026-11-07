package com.eveningoutpost.dexdrip.ui;

/**
 * Created by jamorham on 29/09/2017.
 */
import com.eveningoutpost.dexdrip.g5model.FirmwareCapability;
import com.eveningoutpost.dexdrip.utils.DexCollectionType;

import androidx.databinding.BaseObservable;

import static com.eveningoutpost.dexdrip.services.Ob1G5CollectionService.getTransmitterID;
import static com.eveningoutpost.dexdrip.utils.DexCollectionType.getBestCollectorHardwareName;


public class MicroStatusImpl extends BaseObservable implements MicroStatus {


    @Override
    public String gs(String id) {

        switch (id) {

            case "niceCollector":
                return DexCollectionType.getBestCollectorHardwareName();

            case "bestBridgeBattery":
                return DexCollectionType.getBestBridgeBatteryPercentString();

            default:
                return "Unknown id:" + id;
        }
    }

    @Override
    public boolean bluetooth() { // Dexcom with Bluetooth except G7
        return DexCollectionType.hasBluetooth() && !DexCollectionType.isG7();
    }

    @Override
    public boolean sessionStartTime() { // This is false only if we are using G7
        return !DexCollectionType.isG7();
    }

    @Override
    public boolean xmitterBattery() {
        return false;
    }

    @Override
    public boolean rereadTx() {
        return getBestCollectorHardwareName().equals("G6 Native") && FirmwareCapability.isTransmitterModified(getTransmitterID());
    }

}
