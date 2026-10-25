package com.darkvolfi.dvvein;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Server side: when a player with the key held breaks a block, break the rest of the vein too. */
@Mod.EventBusSubscriber(modid = DVVein.MOD_ID)
public final class VeinMiner {
    private static final Set<UUID> keyHeld = new HashSet<>();

    /** Set while breaking a vein: guards against our own break events and gathers the drops. */
    private static Level miningLevel;
    private static Vec3 dropPoint;

    private VeinMiner() {}

    public static void setKeyHeld(ServerPlayer player, boolean held) {
        if (held) keyHeld.add(player.getUUID());
        else keyHeld.remove(player.getUUID());
    }

    // Low priority: let protection mods cancel the original break first.
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || miningLevel != null) return;
        if (!(event.getPlayer() instanceof ServerPlayer player) || !keyHeld.contains(player.getUUID())) return;
        if (!(event.getLevel() instanceof Level level)) return;

        BlockState origin = event.getState();
        if (!canVein(player, origin)) return;

        List<BlockPos> vein = VeinFinder.find(level, event.getPos(), origin, VeinConfig.MAX_BLOCKS.get(), VeinConfig.GROUP_ORES.get());
        miningLevel = level;
        dropPoint = Vec3.atCenterOf(event.getPos());
        try {
            for (BlockPos pos : vein.subList(1, vein.size())) { // first entry is the block being broken now
                if (!canContinue(player)) break;
                if (player.gameMode.destroyBlock(pos) && !player.isCreative()) {
                    player.causeFoodExhaustion(VeinConfig.EXHAUSTION_PER_BLOCK.get().floatValue());
                }
            }
        } finally {
            miningLevel = null;
            dropPoint = null;
        }
    }

    /**
     * Without the right tool the blocks would drop nothing, so a survival vein would be wasted.
     * Creative drops nothing anyway, so any block goes. Shared with the client preview.
     */
    public static boolean canVein(Player player, BlockState origin) {
        if (origin.isAir()) return false;
        return player.isCreative() || player.hasCorrectToolForDrops(origin);
    }

    private static boolean canContinue(ServerPlayer player) {
        if (player.isCreative()) return true;
        ItemStack tool = player.getMainHandItem();
        if (tool.isDamageableItem() && tool.getMaxDamage() - tool.getDamageValue() <= 1) return false; // keep the tool
        return player.getFoodData().getFoodLevel() > 0;
    }

    /** Drops and XP from the extra blocks appear where the player started mining. */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (miningLevel == null || event.getLevel() != miningLevel) return;
        if (event.getEntity() instanceof ItemEntity || event.getEntity() instanceof ExperienceOrb) {
            event.getEntity().setPos(dropPoint);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        keyHeld.remove(event.getEntity().getUUID());
    }
}
