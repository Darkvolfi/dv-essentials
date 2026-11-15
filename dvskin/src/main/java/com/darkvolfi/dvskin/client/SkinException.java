package com.darkvolfi.dvskin.client;

/** A problem to show the player; {@link #key} is the message's language key under dvskin.error. */
public class SkinException extends Exception {
    public final String key;

    public SkinException(String key) {
        super(key);
        this.key = key;
    }
}
