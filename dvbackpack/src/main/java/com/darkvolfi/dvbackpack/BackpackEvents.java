package com.darkvolfi.dvbackpack;

import com.darkvolfi.dvbackpack.upgrade.FilterUpgrade;
import com.darkvolfi.dvbackpack.upgrade.UpgradeItem;
import com.darkvolfi.dvbackpack.upgrade.UpgradeType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerSetSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the automatic cards do, for every backpack a player carries (worn or anywhere in the inventory):
 * pickup (into the backpack, and the magnet), void, feeding and refill.
 */
@Mod.EventBusSubscriber(modid = DVBackpack.MOD_ID)
public final class BackpackEvents {
    private static final double MAGNET_RANGE = 6;
    /** The held stack last tick, per player: when it runs out the refill card brings more. */
    private static final Map<UUID, Held> HELD = new HashMap<>();

    private record Held(int slot, ItemStack stack) {}

    private BackpackEvents() {}

    /** The contents of every backpack the player carries, each once. */
    private static List<BackpackStorage.Contents> carried(ServerPlayer player) {
        List<ItemStack> backpacks = new ArrayList<>();
        ItemStack worn = WornBackpack.find(player);
        if (!worn.isEmpty()) backpacks.add(worn);
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof BackpackItem && backpacks.stream().noneMatch(b -> BackpackStorage.same(b, stack))) {
                backpacks.add(stack);
            }
        }
        BackpackStorage storage = BackpackStorage.get(player.server);
        List<BackpackStorage.Contents> contents = new ArrayList<>(backpacks.size());
        for (ItemStack backpack : backpacks) contents.add(storage.contents(backpack));
        return contents;
    }

    /** The cards of this type in a backpack. */
    private static List<ItemStack> cards(BackpackStorage.Contents contents, UpgradeType type) {
        List<ItemStack> cards = new ArrayList<>();
        ItemStackHandler upgrades = contents.upgrades();
        for (int i = 0; i < upgrades.getSlots(); i++) {
            ItemStack card = upgrades.getStackInSlot(i);
            if (UpgradeItem.typeOf(card) == type) cards.add(card);
        }
        return cards;
    }

    private static boolean anyMatches(List<ItemStack> cards, ItemStack stack) {
        for (ItemStack card : cards) {
            if (FilterUpgrade.matches(card, stack)) return true;
        }
        return false;
    }

    /** Void first (it destroys), then pickup (into the backpack); what doesn't fit is picked up as usual. */
    @SubscribeEvent
    public static void onPickup(EntityItemPickupEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemEntity entity = event.getItem();
        ItemStack stack = entity.getItem();
        if (stack.isEmpty() || stack.getItem() instanceof BackpackItem) return;
        List<BackpackStorage.Contents> carried = carried(player);
        for (BackpackStorage.Contents contents : carried) {
            if (anyMatches(cards(contents, UpgradeType.VOID), stack)) {
                entity.discard();
                event.setCanceled(true);
                return;
            }
        }
        int before = stack.getCount();
        ItemStack rest = stack.copy();
        for (BackpackStorage.Contents contents : carried) {
            if (rest.isEmpty()) break;
            if (anyMatches(cards(contents, UpgradeType.PICKUP), rest)) rest = ItemHandlerHelper.insertItemStacked(contents.items(), rest, false);
        }
        if (rest.getCount() == before) return;
        player.take(entity, before - rest.getCount());
        if (rest.isEmpty()) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS,
                    0.2f, (player.getRandom().nextFloat() - player.getRandom().nextFloat()) * 1.4f + 2f);
            entity.discard();
            event.setCanceled(true);
        } else {
            entity.setItem(rest); // the rest goes into the inventory the usual way
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.isSpectator()) return;
        boolean everySecond = player.tickCount % 20 == 0;
        boolean magnetTick = player.tickCount % 4 == 0;
        Held held = HELD.get(player.getUUID());
        ItemStack now = player.getMainHandItem();
        boolean ranOut = held != null && held.slot == player.getInventory().selected && now.isEmpty();
        HELD.put(player.getUUID(), new Held(player.getInventory().selected, now.copy()));
        if (!everySecond && !magnetTick && !ranOut) return;

        List<BackpackStorage.Contents> carried = carried(player);
        if (carried.isEmpty()) return;
        if (magnetTick) magnet(player, carried);
        if (everySecond) feed(player, carried);
        if (ranOut) refill(player, held.stack, carried);
    }

    /** Items around the player that a pickup card with its magnet on would take float to the player. */
    private static void magnet(ServerPlayer player, List<BackpackStorage.Contents> carried) {
        List<ItemStack> magnets = new ArrayList<>();
        for (BackpackStorage.Contents contents : carried) {
            for (ItemStack card : cards(contents, UpgradeType.PICKUP)) {
                if (FilterUpgrade.magnet(card)) magnets.add(card);
            }
        }
        if (magnets.isEmpty()) return;
        Vec3 target = player.position().add(0, 0.5, 0);
        for (ItemEntity entity : player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(MAGNET_RANGE))) {
            if (entity.hasPickUpDelay() || !anyMatches(magnets, entity.getItem())) continue;
            Vec3 pull = target.subtract(entity.position());
            if (pull.lengthSqr() < 1) continue;
            entity.setDeltaMovement(pull.normalize().scale(0.45));
        }
    }

    /** Eats the first allowed food when the player is hungry enough for all of it to count. */
    private static void feed(ServerPlayer player, List<BackpackStorage.Contents> carried) {
        if (!player.getFoodData().needsFood()) return;
        int missing = 20 - player.getFoodData().getFoodLevel();
        for (BackpackStorage.Contents contents : carried) {
            List<ItemStack> cards = cards(contents, UpgradeType.FEEDING);
            if (cards.isEmpty()) continue;
            ItemStackHandler items = contents.items();
            for (int i = 0; i < items.getSlots(); i++) {
                ItemStack stack = items.getStackInSlot(i);
                FoodProperties food = stack.getFoodProperties(player);
                if (food == null || food.getNutrition() > missing || harmful(food) || !anyMatches(cards, stack)) continue;
                ItemStack one = items.extractItem(i, 1, false);
                ItemStack left = one.finishUsingItem(player.level(), player); // eats: food, effects, sound
                if (!left.isEmpty() && !ItemStack.isSameItem(left, one)) {
                    // A bowl or bottle: back into the backpack, or the inventory.
                    ItemStack rest = ItemHandlerHelper.insertItemStacked(items, left, false);
                    if (!rest.isEmpty() && !player.getInventory().add(rest)) player.drop(rest, false);
                }
                return;
            }
        }
    }

    private static boolean harmful(FoodProperties food) {
        for (var effect : food.getEffects()) {
            MobEffectInstance instance = effect.getFirst();
            if (instance != null && !instance.getEffect().isBeneficial()) return true;
        }
        return false;
    }

    /** The held stack ran out: bring a stack of the same item from a backpack with a refill card. */
    private static void refill(ServerPlayer player, ItemStack used, List<BackpackStorage.Contents> carried) {
        if (used.isEmpty() || used.isDamageableItem()) return; // a broken tool is not refilled with another
        for (BackpackStorage.Contents contents : carried) {
            if (!anyMatches(cards(contents, UpgradeType.REFILL), used)) continue;
            ItemStackHandler items = contents.items();
            for (int i = 0; i < items.getSlots(); i++) {
                if (!ItemStack.isSameItemSameTags(items.getStackInSlot(i), used)) continue;
                ItemStack taken = items.extractItem(i, used.getMaxStackSize(), false);
                player.getInventory().setItem(player.getInventory().selected, taken);
                return;
            }
        }
    }

    /** A sleeping bag is only somewhere to sleep, unless the world says it should set your spawn too. */
    @SubscribeEvent
    public static void onSetSpawn(PlayerSetSpawnEvent event) {
        BlockPos pos = event.getNewSpawn();
        if (pos == null || BackpackConfig.SLEEPING_BAG_SETS_SPAWN.get()) return;
        if (event.getEntity().level().getBlockState(pos).is(DVBackpack.SLEEPING_BAG.get())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        HELD.remove(event.getEntity().getUUID());
    }
}
