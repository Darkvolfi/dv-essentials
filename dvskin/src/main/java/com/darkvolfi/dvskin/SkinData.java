package com.darkvolfi.dvskin;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * One player's look as it travels over the network: arm model and up to three PNGs (null: not chosen).
 * The client sends it in chunks, the server checks it and passes it on as a whole.
 */
public record SkinData(boolean slim, byte[] skin, byte[] cape, byte[] elytra) {
    /** A standard 64×64 PNG re-encoded by the game stays far below this. */
    public static final int MAX_PNG = 24 * 1024;
    public static final int MAX_BYTES = 1 + 3 * (4 + MAX_PNG);
    /** Client-to-server packets are capped near 32 KB; each chunk stays below that. */
    public static final int CHUNK = 30000;
    public static final int MAX_CHUNKS = (MAX_BYTES + CHUNK - 1) / CHUNK;

    public static final int SKIN = 0, CAPE = 1, ELYTRA = 2;

    public boolean isEmpty() {
        return skin == null && cape == null && elytra == null;
    }

    public byte[] get(int slot) {
        return slot == SKIN ? skin : slot == CAPE ? cape : elytra;
    }

    public byte[] toBytes() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeBoolean(slim);
            for (byte[] png : new byte[][] {skin, cape, elytra}) {
                out.writeInt(png == null ? 0 : png.length);
                if (png != null) out.write(png);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e); // in-memory stream
        }
        return bytes.toByteArray();
    }

    /** Reads what {@link #toBytes()} wrote; throws on anything malformed or over the limits. */
    public static SkinData fromBytes(byte[] data) throws IOException {
        if (data.length > MAX_BYTES) throw new IOException("too big");
        try (DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(data))) {
            boolean slim = in.readBoolean();
            byte[][] pngs = new byte[3][];
            for (int i = 0; i < 3; i++) {
                int length = in.readInt();
                if (length < 0 || length > MAX_PNG) throw new IOException("bad length");
                if (length > 0) {
                    pngs[i] = new byte[length];
                    in.readFully(pngs[i]);
                }
            }
            if (in.available() > 0) throw new IOException("trailing bytes");
            return new SkinData(slim, pngs[0], pngs[1], pngs[2]);
        }
    }
}
