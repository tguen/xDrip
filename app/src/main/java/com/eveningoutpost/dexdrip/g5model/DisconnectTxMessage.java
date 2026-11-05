package com.eveningoutpost.dexdrip.g5model;

import com.eveningoutpost.dexdrip.models.JoH;
import com.eveningoutpost.dexdrip.models.UserError;

import java.nio.ByteBuffer;

/**
 * Created by joeginley on 3/16/16.
 */
public class DisconnectTxMessage extends BaseMessage {
    byte opcode = 0x09;

    public DisconnectTxMessage() {
        data = ByteBuffer.allocate(1);
        data.put(opcode);

        byteSequence = data.array();
        UserError.Log.d(TAG,"DisconnectTX: "+ JoH.bytesToHex(byteSequence));
    }
}
