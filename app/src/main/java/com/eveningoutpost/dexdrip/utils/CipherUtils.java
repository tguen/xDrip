package com.eveningoutpost.dexdrip.utils;

/**
 * Created by jamorham on 06/01/16.
 */
import java.security.SecureRandom;


public class CipherUtils {

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static byte[] getRandomKey(final int length) {
        byte[] keybytes = new byte[length];
        SecureRandom sr = new SecureRandom();
        sr.nextBytes(keybytes);
        return keybytes;
    }

    public static byte[] getRandomKey() {
        return getRandomKey(16);
    }

}
