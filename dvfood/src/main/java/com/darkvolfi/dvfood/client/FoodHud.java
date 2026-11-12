package com.darkvolfi.dvfood.client;

import com.darkvolfi.dvfood.DVFood;
import com.darkvolfi.dvfood.FoodConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
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
            // Harmful food: the vanilla Hunger-effect icons (u=88/97), tinted green when it also poisons.
            boolean hunger = hasFoodEffect(held, MobEffects.HUNGER);
            boolean poison = hasFoodEffect(held, MobEffects.POISON);
            int previewU = hunger ? 88 : iconU + 36;
            if (poison) g.setColor(0.55f, 0.8f, 0.25f, pulse());
            else g.setColor(1f, 1f, 1f, pulse());
            for (int i = 0; i < 10; i++) {
                int u = iconFor(i, foodLevel, newFood, previewU, previewU + 9);
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
        boolean foodOn = FoodConfig.SHOW_HEALTH_PREVIEW.get();
        boolean effectsOn = FoodConfig.SHOW_EFFECT_PREVIEW.get();
        if (!foodOn && !effectsOn) return;
        FoodProperties held = heldFood(player);

        float maxHealth = (float) player.getAttributeValue(Attributes.MAX_HEALTH);
        float foodGain = foodOn && held != null
                ? HealthEstimate.healthFromEating(player.getFoodData(), held, player.getHealth(), maxHealth) : 0;
        EffectPreview fx = effectsOn ? EffectPreview.of(player, held, player.getHealth(), maxHealth) : null;
        int health = Mth.ceil(player.getHealth());
        int newHealth = Math.min(Mth.ceil(player.getHealth() + foodGain) + (fx == null ? 0 : fx.gain), (int) maxHealth);
        int loss = fx == null ? 0 : fx.loss;
        int currentAbsorb = Mth.ceil(player.getAbsorptionAmount());
        int absorb = fx == null ? 0 : addedAbsorption(player, fx.absorption, currentAbsorb);
        if (newHealth <= health && loss == 0 && absorb == 0) return;

        // Same row maths as Gui.renderHearts.
        int maxHp = Math.max((int) maxHealth, health);
        int rows = Mth.ceil((maxHp + currentAbsorb + absorb) / 2.0f / 10.0f);
        int rowHeight = Math.max(10 - (rows - 2), 3);
        int v = player.level().getLevelData().isHardcore() ? 45 : 0;
        int heartIndex = player.hasEffect(MobEffects.POISON) ? 4 : player.hasEffect(MobEffects.WITHER) ? 6 : 2;
        int fullU = 16 + heartIndex * 2 * 9;
        float pulse = pulse();

        if (newHealth > health) {
            g.setColor(1f, 1f, 1f, pulse);
            for (int i = 0; i < Mth.ceil(maxHp / 2.0f); i++) {
                int u = iconFor(i, health, newHealth, fullU, fullU + 9);
                if (u >= 0) g.blit(ICONS, left + (i % 10) * 8, heartsTop - (i / 10) * rowHeight, u, v, 9, 9);
            }
        }
        if (loss > 0) {
            // Dark red overlay on the part of the hearts that would go away.
            int after = Math.max(health - loss, 0);
            g.setColor(0.25f, 0.0f, 0.0f, pulse);
            for (int i = 0; i < Mth.ceil(Math.min(health, maxHp) / 2.0f); i++) {
                int low = i * 2 + 1, high = i * 2 + 2;
                boolean lowLost = low > after && low <= health;
                boolean highLost = high > after && high <= health;
                int x = left + (i % 10) * 8;
                int y = heartsTop - (i / 10) * rowHeight;
                if (lowLost && highLost) g.blit(ICONS, x, y, fullU, v, 9, 9);
                else if (lowLost) g.blit(ICONS, x, y, fullU + 9, v, 9, 9);
                else if (highLost) g.blit(ICONS, x + 5, y, fullU + 5, v, 4, 9);
            }
        }
        if (absorb > 0) {
            // Yellow hearts continue after the current absorption (u=160 full, 169 half), as vanilla draws them.
            int from = maxHp + currentAbsorb;
            g.setColor(1f, 1f, 1f, pulse);
            for (int i = from / 2; i < Mth.ceil((from + absorb) / 2.0f); i++) {
                int u = iconFor(i, from, from + absorb, 160, 169);
                if (u >= 0) g.blit(ICONS, left + (i % 10) * 8, heartsTop - (i / 10) * rowHeight, u, v, 9, 9);
            }
        }
        g.setColor(1f, 1f, 1f, 1f);
    }

    /**
     * How much absorption the held item would really add. Absorption doesn't stack: with the effect already on, the
     * game takes off what the old effect gave and puts on the new amount (only if the new one is at least as strong).
     * So a second golden apple tops the yellow hearts back up instead of doubling them.
     */
    private static int addedAbsorption(Player player, int bonus, int current) {
        if (bonus <= 0) return 0;
        MobEffectInstance active = player.getEffect(MobEffects.ABSORPTION);
        if (active == null) return bonus;
        int old = 4 * (active.getAmplifier() + 1);
        int after = bonus >= old ? Math.max(0, current - old) + bonus : current;
        return Math.max(0, after - current);
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

    private static boolean hasFoodEffect(FoodProperties props, MobEffect effect) {
        for (Pair<MobEffectInstance, Float> pair : props.getEffects()) {
            if (pair.getFirst().getEffect() == effect && pair.getSecond() > 0) return true;
        }
        return false;
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
