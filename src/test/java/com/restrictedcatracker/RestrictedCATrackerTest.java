package com.restrictedcatracker;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class RestrictedCATrackerTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(RestrictedCATrackerPlugin.class);
        RuneLite.main(args);
    }
}