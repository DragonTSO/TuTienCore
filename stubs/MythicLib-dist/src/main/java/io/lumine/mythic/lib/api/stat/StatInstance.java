package io.lumine.mythic.lib.api.stat;

import io.lumine.mythic.lib.api.stat.modifier.StatModifier;

public class StatInstance {
    public void addModifier(StatModifier modifier) {}
    public void removeModifier(String key) {}
    public boolean hasModifier(String key) { return false; }
}
