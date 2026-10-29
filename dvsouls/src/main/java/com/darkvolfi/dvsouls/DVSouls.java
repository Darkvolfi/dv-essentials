package com.darkvolfi.dvsouls;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import com.darkvolfi.dvsouls.hybrid.FeatheredPig;
import com.darkvolfi.dvsouls.hybrid.LavaHorse;
import com.darkvolfi.dvsouls.hybrid.WolfFox;
import com.darkvolfi.dvsouls.hybrid.WoollyCow;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(DVSouls.MOD_ID)
public class DVSouls {
    public static final String MOD_ID = "dvsouls";

    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MOD_ID);

    /** Creatures no jar or collar can hold: bosses and the like. */
    public static final TagKey<EntityType<?>> UNCAPTURABLE = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(MOD_ID, "uncapturable"));

    public static final RegistryObject<Item> SOUL_JAR = ITEMS.register("soul_jar", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> FILLED_SOUL_JAR = ITEMS.register("filled_soul_jar", () -> new SoulItem(true));
    public static final RegistryObject<Item> COLLAR_SHARD = ITEMS.register("collar_shard", () -> new SoulItem(false));
    public static final RegistryObject<Item> SOUL_COLLAR = ITEMS.register("soul_collar", () -> new Item(new Item.Properties().stacksTo(16)));

    // Hybrids: made by fusing two souls. Each is sized like the creature it is built on.
    public static final RegistryObject<EntityType<WoollyCow>> WOOLLY_COW = ENTITIES.register("woolly_cow",
            () -> EntityType.Builder.of(WoollyCow::new, MobCategory.CREATURE).sized(0.9f, 1.4f).clientTrackingRange(10).build("woolly_cow"));
    public static final RegistryObject<EntityType<FeatheredPig>> FEATHERED_PIG = ENTITIES.register("feathered_pig",
            () -> EntityType.Builder.of(FeatheredPig::new, MobCategory.CREATURE).sized(0.9f, 0.9f).clientTrackingRange(10).build("feathered_pig"));
    public static final RegistryObject<EntityType<LavaHorse>> LAVA_HORSE = ENTITIES.register("lava_horse",
            () -> EntityType.Builder.of(LavaHorse::new, MobCategory.CREATURE).sized(1.3964844f, 1.6f).fireImmune().clientTrackingRange(10).build("lava_horse"));
    public static final RegistryObject<EntityType<WolfFox>> WOLF_FOX = ENTITIES.register("wolf_fox",
            () -> EntityType.Builder.of(WolfFox::new, MobCategory.CREATURE).sized(0.6f, 0.85f).clientTrackingRange(10).build("wolf_fox"));

    public static final RegistryObject<Item> WOOLLY_COW_EGG = ITEMS.register("woolly_cow_spawn_egg",
            () -> new ForgeSpawnEggItem(WOOLLY_COW, 0x443626, 0xF2F2F2, new Item.Properties()));
    public static final RegistryObject<Item> FEATHERED_PIG_EGG = ITEMS.register("feathered_pig_spawn_egg",
            () -> new ForgeSpawnEggItem(FEATHERED_PIG, 0xF0A5A2, 0xFFFFFF, new Item.Properties()));
    public static final RegistryObject<Item> LAVA_HORSE_EGG = ITEMS.register("lava_horse_spawn_egg",
            () -> new ForgeSpawnEggItem(LAVA_HORSE, 0x2A2420, 0xFF7A1A, new Item.Properties()));
    public static final RegistryObject<Item> WOLF_FOX_EGG = ITEMS.register("wolf_fox_spawn_egg",
            () -> new ForgeSpawnEggItem(WOLF_FOX, 0xE07A2E, 0xF6EBDD, new Item.Properties()));

    public static final RegistryObject<SoulAltarBlock> SOUL_ALTAR = BLOCKS.register("soul_altar", SoulAltarBlock::new);
    @SuppressWarnings("DataFlowIssue") // the data fixer type is unused for mod block entities
    public static final RegistryObject<BlockEntityType<SoulAltarBlock.SoulAltarBlockEntity>> SOUL_ALTAR_ENTITY = BLOCK_ENTITIES.register("soul_altar",
            () -> BlockEntityType.Builder.of(SoulAltarBlock.SoulAltarBlockEntity::new, SOUL_ALTAR.get()).build(null));

    public static final RegistryObject<SoulFurnace.Block> SOUL_FURNACE = BLOCKS.register("soul_furnace", SoulFurnace.Block::new);
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<SoulFurnace.Entity>> SOUL_FURNACE_ENTITY = BLOCK_ENTITIES.register("soul_furnace",
            () -> BlockEntityType.Builder.of(SoulFurnace.Entity::new, SOUL_FURNACE.get()).build(null));
    public static final RegistryObject<MenuType<SoulFurnace.Menu>> SOUL_FURNACE_MENU = MENUS.register("soul_furnace",
            () -> new MenuType<>(SoulFurnace.Menu::new, FeatureFlags.VANILLA_SET));

    public static final RegistryObject<SoulFuser.Block> SOUL_FUSER = BLOCKS.register("soul_fuser", SoulFuser.Block::new);
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<SoulFuser.Entity>> SOUL_FUSER_ENTITY = BLOCK_ENTITIES.register("soul_fuser",
            () -> BlockEntityType.Builder.of(SoulFuser.Entity::new, SOUL_FUSER.get()).build(null));
    public static final RegistryObject<MenuType<SoulFuser.Menu>> SOUL_FUSER_MENU = MENUS.register("soul_fuser",
            () -> new MenuType<>(SoulFuser.Menu::new, FeatureFlags.VANILLA_SET));

    static {
        ITEMS.register("soul_altar", () -> new BlockItem(SOUL_ALTAR.get(), new Item.Properties()));
        ITEMS.register("soul_furnace", () -> new BlockItem(SOUL_FURNACE.get(), new Item.Properties()));
        ITEMS.register("soul_fuser", () -> new BlockItem(SOUL_FUSER.get(), new Item.Properties()));
        TABS.register("souls", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.dvsouls"))
                .icon(() -> new ItemStack(SOUL_JAR.get()))
                .displayItems((params, output) -> {
                    output.accept(SOUL_JAR.get());
                    output.accept(SOUL_COLLAR.get());
                    output.accept(SOUL_ALTAR.get());
                    output.accept(SOUL_FURNACE.get());
                    output.accept(SOUL_FUSER.get());
                    output.accept(WOOLLY_COW_EGG.get());
                    output.accept(FEATHERED_PIG_EGG.get());
                    output.accept(LAVA_HORSE_EGG.get());
                    output.accept(WOLF_FOX_EGG.get());
                })
                .build());
    }

    public DVSouls() {
        // Deprecated in Forge 47.4, but the constructor-injection replacement doesn't exist on NeoForge 1.20.1.
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        TABS.register(modBus);
        ENTITIES.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(DVSouls::onAttributes);
    }

    /** Each hybrid has the attributes of the creature it is built on. */
    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(WOOLLY_COW.get(), Cow.createAttributes().build());
        event.put(FEATHERED_PIG.get(), Pig.createAttributes().build());
        event.put(LAVA_HORSE.get(), AbstractHorse.createBaseHorseAttributes().build());
        event.put(WOLF_FOX.get(), Wolf.createAttributes().build());
    }
}
