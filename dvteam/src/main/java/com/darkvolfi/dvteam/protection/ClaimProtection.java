package com.darkvolfi.dvteam.protection;

import com.darkvolfi.dvteam.DVTeam;
import com.darkvolfi.dvteam.TeamConfig;
import com.darkvolfi.dvteam.data.TeamSavedData;
import com.darkvolfi.dvteam.network.TeamNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.DispensibleContainerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Iterator;

@Mod.EventBusSubscriber(modid = DVTeam.MOD_ID)
public final class ClaimProtection {
    private ClaimProtection() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        TeamSavedData.get(event.getServer()).applyAllForceLoads(event.getServer());
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp && sp.getServer() != null) {
            TeamSavedData.get(sp.getServer()).ensurePlayerTeam(sp);
            TeamNetwork.syncToAll(sp.getServer());
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp && sp.getServer() != null) {
            TeamNetwork.syncToAll(sp.getServer());
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Player player = event.getPlayer();
        if (player == null) return;

        BlockPos pos = event.getPos();
        if (!checkEdit(level, pos, player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof Player player)) return;

        if (!checkEdit(level, event.getPos(), player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMultiBlockPlace(BlockEvent.EntityMultiPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof Player player)) return;

        for (BlockSnapshot snap : event.getReplacedBlockSnapshots()) {
            if (!checkEdit(level, snap.getPos(), player)) {
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        String dim = level.dimension().location().toString();
        TeamSavedData data = TeamSavedData.get(level.getServer());
        if (event.getEntity() instanceof Player player) {
            if (!data.canEdit(dim, pos.getX() >> 4, pos.getZ() >> 4, player)) {
                event.setCanceled(true);
            }
        } else if (data.getClaim(dim, pos.getX() >> 4, pos.getZ() >> 4) != null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level rawLevel = event.getLevel();
        if (!(rawLevel instanceof ServerLevel level)) return;
        Player player = event.getEntity();
        BlockPos pos = event.getPos();
        String dim = level.dimension().location().toString();
        TeamSavedData data = TeamSavedData.get(level.getServer());

        if (!data.canInteract(dim, pos.getX() >> 4, pos.getZ() >> 4, player)) {
            event.setUseBlock(Event.Result.DENY);
            if (!data.canEdit(dim, pos.getX() >> 4, pos.getZ() >> 4, player)) {
                // Allow eating food or using non-block items in hand, but deny placing blocks/buckets
                ItemStack held = event.getItemStack();
                if (!held.isEdible()) {
                    event.setUseItem(Event.Result.DENY);
                    event.setCanceled(true);
                }
            }
            notifyDenied(level, pos, player);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof BucketItem) && !(stack.getItem() instanceof DispensibleContainerItem)) {
            return;
        }
        Player player = event.getEntity();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(5.0));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player));
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos target = hit.getBlockPos().relative(hit.getDirection());
            if (!checkEdit(level, hit.getBlockPos(), player) || !checkEdit(level, target, player)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Entity target = event.getTarget();
        if (target instanceof Enemy) return;
        BlockPos pos = target.blockPosition();
        String dim = level.dimension().location().toString();
        TeamSavedData data = TeamSavedData.get(level.getServer());
        if (!data.canInteract(dim, pos.getX() >> 4, pos.getZ() >> 4, event.getEntity())) {
            event.setCanceled(true);
            notifyDenied(level, pos, event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Entity target = event.getTarget();
        if (target instanceof Enemy) return;
        BlockPos pos = target.blockPosition();
        String dim = level.dimension().location().toString();
        TeamSavedData data = TeamSavedData.get(level.getServer());
        if (!data.canInteract(dim, pos.getX() >> 4, pos.getZ() >> 4, event.getEntity())) {
            event.setCanceled(true);
            notifyDenied(level, pos, event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        Player attacker = event.getEntity();
        if (!(attacker.level() instanceof ServerLevel level)) return;
        Entity target = event.getTarget();
        if (target instanceof Enemy || target instanceof Player) return;
        if (!checkEdit(level, target.blockPosition(), attacker)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        Entity sourceEntity = event.getSource().getEntity();
        if (!(sourceEntity instanceof ServerPlayer attacker)) return;
        if (victim == attacker) return;

        MinecraftServer server = victim.getServer();
        if (server == null) return;
        TeamSavedData data = TeamSavedData.get(server);
        TeamSavedData.TeamRecord victimTeam = data.getPlayerTeam(victim.getUUID());
        TeamSavedData.TeamRecord attackerTeam = data.getPlayerTeam(attacker.getUUID());
        if (victimTeam != null && attackerTeam != null) {
            boolean sameTeam = victimTeam.id.equals(attackerTeam.id);
            boolean mutuallyAllied = victimTeam.allies.contains(attackerTeam.id) && attackerTeam.allies.contains(victimTeam.id);
            if ((sameTeam || mutuallyAllied) && (!victimTeam.friendlyFire || !attackerTeam.friendlyFire)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!TeamConfig.PROTECT_EXPLOSIONS.get()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        String dim = level.dimension().location().toString();
        TeamSavedData data = TeamSavedData.get(level.getServer());

        event.getAffectedBlocks().removeIf(pos -> data.getClaim(dim, pos.getX() >> 4, pos.getZ() >> 4) != null);
        Iterator<Entity> it = event.getAffectedEntities().iterator();
        while (it.hasNext()) {
            Entity entity = it.next();
            if (!(entity instanceof Enemy)) {
                BlockPos pos = entity.blockPosition();
                if (data.getClaim(dim, pos.getX() >> 4, pos.getZ() >> 4) != null) {
                    it.remove();
                }
            }
        }
    }

    private static boolean checkEdit(ServerLevel level, BlockPos pos, Player player) {
        String dim = level.dimension().location().toString();
        TeamSavedData data = TeamSavedData.get(level.getServer());
        if (!data.canEdit(dim, pos.getX() >> 4, pos.getZ() >> 4, player)) {
            notifyDenied(level, pos, player);
            return false;
        }
        return true;
    }

    private static void notifyDenied(ServerLevel level, BlockPos pos, Player player) {
        String dim = level.dimension().location().toString();
        TeamSavedData data = TeamSavedData.get(level.getServer());
        TeamSavedData.ClaimRecord claim = data.getClaim(dim, pos.getX() >> 4, pos.getZ() >> 4);
        if (claim == null) return;
        TeamSavedData.TeamRecord team = data.getTeam(claim.teamId);
        String teamName = team != null ? team.name : "DV Team";
        player.displayClientMessage(
                Component.translatable("dvteam.msg.protected", teamName).withStyle(ChatFormatting.RED),
                true
        );
    }
}
