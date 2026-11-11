package com.darkvolfi.dvnewcrafts.client;

import com.darkvolfi.dvlib.client.DVConfigScreen;
import com.darkvolfi.dvlib.client.DVMods;
import com.darkvolfi.dvnewcrafts.DVNewCrafts;

/** The only class that touches DV Lib; loaded only after checking that DV Lib is installed. */
final class DVLibLink {
    private DVLibLink() {}

    static void register() {
        // The DV menu only enables tiles of registered mods; there are no settings, so both specs are null.
        DVConfigScreen.register(DVNewCrafts.MOD_ID, null, null, null);
        DVMods.registerScreen(DVNewCrafts.MOD_ID, RecipeNoticeScreen::open);
    }
}
