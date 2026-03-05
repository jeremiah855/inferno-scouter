package com.infernoscouter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("infernoscout")
public interface InfernoScouterConfig extends Config
{
    @ConfigItem(
            keyName = "showDebugRaw",
            name = "Show raw list",
            description = "Show the raw NPC list with coordinates under the 9-tile code"
    )
    default boolean showDebugRaw()
    {
        return true;
    }
}
