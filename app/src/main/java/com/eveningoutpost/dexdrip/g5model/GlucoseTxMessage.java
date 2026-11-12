package com.eveningoutpost.dexdrip.g5model;

import com.eveningoutpost.dexdrip.models.JoH;
import com.eveningoutpost.dexdrip.models.UserError;


/**
 * Created by jamorham on 25/11/2016.
 */

public class GlucoseTxMessage extends BaseMessage {

    static final byte opcode = 0x30;

    public GlucoseTxMessage() {
        init(opcode, 3);
        UserError.Log.d(TAG, "GlucoseTx dbg: " + JoH.bytesToHex(byteSequence));
    }
}
