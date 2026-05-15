package net.Indyuce.mmoitems;

import java.util.function.Predicate;
import net.Indyuce.mmoitems.stat.type.ItemStat;

public class MMOItems {
    public static final MMOItems plugin = new MMOItems();
    private final StatRegistry stats = new StatRegistry();

    public StatRegistry getStats() {
        return stats;
    }

    public static class StatRegistry {
        public void unregisterIf(Predicate<ItemStat> predicate) {
        }

        public void register(ItemStat stat) {
        }
    }
}
