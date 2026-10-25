package com.darkvolfi.dvfood.client;

import com.darkvolfi.dvfood.DVFood;
import com.darkvolfi.dvfood.FoodConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

/**
 * Draws on top of the vanilla hunger and health bars, following the layout of
 * ForgeGui.renderFood / Gui.renderHearts: 9x9 icons 8px apart, two points per icon.
 */
@Mod.EventBusSubscriber(modid = DVFood.MOD_ID, value = Dist.CLIENT)
public final class FoodHud {
    static final ResourceLocation ICONS = new ResourceLocation("textures/gui/icons.png");
    private static final float MAX_EXHAUSTION = 4.0f;
    private static final int EXHAUSTION_COLOR = 0x50FFFFFF;
    private static final int OUTLINE_RGB = 0xFFC830;

    /** Top of the heart rows, captured before vanilla moves {@code leftHeight}. */
    private static int heartsTop;

    private FoodHud() {}

    @SubscribeEvent
    public static void onOverlayPre(RenderGuiOverlayEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui instanceof ForgeGui gui) || mc.player == null) return;
        int height = event.getWindow().getGuiScaledHeight();

        if (event.getOverlay() == VanillaGuiOverlay.PLAYER_HEALTH.type()) {
            heartsTop = height - gui.leftHeight;
            return;
        }
        if (event.getOverlay() != VanillaGuiOverlay.FOOD_LEVEL.type()
                || !FoodConfig.SHOW_EXHAUSTION.get() || !isFoodBarVisible(mc, gui)) return;

        // Drawn before the bar, so the icons sit on top of this underlay.
        int left = event.getWindow().getGuiScaledWidth() / 2 + 91;
        int top = height - gui.rightHeight;
        float ratio = Math.min(mc.player.getFoodData().getExhaustionLevel(), MAX_EXHAUSTION) / MAX_EXHAUSTION;
        int width = Math.round(81 * ratio);
        if (width > 0) event.getGuiGraphics().fill(left - width, top, left, top + 9, EXHAUSTION_COLOR);
    }

    @SubscribeEvent
    public static void onOverlayPost(RenderGuiOverlayEvent.Post event) {
        boolean food = event.getOverlay() == VanillaGuiOverlay.FOOD_LEVEL.type();
        boolean health = event.getOverlay() == VanillaGuiOverlay.PLAYER_HEALTH.type();
        if (!food && !health) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui instanceof ForgeGui gui) || mc.player == null) return;

        RenderSystem.enableBlend();
        if (food && isFoodBarVisible(mc, gui)) {
            renderFoodBar(event.getGuiGraphics(), mc.player, event.getWindow().getGuiScaledWidth() / 2 + 91,
                    event.getWindow().getGuiScaledHeight() - gui.rightHeight + 10); // renderFood already added 10
        }
        if (health && !mc.options.hideGui && gui.shouldDrawSurvivalElements()) {
            renderHealthPreview(event.getGuiGraphics(), mc.player, event.getWindow().getGuiScaledWidth() / 2 - 91);
        }
        event.getGuiGraphics().setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    private static void renderFoodBar(GuiGraphics g, LocalPlayer player, int left, int top) {
        FoodData data = player.getFoodData();
        int iconU = player.hasEffect(MobEffects.HUNGER) ? 52 : 16;
        int foodLevel = data.getFoodLevel();
        int saturation = (int) data.getSaturationLevel();
        FoodProperties held = FoodConfig.SHOW_PREVIEW.get() ? heldFood(player) : null;

        if (held != null) {
            int newFood = Math.min(20, foodLevel + held.getNutrition());
            g.setColor(1f, 1f, 1f, pulse());
            for (int i = 0; i < 10; i++) {
                int u = iconFor(i, foodLevel, newFood, iconU + 36, iconU + 45);
                if (u >= 0) g.blit(ICONS, left - i * 8 - 9, top, u, 27, 9, 9);
            }
            g.setColor(1f, 1f, 1f, 1f);
        }

        if (FoodConfig.SHOW_SATURATION.get()) {
            // Recolour the dark outline of the meat icons that are covered by saturation.
            for (int i = 0; i < 10; i++) {
                int u = iconFor(i, 0, saturation, iconU + 36, iconU + 45);
                if (u >= 0) drawOutline(g, left - i * 8 - 9, top, u, 1f);
            }
            if (held != null) {
                int newSaturation = (int) Math.min(Math.min(20, foodLevel + held.getNutrition()),
                        data.getSaturationLevel() + saturationGain(held));
                float alpha = pulse();
                for (int i = 0; i < 10; i++) {
                    int u = iconFor(i, saturation, newSaturation, iconU + 36, iconU + 45);
                    if (u >= 0) drawOutline(g, left - i * 8 - 9, top, u, alpha);
                }
            }
        }
    }

    private static void renderHealthPreview(GuiGraphics g, LocalPlayer player, int left) {
        if (!FoodConfig.SHOW_HEALTH_PREVIEW.get()) return;
        FoodProperties held = heldFood(player);
        if (held == null) return;

        float maxHealth = (float) player.getAttributeValue(Attributes.MAX_HEALTH);
        float gain = HealthEstimate.healthFromEating(player.getFoodData(), held, player.getHealth(), maxHealth);
        int health = Mth.ceil(player.getHealth());
        int newHealth = Math.min(Mth.ceil(player.getHealth() + gain), (int) maxHealth);
        if (newHealth <= health) return;

        // Same row maths as Gui.renderHearts.
        int maxHearts = Math.max((int) maxHealth, health);
        int rows = Mth.ceil((maxHearts + Mth.ceil(player.getAbsorptionAmount())) / 2.0f / 10.0f);
        int rowHeight = Math.max(10 - (rows - 2), 3);
        int v = player.level().getLevelData().isHardcore() ? 45 : 0;
        int heartIndex = player.hasEffect(MobEffects.POISON) ? 4 : player.hasEffect(MobEffects.WITHER) ? 6 : 2;
        int fullU = 16 + heartIndex * 2 * 9;

        g.setColor(1f, 1f, 1f, pulse());
        for (int i = 0; i < Mth.ceil(maxHearts / 2.0f); i++) {
            int u = iconFor(i, health, newHealth, fullU, fullU + 9);
            if (u < 0) continue;
            int x = left + (i % 10) * 8;
            int y = heartsTop - (i / 10) * rowHeight;
            g.blit(ICONS, x, y, u, v, 9, 9);
        }
    }

    /**
     * Icon u for slot {@code i} when the bar shows values from {@code from} up to {@code to}:
     * full, half, or -1 when the slot is outside that range.
     */
    private static int iconFor(int i, int from, int to, int fullU, int halfU) {
        int idx = i * 2 + 1;
        if (idx > to || idx < from) return -1;
        return idx < to ? fullU : halfU;
    }

    static void drawOutline(GuiGraphics g, int x, int y, int iconU, float alpha) {
        int color = ((int) (alpha * 255) << 24) | OUTLINE_RGB;
        int backgroundU = iconU >= 88 ? 133 : 16; // hunger-effect icons have their own background
        for (int[] p : IconOutline.of(backgroundU, iconU, 27)) {
            g.fill(x + p[0], y + p[1], x + p[0] + 1, y + p[1] + 1, color);
        }
    }

    // Mirrors the visibility check of the vanilla FOOD_LEVEL overlay.
    private static boolean isFoodBarVisible(Minecraft mc, ForgeGui gui) {
        if (mc.options.hideGui || !gui.shouldDrawSurvivalElements()) return false;
        Entity vehicle = mc.player.getVehicle();
        return vehicle == null || !vehicle.showVehicleHealth();
    }

    private static float pulse() {
        return 0.35f + 0.55f * (Mth.sin(Util.getMillis() / 200f) * 0.5f + 0.5f);
    }

    static FoodProperties heldFood(LocalPlayer player) {
        for (ItemStack stack : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
            FoodProperties props = stack.getFoodProperties(player);
            if (props != null && player.canEat(props.canAlwaysEat())) return props;
        }
        return null;
    }

    static float saturationGain(FoodProperties props) {
        return props.getNutrition() * props.getSaturationModifier() * 2.0f;
    }

    @SubscribeEvent
    public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        LocalPlayer player = Minecraft.getInstance().player;
        // Forge fires this every frame and draws whatever is added, so check F3 ourselves.
        if (player == null || !Minecraft.getInstance().options.renderDebug) return;
        FoodData data = player.getFoodData();
        event.getLeft().add(String.format(Locale.ROOT, "[DV Food] hunger: %d, saturation: %.2f, exhaustion: %.2f",
                data.getFoodLevel(), data.getSaturationLevel(), data.getExhaustionLevel()));
    }
}
