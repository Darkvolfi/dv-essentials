package com.darkvolfi.dvgrave;

import com.darkvolfi.dvgrave.compat.CosArmorCompat;
import com.darkvolfi.dvgrave.compat.CuriosCompat;
import com.darkvolfi.dvgrave.compat.SlotsCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Death, in the order the game runs it: the death event (where every stack still is), the experience drop,
 * then the drops event, which every mod has filled by the lowest priority. The grave takes those drops, and
 * any of the player's own stacks that still appear at the death spot in the same tick (mods that spawn their
 * drops themselves).
 */
@Mod.EventBusSubscriber(modid = DVGrave.MOD_ID)
public final class GraveEvents {
    /** Item entities this close to the death spot in the death tick belong in the grave. */
    private static final double LATE_DROP_RANGE = 4.0;

    private static final class Pending {
        final List<GraveItem> where = new ArrayList<>();
        /** Snapshot entries no drop has matched yet; late drops are matched against them too. */
        List<GraveItem> unmatched = List.of();
        final ResourceKey<Level> dimension;
        final Vec3 deathPos;
        final long tick;
        int xp;
        BlockPos grave;

        Pending(ServerPlayer player) {
            dimension = player.level().dimension();
            deathPos = player.position();
            tick = player.level().getGameTime();
        }
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private GraveEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Pending pending = new Pending(player);
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) pending.where.add(new GraveItem(stack.copy(), GraveItem.INVENTORY, "", i, false));
        }
        if (ModList.get().isLoaded("dvslots")) SlotsCompat.snapshot(player, pending.where);
        if (ModList.get().isLoaded("curios")) CuriosCompat.snapshot(player, pending.where);
        if (ModList.get().isLoaded("cosmeticarmorreworked")) CosArmorCompat.snapshot(player, pending.where);
        PENDING.put(player.getUUID(), pending);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onExperience(LivingExperienceDropEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Pending pending = PENDING.get(player.getUUID());
        if (pending == null) return;
        // With keepInventory the player keeps their levels; a grave holding them as well would hand them out twice.
        if (player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) return;
        switch (GraveConfig.XP.get()) {
            case ALL -> pending.xp = totalPoints(player);
            case VANILLA -> pending.xp = event.getDroppedExperience();
            case NONE -> {
                return;
            }
        }
        event.setDroppedExperience(0);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Pending pending = PENDING.get(player.getUUID());
        if (pending == null) return;

        List<GraveItem> stacks = new ArrayList<>();
        List<GraveItem> unmatched = new ArrayList<>(pending.where);
        pending.unmatched = unmatched;
        for (ItemEntity drop : event.getDrops()) {
            ItemStack stack = drop.getItem();
            if (!stack.isEmpty()) stacks.add(origin(stack, unmatched));
        }
        if (stacks.isEmpty() && pending.xp <= 0) return;

        ServerLevel level = player.serverLevel();
        BlockPos pos = findSpot(level, BlockPos.containing(pending.deathPos));
        if (pos == null) {
            // Nowhere to put it: everything drops as usual, and so does the experience taken for the grave.
            if (pending.xp > 0) ExperienceOrb.award(level, pending.deathPos, pending.xp);
            pending.xp = 0;
            return;
        }
        event.getDrops().clear();
        level.setBlockAndUpdate(pos, DVGrave.GRAVE.get().defaultBlockState()
                .setValue(GraveBlock.FACING, player.getDirection().getOpposite()));
        if (level.getBlockEntity(pos) instanceof GraveBlockEntity grave) {
            grave.fill(player, stacks, pending.xp);
            pending.grave = pos;
            player.sendSystemMessage(Component.translatable("dvgrave.died", pos.getX(), pos.getY(), pos.getZ(),
                    Component.translatable(level.dimension().location().toLanguageKey("dimension"))).withStyle(ChatFormatting.GRAY));
        }
    }

    /** Pairs a dropped stack with the slot it was in at death; unknown ones (added by mods) just go to the inventory. */
    private static GraveItem origin(ItemStack stack, List<GraveItem> unmatched) {
        GraveItem where = match(stack, unmatched);
        return where != null ? where : GraveItem.other(stack);
    }

    /** The slot this exact stack was in at death, if no drop has claimed it yet; null otherwise. */
    private static GraveItem match(ItemStack stack, List<GraveItem> unmatched) {
        for (Iterator<GraveItem> it = unmatched.iterator(); it.hasNext(); ) {
            GraveItem where = it.next();
            if (ItemStack.matches(where.stack(), stack)) {
                it.remove();
                return where.withStack(stack);
            }
        }
        return null;
    }

    /**
     * A late drop is taken only if it is a stack the player was carrying at death that no drop accounted for,
     * so items thrown by others or dropped by mobs and blocks nearby in the same tick stay on the ground.
     */
    @SubscribeEvent
    public static void onItemJoin(EntityJoinLevelEvent event) {
        if (PENDING.isEmpty() || !(event.getEntity() instanceof ItemEntity item) || !(event.getLevel() instanceof ServerLevel level)) return;
        for (Map.Entry<UUID, Pending> entry : PENDING.entrySet()) {
            Pending pending = entry.getValue();
            if (pending.grave == null || pending.tick != level.getGameTime() || pending.dimension != level.dimension()) continue;
            if (item.position().distanceTo(pending.deathPos) > LATE_DROP_RANGE) continue;
            if (item.getOwner() != null && !item.getOwner().getUUID().equals(entry.getKey())) continue; // thrown by someone else
            if (!(level.getBlockEntity(pending.grave) instanceof GraveBlockEntity grave)) continue;
            GraveItem where = match(item.getItem().copy(), pending.unmatched);
            if (where == null) continue;
            grave.add(where);
            event.setCanceled(true);
            return;
        }
    }

    /**
     * The owner can always open their own grave, also when they died on land a claim mod protects (DV Team or
     * another): opened here, before those mods see the click and refuse it. Everyone else follows the claim rules.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickGrave(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSecondaryUseActive()) return;
        if (!(event.getLevel().getBlockEntity(event.getPos()) instanceof GraveBlockEntity grave) || !grave.isOwner(player)) return;
        if (event.getHand() == InteractionHand.MAIN_HAND) GraveBlock.open(player, event.getPos(), grave);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) PENDING.clear();
    }

    /**
     * Where the stone goes: the death spot if it is free, else the nearest free spot above (out of lava or water),
     * then around it. Never below the world or into a block with contents.
     */
    private static BlockPos findSpot(ServerLevel level, BlockPos death) {
        int minY = level.getMinBuildHeight() + 1;
        int maxY = level.getMaxBuildHeight() - 2;
        BlockPos start = new BlockPos(death.getX(), Math.max(minY, Math.min(maxY, death.getY())), death.getZ());
        for (int dy = 0; start.getY() + dy <= maxY && dy <= 64; dy++) {
            if (free(level, start.above(dy), true)) return start.above(dy);
        }
        for (int r = 1; r <= 4; r++) {
            for (BlockPos p : BlockPos.betweenClosed(start.offset(-r, -r, -r), start.offset(r, r, r))) {
                if (p.getY() >= minY && p.getY() <= maxY && free(level, p, false)) return p.immutable();
            }
        }
        BlockState at = level.getBlockState(start);
        return !at.hasBlockEntity() && at.getDestroySpeed(level, start) >= 0 ? start : null;
    }

    private static boolean free(ServerLevel level, BlockPos pos, boolean dry) {
        BlockState state = level.getBlockState(pos);
        if (!state.canBeReplaced() || state.hasBlockEntity()) return false;
        return !dry || state.getFluidState().isEmpty();
    }

    /** All experience points the player has: full levels plus the progress into the next one. */
    private static int totalPoints(Player player) {
        int level = player.experienceLevel;
        long points;
        if (level <= 16) points = (long) level * level + 6L * level;
        else if (level <= 31) points = (long) (2.5 * level * level - 40.5 * level + 360);
        else points = (long) (4.5 * level * level - 162.5 * level + 2220);
        points += Math.round(player.experienceProgress * player.getXpNeededForNextLevel());
        return (int) Math.min(Integer.MAX_VALUE, points);
    }
}
