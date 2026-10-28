package com.darkvolfi.dvgrave;

import com.darkvolfi.dvlib.client.DVConfigScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(DVGrave.MOD_ID)
public class DVGrave {
    public static final String MOD_ID = "dvgrave";

    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MOD_ID);

    public static final RegistryObject<GraveBlock> GRAVE = BLOCKS.register("grave", GraveBlock::new);
    @SuppressWarnings("DataFlowIssue") // the data fixer type is unused for mod block entities
    public static final RegistryObject<BlockEntityType<GraveBlockEntity>> GRAVE_ENTITY = BLOCK_ENTITIES.register("grave",
            () -> BlockEntityType.Builder.of(GraveBlockEntity::new, GRAVE.get()).build(null));
    public static final RegistryObject<MenuType<GraveMenu>> GRAVE_MENU = MENUS.register("grave",
            () -> IForgeMenuType.create((id, inventory, buf) -> {
                BlockPos pos = buf.readBlockPos();
                int size = buf.readVarInt();
                int xp = buf.readVarInt();
                String owner = buf.readUtf();
                return new GraveMenu(id, inventory, new net.minecraft.world.SimpleContainer(size), pos, xp, owner);
            }));

    public DVGrave() {
        // Deprecated in Forge 47.4, but the constructor-injection replacement doesn't exist on NeoForge 1.20.1.
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, GraveConfig.SPEC);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> DVConfigScreen.register(MOD_ID, null, GraveConfig.SPEC, null));
    }
}
