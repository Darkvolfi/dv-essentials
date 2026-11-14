package com.darkvolfi.dvglass.client;

import com.darkvolfi.dvglass.DVGlass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.LinkedHashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = DVGlass.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GlassClient {
    private GlassClient() {}

    /** Every vanilla glass block and pane, with the name of the glass texture it is drawn with. */
    private static Map<Block, String> glassBlocks() {
        Map<Block, String> blocks = new LinkedHashMap<>();
        blocks.put(Blocks.GLASS, "glass");
        blocks.put(Blocks.GLASS_PANE, "glass");
        blocks.put(Blocks.TINTED_GLASS, "tinted_glass");
        for (DyeColor color : DyeColor.values()) {
            String name = color.getName() + "_stained_glass";
            blocks.put(ForgeRegistries.BLOCKS.getValue(new ResourceLocation(name)), name);
            blocks.put(ForgeRegistries.BLOCKS.getValue(new ResourceLocation(name + "_pane")), name);
        }
        return blocks;
    }

    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        Map<ResourceLocation, BakedModel> models = event.getModels();
        glassBlocks().forEach((block, texture) -> {
            boolean pane = block instanceof IronBarsBlock;
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                ResourceLocation location = BlockModelShaper.stateToModelLocation(state);
                BakedModel model = models.get(location);
                if (model != null && !(model instanceof ConnectedGlassModel)) {
                    models.put(location, new ConnectedGlassModel(model, block, pane, texture));
                }
            }
        });
    }

    /** Rebuilds every chunk mesh, so a changed setting shows at once. */
    public static void redrawWorld() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            mc.levelRenderer.allChanged();
        }
    }
}
