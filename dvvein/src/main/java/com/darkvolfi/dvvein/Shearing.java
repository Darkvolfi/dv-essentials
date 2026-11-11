package com.darkvolfi.dvvein;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemHandlerHelper;

/** With the vein key held, shearing one sheep shears every sheep around; the wool goes into the inventory. */
@Mod.EventBusSubscriber(modid = DVVein.MOD_ID)
public final class Shearing {
    private static final double RADIUS = 32.0; // 64 blocks across
    private static final double HEIGHT = 3.0;  // above and below the player

    /** Set while asking claim mods about one sheep, so that question doesn't start another round of shearing. */
    private static boolean asking;

    private Shearing() {}

    // Server only: the client must still send the click, so it is not cancelled there.
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (asking || !(event.getEntity() instanceof ServerPlayer player) || !(event.getTarget() instanceof Sheep)) return;
        ItemStack shears = event.getItemStack();
        if (!VeinMiner.isKeyHeld(player) || !shears.canPerformAction(ToolActions.SHEARS_HARVEST)) return;

        ServerLevel level = (ServerLevel) player.level();
        int fortune = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_FORTUNE, shears);
        int sheared = 0;
        for (Sheep sheep : level.getEntitiesOfClass(Sheep.class, player.getBoundingBox().inflate(RADIUS, HEIGHT, RADIUS))) {
            if (!VeinMiner.canContinue(player, shears)) break;
            BlockPos pos = sheep.blockPosition();
            if (!sheep.isShearable(shears, level, pos)) continue;
            // Only the clicked sheep went through the claim check; the others may stand on someone else's land.
            if (sheep != event.getTarget() && !mayInteract(player, sheep, event.getHand())) continue;
            for (ItemStack wool : sheep.onSheared(player, shears, level, pos, fortune)) {
                ItemHandlerHelper.giveItemToPlayer(player, wool);
            }
            if (!player.isCreative()) {
                shears.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(event.getHand()));
                player.causeFoodExhaustion(VeinConfig.EXHAUSTION_PER_BLOCK.get().floatValue());
            }
            sheared++;
        }
        if (sheared > 0) {
            event.setCanceled(true); // the clicked sheep is already done; vanilla would shear it again
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /** Posts the same interact event a real click on this sheep would, so claim mods can refuse it. */
    private static boolean mayInteract(ServerPlayer player, Sheep sheep, InteractionHand hand) {
        asking = true;
        try {
            return ForgeHooks.onInteractEntity(player, sheep, hand) == null;
        } finally {
            asking = false;
        }
    }
}
