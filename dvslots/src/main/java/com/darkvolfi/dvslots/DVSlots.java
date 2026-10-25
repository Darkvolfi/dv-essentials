package com.darkvolfi.dvslots;

import com.darkvolfi.dvlib.client.DVConfigScreen;
import com.darkvolfi.dvslots.menu.SlotsMenu;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import com.darkvolfi.dvslots.network.SlotsNetwork;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(DVSlots.MOD_ID)
public class DVSlots {
    public static final String MOD_ID = "dvslots";

    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MOD_ID);
    public static final RegistryObject<MenuType<SlotsMenu>> SLOTS_MENU = MENUS.register("slots",
            () -> IForgeMenuType.create((id, inventory, buf) -> new SlotsMenu(id, inventory)));

    public DVSlots() {
        // Deprecated in Forge 47.4, but the constructor-injection replacement doesn't exist on NeoForge 1.20.1.
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        MENUS.register(modBus);
        SlotsNetwork.register();
        // No settings, but the DV menu still lists it for its key binding.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> DVConfigScreen.register(MOD_ID, null, null, null));
    }
}
