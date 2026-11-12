package com.darkvolfi.dvteam.data;

import java.util.Locale;

/** Small enums shared by the server data, the sync packet and the screens. */
public final class ClanTypes {
    private ClanTypes() {}

    /** Clan management rights a rank can hold; stored as bit flags. The leader always has all of them. */
    public enum Manage {
        /** Invite players. */
        INVITE,
        /** Remove members ranked strictly below oneself. */
        KICK,
        /** Claim, unclaim and force-load chunks; found and rename settlements. */
        CLAIM,
        /** What allies and outsiders may do in each settlement. */
        SETTLEMENTS,
        /** Open the clan vault. */
        VAULT,
        /** Alliances, war, peace and surrender. */
        DIPLOMACY,
        /** Give members ranks strictly below one's own. */
        RANKS;

        public int bit() {
            return 1 << ordinal();
        }

        public boolean in(int flags) {
            return (flags & bit()) != 0;
        }

        public String translationKey() {
            return "dvteam.manage." + name().toLowerCase(Locale.ROOT);
        }

        public static int all() {
            return (1 << values().length) - 1;
        }
    }

    public enum SettlementType {
        CAPITAL, VILLAGE, TRADE;

        public String translationKey() {
            return "dvteam.settlement." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** What may be done in a settlement (by outsiders, allies, or a rank of the owning clan); stored as bit flags. */
    public enum Perm {
        BUILD, DOORS, CONTAINERS, ANIMALS, FARMING, TRADING;

        public int bit() {
            return 1 << ordinal();
        }

        public boolean in(int flags) {
            return (flags & bit()) != 0;
        }

        public String translationKey() {
            return "dvteam.perm." + name().toLowerCase(Locale.ROOT);
        }

        public static int all() {
            return (1 << values().length) - 1;
        }
    }

    public static int defaultAllyPerms(SettlementType type) {
        return Perm.DOORS.bit() | Perm.CONTAINERS.bit() | Perm.TRADING.bit();
    }

    public static int defaultPublicPerms(SettlementType type) {
        return type == SettlementType.TRADE ? Perm.DOORS.bit() | Perm.TRADING.bit() : Perm.TRADING.bit();
    }

    /** A colour for text: very dark colours are lifted toward white so they stay readable on dark panels and chat. */
    public static int readable(int color) {
        int r = color >> 16 & 0xFF, g = color >> 8 & 0xFF, b = color & 0xFF;
        int luma = (r * 299 + g * 587 + b * 114) / 1000;
        if (luma >= 90) return color;
        float t = (90 - luma) / 90f * 0.6f;
        r += Math.round((255 - r) * t);
        g += Math.round((255 - g) * t);
        b += Math.round((255 - b) * t);
        return r << 16 | g << 8 | b;
    }
}
