package com.darkvolfi.dvlens.client;

import com.darkvolfi.dvlens.DVLens;
import com.darkvolfi.dvlens.network.BlockInfoReply;
import com.darkvolfi.dvlens.network.BlockInfoRequest;
import com.darkvolfi.dvlens.network.EntityInfoReply;
import com.darkvolfi.dvlens.network.EntityInfoRequest;
import com.darkvolfi.dvlens.network.LensNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Objects;

/**
 * Latest server answer about the entity or block entity under the crosshair, refreshed twice a second
 * while looking at it. Nothing is asked when the server doesn't have DV Lens.
 */
@Mod.EventBusSubscriber(modid = DVLens.MOD_ID, value = Dist.CLIENT)
public final class ServerInfo {
    private static final int REFRESH_TICKS = 10;

    /** An Integer entity id, a BlockPos, or null. */
    private static Object target;
    private static EntityInfoReply entity;
    private static BlockInfoReply block;
    private static int timer;

    private ServerInfo() {}

    /** The server's info for this entity, or null if the server doesn't have DV Lens or hasn't answered yet. */
    public static EntityInfoReply get(Entity e) {
        return entity != null && entity.entityId() == e.getId() ? entity : null;
    }

    public static BlockInfoReply get(BlockPos pos) {
        return block != null && block.pos().equals(pos) ? block : null;
    }

    public static void receive(EntityInfoReply reply) {
        if (Integer.valueOf(reply.entityId()).equals(target)) entity = reply;
    }

    public static void receive(BlockInfoReply reply) {
        if (reply.pos().equals(target)) block = reply;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        Object now = null;
        HitResult hit = mc.hitResult;
        if (hit instanceof EntityHitResult e) {
            now = e.getEntity().getId();
        } else if (hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK
                && mc.level != null && mc.level.getBlockEntity(b.getBlockPos()) != null) {
            now = b.getBlockPos().immutable(); // only block entities have anything the server could add
        }
        if (!Objects.equals(now, target)) {
            target = now;
            entity = null;
            block = null;
            timer = 0;
        }
        if (target == null || mc.getConnection() == null || !LensNetwork.CHANNEL.isRemotePresent(mc.getConnection().getConnection())) return;
        if (timer-- <= 0) {
            timer = REFRESH_TICKS;
            if (target instanceof Integer id) LensNetwork.CHANNEL.sendToServer(new EntityInfoRequest(id));
            else LensNetwork.CHANNEL.sendToServer(new BlockInfoRequest((BlockPos) target));
        }
    }
}
