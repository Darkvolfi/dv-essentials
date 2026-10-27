package com.darkvolfi.dvvein;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Server side: when a player with the key held breaks a block, break the rest of the vein too. */
@Mod.EventBusSubscriber(modid = DVVein.MOD_ID)
public final class VeinMiner {
    private static final Set<UUID> keyHeld = new HashSet<>();
    private static final Map<UUID, VeinShape> shapes = new HashMap<>();

    /** Set while breaking a vein: guards against our own break events. */
    private static Level miningLevel;
    /**
     * Drops and XP appearing in this box go straight to the collector. Kept until the end of the tick,
     * because the clicked block's own drops spawn only after the break event returns.
     */
    private static ServerPlayer collector;
    private static Level collectLevel;
    private static AABB collectBox;

    private VeinMiner() {}

    /** Server side: whether the player is holding the vein key. Also used by harvesting and shearing. */
    public static boolean isKeyHeld(Player player) {
        return keyHeld.contains(player.getUUID());
    }

    public static void setKeyState(ServerPlayer player, boolean held, VeinShape shape) {
        if (held) keyHeld.add(player.getUUID());
        else keyHeld.remove(player.getUUID());
        shapes.put(player.getUUID(), shape);
    }

    // Low priority: let protection mods cancel the original break first.
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || miningLevel != null) return;
        if (!(event.getPlayer() instanceof ServerPlayer player) || !keyHeld.contains(player.getUUID())) return;
        if (!(event.getLevel() instanceof Level level)) return;

        BlockState origin = event.getState();
        if (!canVein(player, origin)) return;

        VeinShape shape = shapes.getOrDefault(player.getUUID(), VeinShape.SHAPELESS);
        List<BlockPos> vein = shape.blocks(level, player, event.getPos(), origin, VeinConfig.MAX_BLOCKS.get(), VeinConfig.GROUP_ORES.get());
        AABB box = new AABB(event.getPos());
        for (BlockPos pos : vein) box = box.minmax(new AABB(pos));
        collector = player;
        collectLevel = level;
        collectBox = box.inflate(1.0);
        miningLevel = level;
        try {
            for (BlockPos pos : vein.subList(1, vein.size())) { // first entry is the block being broken now
                if (!canContinue(player, player.getMainHandItem())) break;
                if (player.gameMode.destroyBlock(pos) && !player.isCreative()) {
                    player.causeFoodExhaustion(VeinConfig.EXHAUSTION_PER_BLOCK.get().floatValue());
                }
            }
        } finally {
            miningLevel = null;
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

    /** Stops before the tool breaks or the player runs out of hunger. Shared with sheep shearing. */
    static boolean canContinue(Player player, ItemStack tool) {
        if (player.isCreative()) return true;
        if (tool.isDamageableItem() && tool.getMaxDamage() - tool.getDamageValue() <= 1) return false; // keep the tool
        return player.getFoodData().getFoodLevel() > 0;
    }

    /** Drops and XP from the vein go straight into the inventory (what doesn't fit drops at the player's feet). */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (collector == null || event.getLevel() != collectLevel) return;
        if (!collectBox.contains(event.getEntity().position())) return;
        if (event.getEntity() instanceof ItemEntity item) {
            ItemHandlerHelper.giveItemToPlayer(collector, item.getItem().copy());
            event.setCanceled(true);
        } else if (event.getEntity() instanceof ExperienceOrb orb) {
            collector.giveExperiencePoints(orb.getValue());
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        collector = null;
        collectLevel = null;
        collectBox = null;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        keyHeld.remove(event.getEntity().getUUID());
        shapes.remove(event.getEntity().getUUID());
    }
}
