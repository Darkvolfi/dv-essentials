package com.darkvolfi.dvteam.protection;

import com.darkvolfi.dvteam.DVTeam;
import com.darkvolfi.dvteam.data.ClanData;
import com.darkvolfi.dvteam.data.ClanTypes.Perm;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Settlement protection: each action maps to one {@link Perm}, checked against the settlement the block or
 * entity is in. High priority, so cancelled actions never reach other mods (e.g. DV Vein's harvesting).
 */
@Mod.EventBusSubscriber(modid = DVTeam.MOD_ID)
public final class ClaimProtection {
    private ClaimProtection() {}

    private static boolean allowed(ServerLevel level, Player player, BlockPos pos, Perm perm) {
        String dim = level.dimension().location().toString();
        ClanData data = ClanData.get(level.getServer());
        if (data.allows(level.getServer(), player, dim, pos.getX() >> 4, pos.getZ() >> 4, perm)) return true;
        ClanData.Settlement s = data.settlementAt(dim, pos.getX() >> 4, pos.getZ() >> 4);
        ClanData.Clan clan = s == null ? null : data.clan(s.clanId);
        if (s != null && clan != null) {
            player.displayClientMessage(Component.translatable("dvteam.msg.protected", s.name, clan.name)
                    .withStyle(ChatFormatting.RED), true);
        }
        return false;
    }

    /** Crops and the like count as farming, so planting and harvesting can be allowed without building. */
    private static boolean isFarming(BlockState state) {
        Block b = state.getBlock();
        return b instanceof CropBlock || b instanceof StemBlock || b instanceof NetherWartBlock
                || b instanceof CocoaBlock || b instanceof SweetBerryBushBlock;
    }

    private static Perm permForBlockUse(ServerLevel level, BlockPos pos, BlockState state) {
        Block b = state.getBlock();
        if (b instanceof DoorBlock || b instanceof TrapDoorBlock || b instanceof FenceGateBlock
                || b instanceof ButtonBlock || b instanceof LeverBlock || b instanceof PressurePlateBlock) {
            return Perm.DOORS;
        }
        if (isFarming(state)) return Perm.FARMING;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof Container || b instanceof BaseEntityBlock && state.getMenuProvider(level, pos) != null) {
            return Perm.CONTAINERS;
        }
        return Perm.BUILD;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.getPlayer() == null) return;
        Perm perm = isFarming(event.getState()) ? Perm.FARMING : Perm.BUILD;
        if (!allowed(level, event.getPlayer(), event.getPos(), perm)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Player player)) return;
        Perm perm = isFarming(event.getPlacedBlock()) ? Perm.FARMING : Perm.BUILD;
        if (!allowed(level, player, event.getPos(), perm)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMultiBlockPlace(BlockEvent.EntityMultiPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Player player)) return;
        for (BlockSnapshot snap : event.getReplacedBlockSnapshots()) {
            if (!allowed(level, player, snap.getPos(), Perm.BUILD)) {
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        ClanData data = ClanData.get(level.getServer());
        String dim = level.dimension().location().toString();
        if (event.getEntity() instanceof Player player) {
            if (!data.allows(level.getServer(), player, dim, pos.getX() >> 4, pos.getZ() >> 4, Perm.FARMING)) event.setCanceled(true);
        } else if (data.settlementAt(dim, pos.getX() >> 4, pos.getZ() >> 4) != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        ItemStack held = event.getItemStack();
        Perm perm = permForBlockUse(level, pos, state);
        // Bone meal on crops is farming too.
        if (perm == Perm.BUILD && state.getBlock() instanceof BonemealableBlock && isFarming(state)) perm = Perm.FARMING;
        if (allowed(level, event.getEntity(), pos, perm)) return;
        event.setUseBlock(Event.Result.DENY);
        // Still let players eat or use items that don't touch the block.
        if (!held.isEdible()) {
            event.setUseItem(Event.Result.DENY);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getItemStack().getItem() instanceof BucketItem)) return;
        Player player = event.getEntity();
        Vec3 eye = player.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(player.getViewVector(1.0f).scale(5.0)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));
        if (hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos target = hit.getBlockPos().relative(hit.getDirection());
        if (!allowed(level, player, hit.getBlockPos(), Perm.BUILD) || !allowed(level, player, target, Perm.BUILD)) {
            event.setCanceled(true);
        }
    }

    private static Perm permForEntity(Entity target) {
        if (target instanceof AbstractVillager) return Perm.TRADING;
        return target instanceof LivingEntity ? Perm.ANIMALS : Perm.BUILD; // armor stands, frames, minecarts
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.getTarget() instanceof Enemy) return;
        if (!allowed(level, event.getEntity(), event.getTarget().blockPosition(), permForEntity(event.getTarget()))) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.getTarget() instanceof Enemy) return;
        if (!allowed(level, event.getEntity(), event.getTarget().blockPosition(), permForEntity(event.getTarget()))) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttackEntity(AttackEntityEvent event) {
        Entity target = event.getTarget();
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (target instanceof net.minecraft.world.entity.monster.Enemy || target instanceof Player) return;
        Perm perm = target instanceof LivingEntity ? Perm.ANIMALS : Perm.BUILD;
        if (!allowed(level, event.getEntity(), target.blockPosition(), perm)) event.setCanceled(true);
    }

    /** No PvP in trade centers; clanmates and allies only hurt each other with friendly fire on. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim) || !(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        if (victim == attacker) return;
        ClanData data = ClanData.get(victim.getServer());
        String dim = victim.level().dimension().location().toString();
        if (data.inTradeCenter(dim, victim.chunkPosition().x, victim.chunkPosition().z)
                || data.inTradeCenter(dim, attacker.chunkPosition().x, attacker.chunkPosition().z)) {
            event.setCanceled(true);
            return;
        }
        ClanData.Clan a = data.clanOf(attacker.getUUID());
        ClanData.Clan v = data.clanOf(victim.getUUID());
        if (a == null || v == null) return;
        if ((a == v || data.mutualAllies(a, v)) && !(a.friendlyFire && v.friendlyFire)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        ClanData data = ClanData.get(level.getServer());
        String dim = level.dimension().location().toString();
        event.getAffectedBlocks().removeIf(p -> !data.explosionAllowed(level.getServer(), dim, p.getX() >> 4, p.getZ() >> 4));
        event.getAffectedEntities().removeIf(e -> !(e instanceof Enemy) && !(e instanceof Player)
                && !data.explosionAllowed(level.getServer(), dim, e.chunkPosition().x, e.chunkPosition().z));
    }
}
