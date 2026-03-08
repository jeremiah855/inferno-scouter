package com.infernoscouter;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("infernoscout")
public interface InfernoScouterConfig extends Config
{
    @ConfigItem(
            position = 0,
            keyName = "showDebugRaw",
            name = "Show raw list",
            description = "Show the raw NPC list with coordinates under the 9-tile code"
    )
    default boolean showDebugRaw()
    {
        return true;
    }

    @ConfigItem(
            position = 1,
            keyName = "batColor",
            name = "Bat color",
            description = "Color for bat spawns"
    )
    default Color batColor()
    {
        return new Color(0xB3FAB6);
    }

    @ConfigItem(
            position = 2,
            keyName = "blobColor",
            name = "Blob color",
            description = "Color for blob spawns"
    )
    default Color blobColor()
    {
        return new Color(0xD9C24A);
    }

    @ConfigItem(
            position = 3,
            keyName = "meleeColor",
            name = "Melee color",
            description = "Color for melee spawns"
    )
    default Color meleeColor()
    {
        return new Color(0x3E434B);
    }

    @ConfigItem(
            position = 4,
            keyName = "rangerColor",
            name = "Ranger color",
            description = "Color for ranger spawns"
    )
    default Color rangerColor()
    {
        return new Color(0x43A85B);
    }

    @ConfigItem(
            position = 5,
            keyName = "magerColor",
            name = "Mager color",
            description = "Color for mager spawns"
    )
    default Color magerColor()
    {
        return new Color(0x4F86E8);
    }
}
