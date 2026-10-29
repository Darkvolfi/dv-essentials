package com.darkvolfi.dvsouls;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * An item holding a soul: a filled soul jar, which lets it out where you click, or a dead pet's collar shard, which only the
 * Soul Altar can bring back.
 */
public class SoulItem extends Item {
    private final boolean releases;

    public SoulItem(boolean releases) {
        super(new Properties().stacksTo(1));
        this.releases = releases;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!releases) return InteractionResult.PASS;
        CompoundTag soul = Soul.get(context.getItemInHand());
        if (soul == null) return InteractionResult.PASS;
        Level level = context.getLevel();
        if (level instanceof ServerLevel server) {
            Vec3 pos = Vec3.atBottomCenterOf(context.getClickedPos().relative(context.getClickedFace()));
            Player player = context.getPlayer();
            float yaw = player == null ? 0 : player.getYRot() + 180;
            if (Soul.spawn(soul, server, pos, yaw) == null) return InteractionResult.FAIL;
            level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1, 1);
            if (player == null || !player.getAbilities().instabuild) {
                ItemStack empty = new ItemStack(DVSouls.SOUL_JAR.get());
                if (player == null) context.getItemInHand().shrink(1);
                else player.setItemInHand(context.getHand(), empty);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag soul = Soul.get(stack);
        if (soul == null) {
            tooltip.add(Component.translatable("dvsouls.no_soul").withStyle(ChatFormatting.RED));
            return;
        }
        tooltip.add(Soul.name(soul).copy().withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("dvsouls.power", String.format(Locale.ROOT, "%.1f", Soul.hearts(soul)))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(releases ? "dvsouls.jar.hint" : "dvsouls.shard.hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Burnt as fuel (in the Soul Furnace or Fuser), a full jar leaves its empty jar behind. */
    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack) {
        return releases;
    }

    @Override
    public ItemStack getCraftingRemainingItem(ItemStack stack) {
        return releases ? new ItemStack(DVSouls.SOUL_JAR.get()) : ItemStack.EMPTY;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return !releases;
    }
}
