package com.restrictedcatracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("restrictedcatracker")
public interface RestrictedCATrackerConfig extends Config
{
    @ConfigItem(
            keyName = "bossData",
            name = "",
            description = "",
            hidden = true
    )
    default String bossData()
    {
        return "";
    }

    @ConfigItem(
            keyName = "bossData",
            name = "",
            description = ""
    )
    void bossData(String data);
}