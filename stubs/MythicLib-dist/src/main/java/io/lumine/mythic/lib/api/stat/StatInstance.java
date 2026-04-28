package io.lumine.mythic.lib.api.stat;

import io.lumine.mythic.lib.api.stat.modifier.StatModifier;

import java.util.UUID;
import java.util.function.Predicate;

public class StatInstance {
    public void addModifier(StatModifier modifier) {}
    public void registerModifier(StatModifier modifier) {}
    public void removeModifier(UUID uniqueId) {}
    public void remove(String key) {}
    public void removeIf(Predicate<String> keyCondition) {}
    public boolean hasModifier(String key) { return false; }
}
