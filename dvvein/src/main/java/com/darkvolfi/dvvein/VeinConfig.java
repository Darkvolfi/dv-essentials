package com.darkvolfi.dvvein;

import net.minecraftforge.common.ForgeConfigSpec;

public final class VeinConfig {
    // Server: per world, synced to clients so the preview matches what will be mined.
    public static final ForgeConfigSpec SERVER_SPEC;
    public static final ForgeConfigSpec.IntValue MAX_BLOCKS;
    public static final ForgeConfigSpec.DoubleValue EXHAUSTION_PER_BLOCK;
    public static final ForgeConfigSpec.BooleanValue GROUP_ORES;

    // Client.
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final ForgeConfigSpec.BooleanValue SHOW_PREVIEW;

    public static final int DEFAULT_MAX_BLOCKS = 64;

    static {
        ForgeConfigSpec.Builder s = new ForgeConfigSpec.Builder();
        MAX_BLOCKS = s.comment("Most blocks broken at once, including the one you mine.")
                .defineInRange("maxBlocks", DEFAULT_MAX_BLOCKS, 1, 1024);
        EXHAUSTION_PER_BLOCK = s.comment("Extra hunger exhaustion per additional block (vanilla mining adds 0.005).")
                .defineInRange("exhaustionPerBlock", 0.02, 0.0, 4.0);
        GROUP_ORES = s.comment("Treat ores sharing a forge:ores/* tag as the same block (e.g. iron and deepslate iron).")
                .define("groupOres", true);
        SERVER_SPEC = s.build();

        ForgeConfigSpec.Builder c = new ForgeConfigSpec.Builder();
        SHOW_PREVIEW = c.comment("Outline the blocks that will be broken while the key is held.")
                .define("showPreview", true);
        CLIENT_SPEC = c.build();
    }

    /** Server values exist only while a world with this mod on the server is open. */
    public static int maxBlocks() {
        return SERVER_SPEC.isLoaded() ? MAX_BLOCKS.get() : DEFAULT_MAX_BLOCKS;
    }

    public static boolean groupOres() {
        return !SERVER_SPEC.isLoaded() || GROUP_ORES.get();
    }

    private VeinConfig() {}
}
