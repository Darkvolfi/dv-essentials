package com.darkvolfi.dvmap.network;

import com.darkvolfi.dvmap.DVMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Only the server knows where structures are: when a player stands inside one, its client (with DV Map) is told,
 * and the map keeps a marker there. Client-side the list is saved per world, so this only sends what's new this session.
 */
@Mod.EventBusSubscriber(modid = DVMap.MOD_ID)
public final class StructureDiscovery {
    private static final int INTERVAL_TICKS = 40;
    /** Too common or hidden to be worth a marker: mineshafts run under everything, treasure would be given away. */
    private static final Set<String> SKIPPED = Set.of(
            "minecraft:mineshaft", "minecraft:mineshaft_mesa", "minecraft:buried_treasure", "minecraft:nether_fossil");
    private static final Map<UUID, Set<String>> SENT = new HashMap<>();

    private StructureDiscovery() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if ((player.tickCount + player.getId()) % INTERVAL_TICKS != 0) return;
        if (!MapNetwork.CHANNEL.isRemotePresent(player.connection.connection)) return;

        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();
        if (!level.structureManager().hasAnyStructureAt(pos)) return;
        for (Structure structure : level.structureManager().getAllStructuresAt(pos).keySet()) {
            ResourceLocation id = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(structure);
            if (id == null || SKIPPED.contains(id.toString())) continue;
            StructureStart start = level.structureManager().getStructureAt(pos, structure);
            if (!start.isValid()) continue;
            BoundingBox box = start.getBoundingBox();
            String key = id + "@" + box.getCenter().asLong();
            if (!SENT.computeIfAbsent(player.getUUID(), u -> new HashSet<>()).add(key)) continue;
            MapNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new StructurePacket(
                    id, level.dimension().location(), box.getCenter().getX(), box.getCenter().getZ()));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }
}
