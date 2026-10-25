package com.darkvolfi.dvharvest;

import net.minecraft.client.Minecraft;

/** Client-only helpers, kept out of {@link Harvest} so the server never loads client classes. */
final class HarvestClient {
    private HarvestClient() {}

    static boolean serverHasMod() {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null && DVHarvest.CHANNEL.isRemotePresent(connection.getConnection());
    }
}
