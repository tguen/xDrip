package com.eveningoutpost.dexdrip.ui;

/**
 * Created by jamorham on 29/09/2017.
 */
import com.eveningoutpost.dexdrip.utils.DexCollectionType;

import androidx.databinding.BaseObservable;


public class MicroStatusImpl extends BaseObservable implements MicroStatus {


    @Override
    public String gs(String id) {
        switch (id) {
            case "niceCollector":
                return DexCollectionType.getBestCollectorHardwareName();
            default:
                return "Unknown id:" + id;
        }
    }

    @Override
    public boolean bluetooth() { // Dexcom with Bluetooth except G7
        return DexCollectionType.hasBluetooth() && !DexCollectionType.isG7();
    }

}
