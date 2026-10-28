package com.darkvolfi.dvvein;

import com.darkvolfi.dvvein.client.VeinClient;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.Collections;
import java.util.List;

/**
 * Right-click a ripe crop: drop its harvest and put it back as a seedling. With the vein key held, the
 * whole connected field is harvested and everything goes straight into the inventory.
 * Works for vanilla and modded {@link CropBlock}s, nether wart, cocoa, and anything
 * tagged dvvein:harvestable that grows through an "age" property.
 */
@Mod.EventBusSubscriber(modid = DVVein.MOD_ID)
public final class Harvest {
    public static final TagKey<Block> HARVESTABLE = BlockTags.create(new ResourceLocation(DVVein.MOD_ID, "harvestable"));
    public static final TagKey<Block> BLACKLIST = BlockTags.create(new ResourceLocation(DVVein.MOD_ID, "blacklist"));
    private static final float EXHAUSTION = 0.005f; // same as breaking a block
    private static final int BREAK_EFFECTS = 2001;  // level event: break particles and sound

    private Harvest() {}

    // Runs on both sides: the client must also cancel, or it would try to use the held item.
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (event.getHand() != InteractionHand.MAIN_HAND || player.isShiftKeyDown() || player.isSpectator()) return;
        Level level = event.getLevel();
        if (level.isClientSide && !VeinClient.serverHasMod()) return;

        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!isRipe(state)) return;
        BlockState seedling = seedling(state);
        if (seedling == null || !level.mayInteract(player, pos) || !player.mayBuild()) return;

        if (level instanceof ServerLevel server) {
            boolean field = VeinMiner.isKeyHeld(player);
            harvest(server, player, pos, state, seedling, field);
            player.causeFoodExhaustion(EXHAUSTION);
            if (field) harvestField(server, player, pos, state);
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
    }

    /** Every ripe crop of the same kind connected to the clicked one. */
    private static void harvestField(ServerLevel level, Player player, BlockPos origin, BlockState originState) {
        List<BlockPos> field = VeinFinder.find(level, origin, originState, VeinConfig.MAX_BLOCKS.get(), false);
        for (BlockPos pos : field.subList(1, field.size())) { // the clicked crop is already done
            if (!player.isCreative() && player.getFoodData().getFoodLevel() <= 0) return;
            BlockState state = level.getBlockState(pos);
            if (!isRipe(state) || VeinMiner.isBlacklisted(player, state) || !level.mayInteract(player, pos)) continue;
            BlockState seedling = seedling(state);
            if (seedling == null) continue;
            harvest(level, player, pos, state, seedling, true);
            player.causeFoodExhaustion(EXHAUSTION + VeinConfig.EXHAUSTION_PER_BLOCK.get().floatValue());
        }
    }

    /** Drops the harvest (or puts it in the inventory) and puts a seedling in place. */
    private static void harvest(ServerLevel level, Player player, BlockPos pos, BlockState state, BlockState seedling, boolean toInventory) {
        List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), player, player.getMainHandItem());
        removeOne(drops, state.getBlock().getCloneItemStack(level, pos, state)); // the seed that gets replanted
        level.levelEvent(BREAK_EFFECTS, pos, Block.getId(state));
        level.setBlock(pos, seedling, Block.UPDATE_ALL);
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) continue;
            if (toInventory) ItemHandlerHelper.giveItemToPlayer(player, drop);
            else Block.popResource(level, pos, drop);
        }
    }

    static boolean isRipe(BlockState state) {
        if (state.is(BLACKLIST)) return false;
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) return crop.isMaxAge(state);
        if (block instanceof NetherWartBlock || block instanceof CocoaBlock || state.is(HARVESTABLE)) {
            IntegerProperty age = age(state);
            return age != null && state.getValue(age) == Collections.max(age.getPossibleValues());
        }
        return false;
    }

    /** The same block at age 0, keeping its other properties (facing, etc.). */
    static BlockState seedling(BlockState state) {
        IntegerProperty age = age(state);
        if (age != null) return state.setValue(age, Collections.min(age.getPossibleValues()));
        if (state.getBlock() instanceof CropBlock crop) return crop.getStateForAge(0); // age under another name
        return null;
    }

    private static IntegerProperty age(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty integer && property.getName().equals("age")) return integer;
        }
        return null;
    }

    private static void removeOne(List<ItemStack> drops, ItemStack seed) {
        if (seed.isEmpty()) return;
        for (ItemStack drop : drops) {
            if (ItemStack.isSameItem(drop, seed)) {
                drop.shrink(1);
                return;
            }
        }
    }
}
