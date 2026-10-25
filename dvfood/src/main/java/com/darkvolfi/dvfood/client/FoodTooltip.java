package com.darkvolfi.dvfood.client;

import com.darkvolfi.dvfood.DVFood;
import com.darkvolfi.dvfood.FoodConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.datafixers.util.Either;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Food tooltip: a row of hunger icons and, below it, a row of gold-outlined saturation icons. */
public record FoodTooltip(int nutrition, float saturation) implements TooltipComponent {
    private static final int MAX_ICONS = 10;

    @Mod.EventBusSubscriber(modid = DVFood.MOD_ID, value = Dist.CLIENT)
    public static final class Events {
        @SubscribeEvent
        public static void onGatherComponents(RenderTooltipEvent.GatherComponents event) {
            if (!FoodConfig.SHOW_TOOLTIP.get()) return;
            FoodProperties props = event.getItemStack().getFoodProperties(Minecraft.getInstance().player);
            if (props == null) return;
            event.getTooltipElements().add(Either.right(
                    new FoodTooltip(props.getNutrition(), FoodHud.saturationGain(props))));
        }
    }

    @Mod.EventBusSubscriber(modid = DVFood.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent
        public static void onRegisterFactories(RegisterClientTooltipComponentFactoriesEvent event) {
            event.register(FoodTooltip.class, Renderer::new);
        }
    }

    private record Renderer(FoodTooltip tip) implements ClientTooltipComponent {
        @Override
        public int getHeight() {
            return 22;
        }

        @Override
        public int getWidth(Font font) {
            return Math.max(icons(tip.nutrition), icons(tip.saturation)) * 9;
        }

        @Override
        public void renderImage(Font font, int x, int y, GuiGraphics g) {
            RenderSystem.enableBlend();
            drawRow(g, x, y, tip.nutrition, false);
            drawRow(g, x, y + 10, tip.saturation, true);
            g.setColor(1f, 1f, 1f, 1f);
            RenderSystem.disableBlend();
        }

        /** Two points per icon, last icon half when the value is odd. */
        private static void drawRow(GuiGraphics g, int x, int y, float points, boolean saturation) {
            int count = icons(points);
            for (int i = 0; i < count; i++) {
                boolean half = points - i * 2 < 2;
                int iconU = half ? 61 : 52;
                int ix = x + i * 9;
                if (saturation) {
                    // Hollow: faded empty icon with the gold outline of the meat on top.
                    g.setColor(1f, 1f, 1f, 0.5f);
                    g.blit(FoodHud.ICONS, ix, y, 16, 27, 9, 9);
                    g.setColor(1f, 1f, 1f, 1f);
                    FoodHud.drawOutline(g, ix, y, iconU, 1f);
                } else {
                    g.blit(FoodHud.ICONS, ix, y, 16, 27, 9, 9);
                    g.blit(FoodHud.ICONS, ix, y, iconU, 27, 9, 9);
                }
            }
        }

        private static int icons(float points) {
            return Math.min(MAX_ICONS, (int) Math.ceil(points / 2.0f - 0.25f)); // ignore slivers under half an icon
        }
    }
}
