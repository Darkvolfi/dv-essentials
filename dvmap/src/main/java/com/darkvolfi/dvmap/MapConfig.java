package com.darkvolfi.dvmap;

import net.minecraftforge.common.ForgeConfigSpec;

public final class MapConfig {
    public enum Corner {
        TOP_RIGHT,
        TOP_LEFT,
        BOTTOM_RIGHT,
        BOTTOM_LEFT
    }

    public enum Shape {
        SQUARE,
        CIRCLE
    }

    public enum LayerMode {
        AUTO,
        SURFACE,
        CAVE
    }

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue MINIMAP_ENABLED;
    public static final ForgeConfigSpec.EnumValue<Corner> MINIMAP_CORNER;
    public static final ForgeConfigSpec.EnumValue<Shape> MINIMAP_SHAPE;
    public static final ForgeConfigSpec.IntValue MINIMAP_SIZE;
    public static final ForgeConfigSpec.IntValue MINIMAP_ZOOM;
    public static final ForgeConfigSpec.IntValue MAP_RADIUS;
    public static final ForgeConfigSpec.BooleanValue ROTATE_WITH_PLAYER;
    public static final ForgeConfigSpec.EnumValue<LayerMode> LAYER_MODE;
    public static final ForgeConfigSpec.BooleanValue HEIGHT_SHADING;
    public static final ForgeConfigSpec.BooleanValue BIOME_BLENDING;
    public static final ForgeConfigSpec.BooleanValue BLOCK_LIGHT_SHADING;
    public static final ForgeConfigSpec.BooleanValue SHOW_COORDINATES;
    public static final ForgeConfigSpec.BooleanValue SHOW_PORTAL_COORDINATES;
    public static final ForgeConfigSpec.BooleanValue SHOW_BIOME;
    public static final ForgeConfigSpec.BooleanValue SHOW_TIME;
    public static final ForgeConfigSpec.BooleanValue SHOW_CHUNK_GRID;
    public static final ForgeConfigSpec.BooleanValue SHOW_PLAYERS;
    public static final ForgeConfigSpec.BooleanValue SHOW_HOSTILES;
    public static final ForgeConfigSpec.BooleanValue SHOW_ANIMALS;
    public static final ForgeConfigSpec.BooleanValue SHOW_ITEMS;
    public static final ForgeConfigSpec.BooleanValue MOB_FACES;
    public static final ForgeConfigSpec.BooleanValue DAY_NIGHT;
    public static final ForgeConfigSpec.BooleanValue HIDE_ME;
    public static final ForgeConfigSpec.BooleanValue HIDE_MY_NAME;
    public static final ForgeConfigSpec.BooleanValue SHOW_WAYPOINTS_MINIMAP;
    public static final ForgeConfigSpec.BooleanValue SHOW_WAYPOINTS_WORLD;
    public static final ForgeConfigSpec.BooleanValue DEATH_WAYPOINT;
    public static final ForgeConfigSpec.BooleanValue SHOW_STRUCTURES;
    public static final ForgeConfigSpec.BooleanValue SHOW_CREATE_TRAINS;
    public static final ForgeConfigSpec.BooleanValue TELEPORT_MESSAGE;
    public static final ForgeConfigSpec.ConfigValue<String> CREATE_TRACK_COLOR;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        MINIMAP_ENABLED = b.comment("Show the minimap on the HUD.")
                .define("minimapEnabled", true);
        MINIMAP_CORNER = b.comment("Which screen corner the minimap sits in.")
                .defineEnum("minimapCorner", Corner.TOP_RIGHT);
        MINIMAP_SHAPE = b.comment("Minimap shape: square or round.")
                .defineEnum("minimapShape", Shape.SQUARE);
        MINIMAP_SIZE = b.comment("Minimap size in GUI pixels (40 to 240).")
                .defineInRange("minimapSize", 96, 40, 240);
        MINIMAP_ZOOM = b.comment("Minimap zoom level (1 = wide, 6 = very close).")
                .defineInRange("minimapZoom", 2, 1, 6);
        ROTATE_WITH_PLAYER = b.comment("Rotate the minimap with the player's camera yaw instead of keeping North up.")
                .define("rotateWithPlayer", false);
        LAYER_MODE = b.comment("Map layer mode: AUTO switches to cave view when under a roof.")
                .defineEnum("layerMode", LayerMode.AUTO);
        MAP_RADIUS = b.comment("How far around you the map is drawn, in chunks. 0 follows your render distance, so everything you can see gets mapped.")
                .defineInRange("mapRadius", 0, 0, 32);
        HEIGHT_SHADING = b.comment("Shade terrain slopes and water depth.")
                .define("heightShading", true);
        BIOME_BLENDING = b.comment("Tint grass, foliage and water with biome colours.")
                .define("biomeBlending", true);
        BLOCK_LIGHT_SHADING = b.comment("Warmly highlight blocks lit by torches, lava or glowstone.")
                .define("blockLightShading", true);
        SHOW_COORDINATES = b.comment("Show X, Y, Z coordinates below the minimap.")
                .define("showCoordinates", true);
        SHOW_PORTAL_COORDINATES = b.comment("Below the coordinates: the matching Nether position in the Overworld (÷8), and the Overworld position in the Nether (×8).")
                .define("showPortalCoordinates", true);
        SHOW_BIOME = b.comment("Show the current biome name below the minimap.")
                .define("showBiome", true);
        SHOW_TIME = b.comment("Show in-game clock time below the minimap.")
                .define("showTime", true);
        SHOW_CHUNK_GRID = b.comment("Draw chunk boundary grid lines.")
                .define("showChunkGrid", false);
        SHOW_PLAYERS = b.comment("Show other players on the radar.")
                .define("showPlayers", true);
        SHOW_HOSTILES = b.comment("Show hostile monsters on the radar.")
                .define("showHostiles", true);
        SHOW_ANIMALS = b.comment("Show passive animals and creatures on the radar.")
                .define("showAnimals", true);
        SHOW_ITEMS = b.comment("Show dropped items on the radar.")
                .define("showItems", false);
        MOB_FACES = b.comment("Draw creatures as small faces instead of dots.")
                .define("mobFaces", true);
        DAY_NIGHT = b.comment("Darken the map at night, like the world around you.")
                .define("dayNight", true);
        HIDE_ME = b.comment("Hide yourself from other players' maps (needs DV Map on the server). Also hides your name.")
                .define("hideMe", false);
        HIDE_MY_NAME = b.comment("Hide your name on other players' maps, and the name tag over your head for players with DV Map if the server allows it (needs DV Map on the server).")
                .define("hideMyName", false);
        SHOW_WAYPOINTS_MINIMAP = b.comment("Show waypoint markers on the minimap.")
                .define("showWaypointsOnMinimap", true);
        SHOW_WAYPOINTS_WORLD = b.comment("Show waypoint beams and distance labels in the 3D world.")
                .define("showWaypointsInWorld", true);
        DEATH_WAYPOINT = b.comment("Automatically create or update a waypoint where you last died.")
                .define("deathWaypoint", true);
        SHOW_STRUCTURES = b.comment("Mark villages, fortresses and other structures you've been inside (needs DV Map on the server).")
                .define("showStructures", true);
        TELEPORT_MESSAGE = b.comment("Show the chat message (\"Teleported ... to ...\") after teleporting from the map or the waypoint list. Your own /tp commands always show it.")
                .define("teleportMessage", true);
        SHOW_CREATE_TRAINS = b.comment("Show Create's tracks and trains on the world map (only when Create is installed; loaded chunks only).")
                .define("showCreateTrains", true);
        CREATE_TRACK_COLOR = b.comment("Colour of Create tracks on the world map, as #RRGGBB.")
                .define("createTrackColor", "#FFAA33");
        SPEC = b.build();
    }

    private MapConfig() {}
}
