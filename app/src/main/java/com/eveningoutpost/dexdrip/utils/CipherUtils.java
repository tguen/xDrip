package com.eveningoutpost.dexdrip.utils;

/**
 * Created by jamorham on 06/01/16.
 */
import android.util.Log;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;


public class CipherUtils {

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    static final String TAG = "jamorham cip";
    static final byte[] errorbyte = {};

    public static byte[] encrypt(byte[] ivBytes, byte[] keyBytes, byte[] textBytes) {
        try {
            AlgorithmParameterSpec ivSpec = new IvParameterSpec(ivBytes);
            SecretKeySpec newKey = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, newKey, ivSpec);
            return cipher.doFinal(textBytes);
        } catch (Exception e) {
            Log.e(TAG, "Error during encryption: " + e.toString());
            return errorbyte;
        }
    }

    public static byte[] decrypt(byte[] ivBytes, byte[] keyBytes, byte[] textBytes) {
        try {
            AlgorithmParameterSpec ivSpec = new IvParameterSpec(ivBytes);
            SecretKeySpec newKey = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, newKey, ivSpec);
            return cipher.doFinal(textBytes);
        } catch (Exception e) {
            return errorbyte;
        }
    }

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static String getSHA256(byte[] mydata) {
        try {
            MessageDigest digest = java.security.MessageDigest
                    .getInstance("SHA256");
            digest.update(mydata);
            return bytesToHex(digest.digest()).toLowerCase();
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "SHA hash exception: " + e.toString());
            return null;
        }
    }

    public static String getSHA256(String mykey) {
        try {
            MessageDigest digest = java.security.MessageDigest
                    .getInstance("SHA256");
            digest.update(mykey.getBytes(Charset.forName("UTF-8")));
            return bytesToHex(digest.digest()).toLowerCase();
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "SHA hash exception: " + e.toString());
            return null;
        }
    }

    public static byte[] encryptBytes(byte[] plainText, byte[] keyBytes) {
        byte[] ivBytes = new byte[16];

        if ((keyBytes == null) || (keyBytes.length != 16)) {
            Log.e(TAG, "Invalid Keybytes length!");
            return errorbyte;
        }
        SecureRandom sr = new SecureRandom();
        sr.nextBytes(ivBytes);
        byte[] cipherData = CipherUtils.encrypt(ivBytes, keyBytes, plainText);
        byte[] destination = new byte[cipherData.length + ivBytes.length];
        System.arraycopy(ivBytes, 0, destination, 0, ivBytes.length);
        System.arraycopy(cipherData, 0, destination, ivBytes.length, cipherData.length);
        return destination;
    }
    public static byte[] decryptBytes(byte[] cipherData,byte[] keyBytes) {
        byte[] ivBytes = new byte[16];
        if (cipherData.length < ivBytes.length) return errorbyte;
        if ((keyBytes==null) || (keyBytes.length != 16)) {
            Log.e(TAG, "Invalid Keybytes length!");
            return errorbyte;
        }
        System.arraycopy(cipherData, 0, ivBytes, 0, ivBytes.length);
        byte[] destination = new byte[cipherData.length - ivBytes.length];
        System.arraycopy(cipherData, ivBytes.length, destination, 0, destination.length);
        return CipherUtils.decrypt(ivBytes, keyBytes, destination);
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

    public static String getRandomHexKey() {
        return bytesToHex(getRandomKey());
    }
}
