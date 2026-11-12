package com.darkvolfi.dvteam.data;

import com.darkvolfi.dvteam.data.ClanTypes.Manage;
import com.darkvolfi.dvteam.data.ClanTypes.Perm;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

/**
 * A rank a clan's leader defines: name, colour, management rights and what members of the rank may do on the
 * clan's own land. The leader is not a rank: the leader may always do everything.
 */
public final class ClanRank {
    /** The rank id the leader maps to in {@code Clan.members}. Real ranks start at 1. */
    public static final int LEADER_ID = 0;
    public static final int MAX_RANKS = 10;
    public static final int NAME_MAX = 24;
    /**
     * The starting ranks are named by translation keys, so every player reads them in their own language until the
     * leader renames them. A name with this prefix is shown translated.
     */
    private static final String KEY_PREFIX = "dvteam.rank.";
    public static final String DEFAULT_OFFICER = KEY_PREFIX + "officer";
    public static final String DEFAULT_MEMBER = KEY_PREFIX + "member";

    public final int id;
    public String name;
    public int color;
    /** {@link Manage} bits. */
    public int manage;
    /** {@link Perm} bits on the clan's own land. */
    public int land;

    public ClanRank(int id, String name, int color, int manage, int land) {
        this.id = id;
        this.name = name;
        this.color = color & 0xFFFFFF;
        this.manage = manage & Manage.all();
        this.land = land & Perm.all();
    }

    public Component displayName() {
        return name.startsWith(KEY_PREFIX) ? Component.translatable(name) : Component.literal(name);
    }

    public int textColor() {
        return ClanTypes.readable(color);
    }

    void write(FriendlyByteBuf buf) {
        buf.writeVarInt(id);
        buf.writeUtf(name, 64);
        buf.writeInt(color);
        buf.writeVarInt(manage);
        buf.writeVarInt(land);
    }

    static ClanRank read(FriendlyByteBuf buf) {
        return new ClanRank(buf.readVarInt(), buf.readUtf(64), buf.readInt(), buf.readVarInt(), buf.readVarInt());
    }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("Id", id);
        t.putString("Name", name);
        t.putInt("Color", color);
        t.putInt("Manage", manage);
        t.putInt("Land", land);
        return t;
    }

    static ClanRank load(CompoundTag t) {
        return new ClanRank(t.getInt("Id"), t.getString("Name"), t.getInt("Color"), t.getInt("Manage"), t.getInt("Land"));
    }
}
