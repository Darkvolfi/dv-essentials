package com.darkvolfi.dvbackpack;

import com.darkvolfi.dvbackpack.network.BackpackNetwork;
import com.darkvolfi.dvbackpack.upgrade.UpgradeItem;
import com.darkvolfi.dvbackpack.upgrade.UpgradeType;
import com.darkvolfi.dvlib.client.DVConfigScreen;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

import java.util.EnumMap;
import java.util.Map;

@Mod(DVBackpack.MOD_ID)
public class DVBackpack {
    public static final String MOD_ID = "dvbackpack";
    public static final Logger LOG = LogUtils.getLogger();

    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MOD_ID);
    private static final DeferredRegister<RecipeSerializer<?>> RECIPES = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final Map<BackpackTier, RegistryObject<BackpackItem>> BACKPACKS = new EnumMap<>(BackpackTier.class);
    public static final Map<UpgradeType, RegistryObject<UpgradeItem>> UPGRADES = new EnumMap<>(UpgradeType.class);
    public static final RegistryObject<Item> UPGRADE_BASE = ITEMS.register("upgrade_base", () -> new Item(new Item.Properties()));
    /** Goes into the leather backpack's recipe; the backpack carries it rolled up under the bag. */
    public static final RegistryObject<Item> ROLLED_SLEEPING_BAG = ITEMS.register("rolled_sleeping_bag", () -> new Item(new Item.Properties()));

    static {
        for (BackpackTier tier : BackpackTier.values()) {
            BACKPACKS.put(tier, ITEMS.register(tier.id() + "_backpack", () -> new BackpackItem(tier)));
        }
        for (UpgradeType type : UpgradeType.values()) {
            if (type.ownCard()) UPGRADES.put(type, ITEMS.register(type.id() + "_upgrade", () -> new UpgradeItem(type)));
        }
    }

    public static final RegistryObject<BackpackBlock> BACKPACK_BLOCK = BLOCKS.register("backpack", BackpackBlock::new);
    public static final RegistryObject<SleepingBagBlock> SLEEPING_BAG = BLOCKS.register("sleeping_bag", SleepingBagBlock::new);
    @SuppressWarnings("DataFlowIssue") // the data fixer type is unused for mod block entities
    public static final RegistryObject<BlockEntityType<BackpackBlockEntity>> BACKPACK_ENTITY = BLOCK_ENTITIES.register("backpack",
            () -> BlockEntityType.Builder.of(BackpackBlockEntity::new, BACKPACK_BLOCK.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<SleepingBagBlockEntity>> SLEEPING_BAG_ENTITY = BLOCK_ENTITIES.register("sleeping_bag",
            () -> BlockEntityType.Builder.of(SleepingBagBlockEntity::new, SLEEPING_BAG.get()).build(null));
    public static final RegistryObject<MenuType<BackpackMenu>> BACKPACK_MENU = MENUS.register("backpack",
            () -> IForgeMenuType.create(BackpackMenu::new));
    public static final RegistryObject<TierUpgradeRecipe.Serializer> TIER_UPGRADE = RECIPES.register("tier_upgrade", TierUpgradeRecipe.Serializer::new);

    static {
        TABS.register("backpacks", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.dvbackpack"))
                .icon(() -> new ItemStack(BACKPACKS.get(BackpackTier.LEATHER).get()))
                .displayItems((params, output) -> {
                    BACKPACKS.values().forEach(item -> output.accept(item.get()));
                    output.accept(ROLLED_SLEEPING_BAG.get());
                    output.accept(UPGRADE_BASE.get());
                    UPGRADES.values().forEach(item -> output.accept(item.get()));
                })
                .build());
    }

    public DVBackpack() {
        // Deprecated in Forge 47.4, but the constructor-injection replacement doesn't exist on NeoForge 1.20.1.
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        RECIPES.register(modBus);
        TABS.register(modBus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, BackpackConfig.SPEC);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> DVConfigScreen.register(MOD_ID, null, BackpackConfig.SPEC, null));
        BackpackNetwork.register();
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) BackpackStorage.get(e.getServer()).tick(e.getServer().overworld());
        });
    }
}
