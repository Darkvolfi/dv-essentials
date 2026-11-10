package com.darkvolfi.dvsouls;

import com.darkvolfi.dvlib.client.DVConfigScreen;
import com.darkvolfi.dvsouls.charm.CharmRecipes;
import com.darkvolfi.dvsouls.charm.Charms;
import com.darkvolfi.dvsouls.charm.EssenceItem;
import com.darkvolfi.dvsouls.charm.SoulCharm;
import com.darkvolfi.dvsouls.compat.BackpackCards;
import net.minecraftforge.fml.ModList;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import java.util.EnumMap;
import java.util.Map;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
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
    private static final DeferredRegister<RecipeSerializer<?>> RECIPES = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, MOD_ID);
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, MOD_ID);
    private static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(ForgeRegistries.FLUIDS, MOD_ID);

    /** Creatures no jar or collar can hold: bosses and the like. */
    public static final TagKey<EntityType<?>> UNCAPTURABLE = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(MOD_ID, "uncapturable"));

    public static final RegistryObject<Item> SOUL_JAR = ITEMS.register("soul_jar", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> FILLED_SOUL_JAR = ITEMS.register("filled_soul_jar", () -> new SoulItem(true));
    public static final RegistryObject<Item> COLLAR_SHARD = ITEMS.register("collar_shard", () -> new SoulItem(false));
    public static final RegistryObject<Item> SOUL_COLLAR = ITEMS.register("soul_collar", () -> new Item(new Item.Properties().stacksTo(16)));

    // Pure soul: refined from creatures' souls, it belongs to none. A liquid, in buckets and bottles.
    public static final RegistryObject<FluidType> PURE_SOUL_TYPE = FLUID_TYPES.register("pure_soul", PureSoul::new);
    public static final RegistryObject<ForgeFlowingFluid.Source> PURE_SOUL = FLUIDS.register("pure_soul",
            () -> new ForgeFlowingFluid.Source(pureSoul()));
    public static final RegistryObject<ForgeFlowingFluid.Flowing> FLOWING_PURE_SOUL = FLUIDS.register("flowing_pure_soul",
            () -> new ForgeFlowingFluid.Flowing(pureSoul()));
    /** Soul faces (the game's soul particles) drift up out of it now and then. */
    public static final RegistryObject<LiquidBlock> PURE_SOUL_BLOCK = BLOCKS.register("pure_soul",
            () -> new LiquidBlock(PURE_SOUL, BlockBehaviour.Properties.copy(Blocks.WATER).lightLevel(state -> 10)) {
                @Override
                public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
                    if (random.nextInt(6) != 0) return;
                    level.addParticle(ParticleTypes.SOUL, pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble() * 0.8,
                            pos.getZ() + random.nextDouble(), 0, 0.03, 0);
                }
            });
    /** Burns in any furnace, like lava. */
    public static final RegistryObject<Item> PURE_SOUL_BUCKET = ITEMS.register("pure_soul_bucket",
            () -> new BucketItem(PURE_SOUL, new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)) {
                @Override
                public int getBurnTime(ItemStack stack, @Nullable RecipeType<?> recipeType) {
                    return 12000;
                }
            });
    /** Stacks to one, like a potion: it pours into a bar from the one slot under it, leaving its bottle there. */
    public static final RegistryObject<Item> PURE_SOUL_BOTTLE = ITEMS.register("pure_soul_bottle",
            () -> new Item(new Item.Properties().craftRemainder(Items.GLASS_BOTTLE).stacksTo(1)));
    /** Coal soaked in pure soul: burns twice as long as coal in any furnace. */
    public static final RegistryObject<Item> SOUL_COAL = ITEMS.register("soul_coal", () -> new Item(new Item.Properties()) {
        @Override
        public int getBurnTime(ItemStack stack, @Nullable RecipeType<?> recipeType) {
            return 3200;
        }
    });

    /** Nine soul coal, like the game's coal block: it burns nine times as long. */
    public static final RegistryObject<Block> SOUL_COAL_BLOCK = BLOCKS.register("soul_coal_block",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.COAL_BLOCK)));
    public static final RegistryObject<Item> SOUL_COAL_BLOCK_ITEM = ITEMS.register("soul_coal_block",
            () -> new BlockItem(SOUL_COAL_BLOCK.get(), new Item.Properties()) {
                @Override
                public int getBurnTime(ItemStack stack, @Nullable RecipeType<?> recipeType) {
                    return 28800;
                }
            });

    private static ForgeFlowingFluid.Properties pureSoul() {
        return new ForgeFlowingFluid.Properties(PURE_SOUL_TYPE, PURE_SOUL, FLOWING_PURE_SOUL)
                .bucket(PURE_SOUL_BUCKET).block(PURE_SOUL_BLOCK).slopeFindDistance(2).levelDecreasePerBlock(2).tickRate(15);
    }

    /** A creature a foreign soul has corrupted: it looks like the creature, darkened, and attacks. */
    public static final RegistryObject<EntityType<Corrupted>> CORRUPTED = ENTITIES.register("corrupted",
            () -> EntityType.Builder.of(Corrupted::new, MobCategory.MONSTER).sized(0.9f, 1.4f).clientTrackingRange(10).build("corrupted"));

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
    public static final RegistryObject<BindingAltarBlock> BINDING_ALTAR = BLOCKS.register("binding_altar", BindingAltarBlock::new);
    /** Both altars keep what is laid on them the same way. */
    @SuppressWarnings("DataFlowIssue") // the data fixer type is unused for mod block entities
    public static final RegistryObject<BlockEntityType<SoulAltarBlock.SoulAltarBlockEntity>> SOUL_ALTAR_ENTITY = BLOCK_ENTITIES.register("soul_altar",
            () -> BlockEntityType.Builder.of(SoulAltarBlock.SoulAltarBlockEntity::new, SOUL_ALTAR.get(), BINDING_ALTAR.get()).build(null));

    // The Soul Furnace, Soul Smoker and Soul Blast Furnace, each with its block entity and menu.
    public static final Map<SoulFurnace.Kind, RegistryObject<SoulFurnace.Block>> FURNACES = new EnumMap<>(SoulFurnace.Kind.class);
    public static final Map<SoulFurnace.Kind, RegistryObject<BlockEntityType<SoulFurnace.Entity>>> FURNACE_ENTITIES = new EnumMap<>(SoulFurnace.Kind.class);
    public static final Map<SoulFurnace.Kind, RegistryObject<MenuType<SoulFurnace.Menu>>> FURNACE_MENUS = new EnumMap<>(SoulFurnace.Kind.class);
    /** Their DV Backpack upgrade cards; empty without DV Backpack. */
    public static final Map<SoulFurnace.Kind, RegistryObject<Item>> FURNACE_CARDS = new EnumMap<>(SoulFurnace.Kind.class);

    static {
        for (SoulFurnace.Kind kind : SoulFurnace.Kind.values()) {
            RegistryObject<SoulFurnace.Block> block = BLOCKS.register(kind.id(), () -> new SoulFurnace.Block(kind));
            FURNACES.put(kind, block);
            //noinspection DataFlowIssue: the data fixer type is unused for mod block entities
            FURNACE_ENTITIES.put(kind, BLOCK_ENTITIES.register(kind.id(),
                    () -> BlockEntityType.Builder.of((pos, state) -> new SoulFurnace.Entity(kind, pos, state), block.get()).build(null)));
            FURNACE_MENUS.put(kind, MENUS.register(kind.id(),
                    () -> new MenuType<>((id, inventory) -> new SoulFurnace.Menu(kind, id, inventory), FeatureFlags.VANILLA_SET)));
            ITEMS.register(kind.id(), () -> new BlockItem(block.get(), new Item.Properties()));
        }
        if (ModList.get().isLoaded("dvbackpack")) BackpackCards.register(ITEMS, FURNACE_CARDS);
    }

    public static final RegistryObject<SoulFuser.Block> SOUL_FUSER = BLOCKS.register("soul_fuser", SoulFuser.Block::new);
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<SoulFuser.Entity>> SOUL_FUSER_ENTITY = BLOCK_ENTITIES.register("soul_fuser",
            () -> BlockEntityType.Builder.of(SoulFuser.Entity::new, SOUL_FUSER.get()).build(null));
    public static final RegistryObject<MenuType<SoulFuser.Menu>> SOUL_FUSER_MENU = MENUS.register("soul_fuser",
            () -> new MenuType<>(SoulFuser.Menu::new, FeatureFlags.VANILLA_SET));

    public static final RegistryObject<SoulDistiller.Block> SOUL_DISTILLER = BLOCKS.register("soul_distiller", SoulDistiller.Block::new);
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<SoulDistiller.Entity>> SOUL_DISTILLER_ENTITY = BLOCK_ENTITIES.register("soul_distiller",
            () -> BlockEntityType.Builder.of(SoulDistiller.Entity::new, SOUL_DISTILLER.get()).build(null));
    public static final RegistryObject<MenuType<SoulDistiller.Menu>> SOUL_DISTILLER_MENU = MENUS.register("soul_distiller",
            () -> new MenuType<>(SoulDistiller.Menu::new, FeatureFlags.VANILLA_SET));

    public static final RegistryObject<SoulRefiner.Block> SOUL_REFINER = BLOCKS.register("soul_refiner", SoulRefiner.Block::new);
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<SoulRefiner.Entity>> SOUL_REFINER_ENTITY = BLOCK_ENTITIES.register("soul_refiner",
            () -> BlockEntityType.Builder.of(SoulRefiner.Entity::new, SOUL_REFINER.get()).build(null));
    public static final RegistryObject<MenuType<SoulRefiner.Menu>> SOUL_REFINER_MENU = MENUS.register("soul_refiner",
            () -> new MenuType<>(SoulRefiner.Menu::new, FeatureFlags.VANILLA_SET));

    // Soul charms: essences distilled from souls give their creatures' abilities.
    /** An empty glass test tube, for the Soul Distiller to drip an essence into. */
    public static final RegistryObject<Item> SOUL_TUBE = ITEMS.register("soul_tube", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> SOUL_ESSENCE = ITEMS.register("soul_essence", EssenceItem::new);
    public static final Map<SoulCharm.Tier, RegistryObject<Item>> CHARMS = new EnumMap<>(SoulCharm.Tier.class);
    public static final RegistryObject<RecipeSerializer<CharmRecipes.Essences>> CHARM_ESSENCES = RECIPES.register("charm_essences",
            () -> new SimpleCraftingRecipeSerializer<>(CharmRecipes.Essences::new));
    public static final RegistryObject<RecipeSerializer<CharmRecipes.Upgrade>> CHARM_UPGRADE = RECIPES.register("charm_upgrade",
            CharmRecipes.Upgrade.Serializer::new);
    /** Kept up by a spider's essence: climbs walls (see {@link Charms}). */
    public static final RegistryObject<MobEffect> CLIMBING = EFFECTS.register("climbing", () -> new CharmEffect(0x6B4F3A));
    /** Kept up by a lava horse's essence: walks on lava. */
    public static final RegistryObject<MobEffect> LAVA_WALKING = EFFECTS.register("lava_walking", () -> new CharmEffect(0xFF7A1A));

    /** A status effect that does nothing by itself: {@link Charms} looks for it. */
    private static final class CharmEffect extends MobEffect {
        CharmEffect(int colour) {
            super(MobEffectCategory.BENEFICIAL, colour);
        }
    }

    static {
        for (SoulCharm.Tier tier : SoulCharm.Tier.values()) {
            CHARMS.put(tier, ITEMS.register(tier.id() + "_soul_charm", () -> new SoulCharm(tier)));
        }
        ITEMS.register("soul_distiller", () -> new BlockItem(SOUL_DISTILLER.get(), new Item.Properties()));
        ITEMS.register("soul_refiner", () -> new BlockItem(SOUL_REFINER.get(), new Item.Properties()));
        ITEMS.register("soul_altar", () -> new BlockItem(SOUL_ALTAR.get(), new Item.Properties()));
        ITEMS.register("binding_altar", () -> new BlockItem(BINDING_ALTAR.get(), new Item.Properties()));
        ITEMS.register("soul_fuser", () -> new BlockItem(SOUL_FUSER.get(), new Item.Properties()));
        TABS.register("souls", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.dvsouls"))
                .icon(() -> new ItemStack(SOUL_JAR.get()))
                .displayItems((params, output) -> {
                    output.accept(SOUL_JAR.get());
                    output.accept(SOUL_COLLAR.get());
                    output.accept(SOUL_REFINER.get());
                    output.accept(PURE_SOUL_BUCKET.get());
                    output.accept(PURE_SOUL_BOTTLE.get());
                    output.accept(SOUL_COAL.get());
                    output.accept(SOUL_COAL_BLOCK_ITEM.get());
                    output.accept(BINDING_ALTAR.get());
                    output.accept(SOUL_ALTAR.get());
                    for (RegistryObject<SoulFurnace.Block> furnace : FURNACES.values()) output.accept(furnace.get());
                    for (RegistryObject<Item> card : FURNACE_CARDS.values()) output.accept(card.get());
                    output.accept(SOUL_FUSER.get());
                    output.accept(SOUL_DISTILLER.get());
                    output.accept(SOUL_TUBE.get());
                    for (RegistryObject<Item> charm : CHARMS.values()) output.accept(charm.get());
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
        RECIPES.register(modBus);
        EFFECTS.register(modBus);
        FLUID_TYPES.register(modBus);
        FLUIDS.register(modBus);
        modBus.addListener(DVSouls::onAttributes);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SoulsConfig.SPEC);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> DVConfigScreen.register(MOD_ID, null, SoulsConfig.SPEC, null));
    }

    /** Each hybrid has the attributes of the creature it is built on. */
    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(WOOLLY_COW.get(), Cow.createAttributes().build());
        event.put(FEATHERED_PIG.get(), Pig.createAttributes().build());
        event.put(LAVA_HORSE.get(), AbstractHorse.createBaseHorseAttributes().build());
        event.put(WOLF_FOX.get(), Wolf.createAttributes().build());
        event.put(CORRUPTED.get(), Corrupted.createAttributes().build());
    }
}
