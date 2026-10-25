package com.darkvolfi.dvslots.slot;

import com.darkvolfi.dvslots.DVSlots;
import com.darkvolfi.dvslots.network.SlotsNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Attaches, saves, copies, drops and syncs the DV slots. */
@Mod.EventBusSubscriber(modid = DVSlots.MOD_ID)
public final class SlotEvents {
    private static final ResourceLocation KEY = new ResourceLocation(DVSlots.MOD_ID, "slots");
    private static final int FLIGHT_SYNC_TICKS = 20;

    private SlotEvents() {}

    @SubscribeEvent
    public static void onAttach(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) event.addCapability(KEY, new SlotData());
    }

    /** Respawn and returning from the End create a new player object: carry the slots over. */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        original.reviveCaps();
        SlotData from = SlotData.get(original);
        SlotData to = SlotData.get(event.getEntity());
        if (from != null && to != null) to.deserializeNBT(from.serializeNBT());
        original.invalidateCaps();
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) return;
        if (player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) return;
        SlotData data = SlotData.get(player);
        if (data == null) return;
        for (SlotType type : SlotType.values()) {
            ItemStack stack = data.get(type);
            if (stack.isEmpty() || EnchantmentHelper.hasVanishingCurse(stack)) {
                data.set(type, ItemStack.EMPTY);
                continue;
            }
            ItemEntity drop = new ItemEntity(player.level(), player.getX(), player.getEyeY() - 0.3, player.getZ(), stack.copy());
            drop.setDefaultPickUpDelay();
            event.getDrops().add(drop);
            data.set(type, ItemStack.EMPTY);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        SlotData data = SlotData.get(player);
        if (data == null) return;
        // Flight wears the elytra down in place, which the handler doesn't notice.
        if (player.isFallFlying() && player.tickCount % FLIGHT_SYNC_TICKS == 0) data.markDirty();
        if (data.consumeDirty()) SlotsNetwork.syncToTrackingAndSelf(player);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SlotsNetwork.syncToTrackingAndSelf(player);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SlotsNetwork.syncToTrackingAndSelf(player);
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SlotsNetwork.syncToTrackingAndSelf(player);
    }

    /** Others need to know what you wear on your back to draw it. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof Player target && event.getEntity() instanceof ServerPlayer viewer) {
            SlotsNetwork.syncTo(viewer, target);
        }
    }

    @Mod.EventBusSubscriber(modid = DVSlots.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent
        public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
            event.register(SlotData.class);
        }
    }
}
