package com.restrictedcatracker;

public class BossEntry
{
    private String name;
    private int current;
    private int max;

    public BossEntry(String name, int current, int max)
    {
        this.name = name;
        this.current = current;
        this.max = max;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public int getCurrent()
    {
        return current;
    }

    public void setCurrent(int current)
    {
        this.current = current;
    }

    public int getMax()
    {
        return max;
    }

    public void setMax(int max)
    {
        this.max = max;
    }

    /**
     * A boss counts as complete if:
     *   - max is 0 (boss is entirely unobtainable on this account - there's
     *     nothing to do, so it's done by definition), or
     *   - current has reached the configured achievable max
     */
    public boolean isComplete()
    {
        return max == 0 || current >= max;
    }

    /**
     * Serializes this entry as "Name|current|max"
     */
    public String serialize()
    {
        return name + "|" + current + "|" + max;
    }

    /**
     * Parses an entry from "Name|current|max". Returns null if malformed.
     */
    public static BossEntry parse(String raw)
    {
        if (raw == null || raw.trim().isEmpty())
        {
            return null;
        }

        String[] parts = raw.split("\\|");
        if (parts.length != 3)
        {
            return null;
        }

        try
        {
            String name = parts[0];
            int current = Integer.parseInt(parts[1].trim());
            int max = Integer.parseInt(parts[2].trim());
            return new BossEntry(name, current, max);
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }
}