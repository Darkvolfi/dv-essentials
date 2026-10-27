package com.darkvolfi.dvteam.data;

import java.util.Locale;

/** Small enums shared by the server data, the sync packet and the screens. */
public final class ClanTypes {
    private ClanTypes() {}

    public enum Rank {
        MEMBER, OFFICER, LEADER;

        public boolean atLeast(Rank other) {
            return ordinal() >= other.ordinal();
        }

        public String translationKey() {
            return "dvteam.rank." + name().toLowerCase(Locale.ROOT);
        }
    }

    public enum SettlementType {
        CAPITAL, VILLAGE, TRADE;

        public String translationKey() {
            return "dvteam.settlement." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** What outsiders may do in a settlement; stored as bit flags. */
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
}
