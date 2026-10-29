package com.darkvolfi.dvnewcrafts;

import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

@Mod(DVNewCrafts.MOD_ID)
public class DVNewCrafts {
    public static final String MOD_ID = "dvnewcrafts";

    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    private static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, MOD_ID);

    /**
     * The copper torch's green flame. Made here rather than in the registry's supplier because the torch blocks need
     * it when they are made, and blocks are registered before particle types.
     */
    public static final SimpleParticleType COPPER_FLAME = new SimpleParticleType(false);

    static {
        PARTICLES.register("copper_flame", () -> COPPER_FLAME);
    }

    public static final RegistryObject<Item> COPPER_SWORD = ITEMS.register("copper_sword",
            () -> new SwordItem(CopperMaterials.TOOLS, 3, -2.4f, new Item.Properties()));
    public static final RegistryObject<Item> COPPER_SHOVEL = ITEMS.register("copper_shovel",
            () -> new ShovelItem(CopperMaterials.TOOLS, 1.5f, -3.0f, new Item.Properties()));
    public static final RegistryObject<Item> COPPER_PICKAXE = ITEMS.register("copper_pickaxe",
            () -> new PickaxeItem(CopperMaterials.TOOLS, 1, -2.8f, new Item.Properties()));
    public static final RegistryObject<Item> COPPER_AXE = ITEMS.register("copper_axe",
            () -> new AxeItem(CopperMaterials.TOOLS, 7.0f, -3.2f, new Item.Properties()));
    public static final RegistryObject<Item> COPPER_HOE = ITEMS.register("copper_hoe",
            () -> new HoeItem(CopperMaterials.TOOLS, -1, -2.0f, new Item.Properties()));

    public static final RegistryObject<Item> COPPER_HELMET = armor("copper_helmet", ArmorItem.Type.HELMET);
    public static final RegistryObject<Item> COPPER_CHESTPLATE = armor("copper_chestplate", ArmorItem.Type.CHESTPLATE);
    public static final RegistryObject<Item> COPPER_LEGGINGS = armor("copper_leggings", ArmorItem.Type.LEGGINGS);
    public static final RegistryObject<Item> COPPER_BOOTS = armor("copper_boots", ArmorItem.Type.BOOTS);

    public static final RegistryObject<Item> COPPER_NUGGET = ITEMS.register("copper_nugget", () -> new Item(new Item.Properties()));

    public static final RegistryObject<Block> COPPER_TORCH = BLOCKS.register("copper_torch",
            () -> new TorchBlock(BlockBehaviour.Properties.copy(Blocks.TORCH), COPPER_FLAME));
    public static final RegistryObject<Block> COPPER_WALL_TORCH = BLOCKS.register("copper_wall_torch",
            () -> new WallTorchBlock(BlockBehaviour.Properties.copy(Blocks.WALL_TORCH).dropsLike(COPPER_TORCH.get()), COPPER_FLAME));
    public static final RegistryObject<Block> COPPER_LANTERN = BLOCKS.register("copper_lantern",
            () -> new LanternBlock(BlockBehaviour.Properties.copy(Blocks.LANTERN).mapColor(MapColor.COLOR_ORANGE)));
    public static final RegistryObject<Block> COPPER_CHAIN = BLOCKS.register("copper_chain",
            () -> new ChainBlock(BlockBehaviour.Properties.copy(Blocks.CHAIN).mapColor(MapColor.COLOR_ORANGE)));
    public static final RegistryObject<Block> COPPER_BARS = BLOCKS.register("copper_bars",
            () -> new IronBarsBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BARS).mapColor(MapColor.COLOR_ORANGE)));

    static {
        ITEMS.register("copper_torch", () -> new StandingAndWallBlockItem(COPPER_TORCH.get(), COPPER_WALL_TORCH.get(),
                new Item.Properties(), Direction.DOWN));
        blockItem(COPPER_LANTERN);
        blockItem(COPPER_CHAIN);
        blockItem(COPPER_BARS);
    }

    public DVNewCrafts() {
        // Deprecated in Forge 47.4, but the constructor-injection replacement doesn't exist on NeoForge 1.20.1.
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        PARTICLES.register(modBus);
        modBus.addListener(DVNewCrafts::onCreativeTabs);
    }

    private static RegistryObject<Item> armor(String name, ArmorItem.Type type) {
        return ITEMS.register(name, () -> new ArmorItem(CopperMaterials.ARMOR, type, new Item.Properties()));
    }

    private static void blockItem(RegistryObject<Block> block) {
        ITEMS.register(block.getId().getPath(), () -> new BlockItem(block.get(), new Item.Properties()));
    }

    /** Each copper thing goes into the game's own tabs, next to its stone or iron counterpart. */
    private static void onCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        var tab = event.getTabKey();
        if (tab == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            after(event, Items.STONE_HOE, COPPER_SHOVEL, COPPER_PICKAXE, COPPER_AXE, COPPER_HOE);
        } else if (tab == CreativeModeTabs.COMBAT) {
            after(event, Items.STONE_SWORD, COPPER_SWORD);
            after(event, Items.STONE_AXE, COPPER_AXE);
            after(event, Items.LEATHER_BOOTS, COPPER_HELMET, COPPER_CHESTPLATE, COPPER_LEGGINGS, COPPER_BOOTS);
        } else if (tab == CreativeModeTabs.INGREDIENTS) {
            after(event, Items.IRON_NUGGET, COPPER_NUGGET);
        } else if (tab == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            after(event, Items.SOUL_TORCH, COPPER_TORCH.get());
            after(event, Items.SOUL_LANTERN, COPPER_LANTERN.get());
        } else if (tab == CreativeModeTabs.BUILDING_BLOCKS) {
            after(event, Items.CHAIN, COPPER_BARS.get(), COPPER_CHAIN.get());
        }
    }

    @SafeVarargs
    private static void after(BuildCreativeModeTabContentsEvent event, ItemLike anchor, Supplier<? extends ItemLike>... items) {
        ItemLike[] likes = new ItemLike[items.length];
        for (int i = 0; i < items.length; i++) likes[i] = items[i].get();
        after(event, anchor, likes);
    }

    /** Puts the items after {@code anchor} in order, or at the end if the tab doesn't have it. */
    private static void after(BuildCreativeModeTabContentsEvent event, ItemLike anchor, ItemLike... items) {
        ItemStack previous = new ItemStack(anchor);
        for (ItemLike item : items) {
            ItemStack stack = new ItemStack(item);
            if (event.getEntries().contains(previous)) {
                event.getEntries().putAfter(previous, stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            } else {
                event.accept(stack);
            }
            previous = stack;
        }
    }
}
