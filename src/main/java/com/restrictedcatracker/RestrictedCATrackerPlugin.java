package com.restrictedcatracker;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@PluginDescriptor(
        name = "Restricted CA Tracker",
        description = "Tracks Combat Achievement progress per boss for accounts that can never reach 100% due to account restrictions, marking them complete once your manually-set achievable max is reached.",
        tags = {"combat", "achievements", "restricted", "ironman", "hardcore"}
)
public class RestrictedCATrackerPlugin extends Plugin
{
    @Inject
    private Client client;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private RestrictedCATrackerConfig config;

    private RestrictedCATrackerPanel panel;
    private NavigationButton navButton;

    @Provides
    RestrictedCATrackerConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(RestrictedCATrackerConfig.class);
    }

    @Override
    protected void startUp() throws Exception
    {
        panel = new RestrictedCATrackerPanel(config);

        final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/icon.png");

        navButton = NavigationButton.builder()
                .tooltip("Restricted CA Tracker")
                .icon(icon)
                .priority(5)
                .panel(panel)
                .build();

        clientToolbar.addNavigation(navButton);

        SwingUtilities.invokeLater(panel::rebuild);
    }

    @Override
    protected void shutDown() throws Exception
    {
        clientToolbar.removeNavigation(navButton);
        panel = null;
    }

    /**
     * The CA boss-list interface (group 716) lays out each boss's
     * progress text in a flat repeating block of 11 widgets per boss,
     * where the actual "x/y" text lives at offset 1 within each block:
     *   block 0 = decoration, block 1 = "x/y" text, blocks 2-10 = decoration
     * Confirmed via manual widget dump - this is not officially documented
     * by RuneLite, so it could break on a Jagex interface update.
     */
    private static final int CA_GROUP_ID = 716;
    private static final int CA_NAME_WIDGET_RAW_ID = 46923790;
    private static final int CA_PROGRESS_WIDGET_RAW_ID = 46923795;
    private static final int CA_PROGRESS_BLOCK_SIZE = 11;
    private static final int CA_PROGRESS_TEXT_OFFSET = 1;
    // The track/background of the bar sits at offset 2 (width 129, black).
    // The actual fill segments are offsets 7-10 - these start at width 0
    // (or -1) when progress is 0, and presumably grow as tasks complete.
    // We set all of them to the track's full width to render as 100% full.
    private static final int[] CA_PROGRESS_BAR_FILL_OFFSETS = { 7, 8, 9, 10 };
    private static final int GREEN_COLOR = 0x00C853;

    @Subscribe
    public void onGameTick(GameTick event)
    {
        // The CA boss list can be open/closed/re-filtered at any time, so
        // we just re-apply our overrides every tick while it's open. This
        // is cheap (a handful of widget lookups) and self-correcting if
        // Jagex's own UI ever overwrites our text.
        applyOverrides();
    }

    private void applyOverrides()
    {
        Widget container = client.getWidget(CA_GROUP_ID, 0);
        if (container == null)
        {
            // CA boss list isn't open right now.
            return;
        }

        Widget nameWidget = findWidgetById(container, CA_NAME_WIDGET_RAW_ID);
        Widget progressWidget = findWidgetById(container, CA_PROGRESS_WIDGET_RAW_ID);

        if (nameWidget == null || progressWidget == null)
        {
            return;
        }

        Widget[] names = nameWidget.getDynamicChildren();
        Widget[] progress = progressWidget.getDynamicChildren();

        if (names == null || progress == null)
        {
            return;
        }

        Map<String, BossEntry> configuredBosses = loadConfiguredBosses();
        if (configuredBosses.isEmpty())
        {
            return;
        }

        for (int i = 0; i < names.length; i++)
        {
            Widget nameW = names[i];
            if (nameW == null)
            {
                continue;
            }

            String bossName = nameW.getText();
            if (bossName == null || bossName.isEmpty())
            {
                continue;
            }

            BossEntry entry = configuredBosses.get(bossName.toLowerCase());
            if (entry == null)
            {
                continue;
            }

            int progressIndex = i * CA_PROGRESS_BLOCK_SIZE + CA_PROGRESS_TEXT_OFFSET;
            if (progressIndex < 0 || progressIndex >= progress.length)
            {
                continue;
            }

            Widget progressTextWidget = progress[progressIndex];
            if (progressTextWidget == null)
            {
                continue;
            }

            // Always override the displayed text to match your manually
            // entered numbers, regardless of whether they count as
            // "complete" yet. Only the complete case gets the green
            // recolor + filled bar treatment below - an in-progress
            // entry just shows your numbers with the game's normal
            // (untouched) styling.
            String overrideText = entry.getCurrent() + "/" + entry.getMax();
            if (!overrideText.equals(progressTextWidget.getText()))
            {
                progressTextWidget.setText(overrideText);
            }

            if (!entry.isComplete())
            {
                continue;
            }

            progressTextWidget.setTextColor(GREEN_COLOR);

            // Boss name itself also gets recolored green to match the
            // "fully complete" look from a genuinely 100% boss.
            nameW.setTextColor(GREEN_COLOR);

            // Fill the progress bar to match the track's full width and
            // recolor green, so it visually reads as a completed bar
            // instead of an empty/grey one.
            //
            // NOTE: using the track's raw width (offset 2) overshoots the
            // actual bar boundary - the fill segments apparently need to
            // be sized closer to the 4 tier-segment widths (offsets 3-6,
            // ~33px each) rather than the full 129px track. We use the
            // tier segment width here instead and leave a small margin.
            int blockStart = i * CA_PROGRESS_BLOCK_SIZE;
            int tierSegmentWidth = -1;
            int tierOffsetForWidth = 3;
            int tierIndex = blockStart + tierOffsetForWidth;
            if (tierIndex >= 0 && tierIndex < progress.length && progress[tierIndex] != null)
            {
                tierSegmentWidth = progress[tierIndex].getOriginalWidth();
            }

            if (tierSegmentWidth > 0)
            {
                for (int offset : CA_PROGRESS_BAR_FILL_OFFSETS)
                {
                    int fillIndex = blockStart + offset;
                    if (fillIndex < 0 || fillIndex >= progress.length)
                    {
                        continue;
                    }

                    Widget fillWidget = progress[fillIndex];
                    if (fillWidget == null)
                    {
                        continue;
                    }

                    fillWidget.setOriginalWidth(tierSegmentWidth);
                    fillWidget.setTextColor(GREEN_COLOR);
                    fillWidget.setFilled(true);
                    fillWidget.revalidate();
                }
            }
        }
    }

    /**
     * Reads the panel's stored boss config (same source the sidebar panel
     * uses) and returns a lookup by lowercase boss name -> entry. Includes
     * both complete and incomplete entries - incomplete ones still get
     * their text overridden in applyOverrides(), just without the green
     * recolor/bar-fill treatment.
     */
    private Map<String, BossEntry> loadConfiguredBosses()
    {
        Map<String, BossEntry> map = new HashMap<>();
        String raw = config.bossData();
        if (raw == null || raw.trim().isEmpty())
        {
            return map;
        }

        for (String chunk : raw.split(";;"))
        {
            BossEntry entry = BossEntry.parse(chunk);
            if (entry != null)
            {
                map.put(entry.getName().toLowerCase(), entry);
            }
        }

        return map;
    }

    /**
     * Recursively searches a widget tree for a widget matching a specific
     * raw id, checking children/staticChildren/dynamicChildren.
     */
    private Widget findWidgetById(Widget widget, int targetId)
    {
        if (widget == null)
        {
            return null;
        }

        if (widget.getId() == targetId)
        {
            return widget;
        }

        Widget[][] childArrays = { widget.getChildren(), widget.getStaticChildren(), widget.getDynamicChildren() };
        for (Widget[] arr : childArrays)
        {
            if (arr == null)
            {
                continue;
            }
            for (Widget child : arr)
            {
                Widget found = findWidgetById(child, targetId);
                if (found != null)
                {
                    return found;
                }
            }
        }

        return null;
    }
}