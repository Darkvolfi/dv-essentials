package com.darkvolfi.dvbackpack;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A backpack. Right-click opens it; sneak + right-click on the ground sets it down as a block.
 * It can be worn in the chest slot, DV Slots' backpack slot or a Curios back slot (see {@link WornBackpack}).
 */
public class BackpackItem extends Item {
    private final BackpackTier tier;

    public BackpackItem(BackpackTier tier) {
        super(tier == BackpackTier.NETHERITE ? new Properties().stacksTo(1).fireResistant() : new Properties().stacksTo(1));
        this.tier = tier;
    }

    public BackpackTier tier() {
        return tier;
    }

    /** The default sleeping bag colour: a green you would take camping. */
    public static final int DEFAULT_BAG_COLOR = 0x5B7A3A;

    /**
     * The sleeping bag's colour, kept on the item (unlike the contents) because the client draws it: the rolled
     * bag on the backpack, and the bag laid out.
     */
    public static int bagColor(ItemStack backpack) {
        CompoundTag tag = backpack.getTag();
        return tag != null && tag.contains("BagColor") ? tag.getInt("BagColor") : DEFAULT_BAG_COLOR;
    }

    public static void setBagColor(ItemStack backpack, int rgb) {
        backpack.getOrCreateTag().putInt("BagColor", rgb & 0xFFFFFF);
    }

    /** What the tank cards hold, copied onto the item so the client can draw the tanks on the backpack's sides. */
    public static List<FluidStack> tanks(ItemStack backpack) {
        List<FluidStack> tanks = new ArrayList<>();
        CompoundTag tag = backpack.getTag();
        if (tag == null) return tanks;
        for (Tag t : tag.getList("Tanks", Tag.TAG_COMPOUND)) tanks.add(FluidStack.loadFluidStackFromNBT((CompoundTag) t));
        return tanks;
    }

    public static void setTanks(ItemStack backpack, List<FluidStack> tanks) {
        ListTag list = new ListTag();
        for (FluidStack fluid : tanks) list.add(fluid.writeToNBT(new CompoundTag()));
        if (list.isEmpty()) {
            if (backpack.getTag() != null) backpack.getTag().remove("Tanks");
        } else {
            backpack.getOrCreateTag().put("Tanks", list);
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : 40;
            BackpackMenu.open(sp, stack, BackpackMenu.Source.hand(slot));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isSecondaryUseActive()) return InteractionResult.PASS; // falls through to use()
        BlockPlaceContext place = new BlockPlaceContext(context);
        if (!place.canPlace()) return InteractionResult.FAIL;
        Level level = context.getLevel();
        BlockPos pos = place.getClickedPos();
        BlockState state = DVBackpack.BACKPACK_BLOCK.get().getStateForPlacement(place);
        if (state == null || !level.setBlock(pos, state, 3)) return InteractionResult.FAIL;
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof BackpackBlockEntity entity) {
            ItemStack stack = context.getItemInHand();
            if (level.getServer() != null) BackpackStorage.get(level.getServer()).contents(stack); // give it an id first
            entity.setBackpack(stack.split(1));
        }
        level.playSound(player, pos, SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.BLOCKS, 1f, 0.8f);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public @Nullable EquipmentSlot getEquipmentSlot(ItemStack stack) {
        return EquipmentSlot.CHEST;
    }

    @Override
    public boolean canFitInsideContainerItems() {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("dvbackpack.tooltip.slots", tier.slots()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("dvbackpack.tooltip.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
