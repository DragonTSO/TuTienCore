package com.turtle.tutiencore.core.hook;

import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.manager.StatManager;
import net.Indyuce.mmoitems.stat.type.ItemStat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

final class MMOItemsStatRegistry {

    private MMOItemsStatRegistry() {
    }

    static boolean registerOrReplace(ItemStat<?, ?> stat) {
        ItemStat<?, ?> current = MMOItems.plugin.getStats().get(stat.getId());
        if (current != null && current.getClass().getName().equals(stat.getClass().getName())) {
            return false;
        }

        if (current != null) {
            unregisterById(stat.getId());
        }

        MMOItems.plugin.getStats().register(stat);
        return true;
    }

    private static void unregisterById(String id) {
        StatManager manager = MMOItems.plugin.getStats();

        removeFromMapField(manager, "stats", id);
        removeAliases(manager, id);
        removeFromListField(manager, "numericStats", id);
        removeFromListField(manager, "itemRestrictions", id);
        removeFromListField(manager, "consumableActions", id);
        removeFromListField(manager, "playerConsumables", id);
        removeFromTypeCaches(id);
    }

    @SuppressWarnings("unchecked")
    private static void removeFromMapField(StatManager manager, String fieldName, String id) {
        Object value = getFieldValue(manager, fieldName);
        if (value instanceof Map<?, ?> map) {
            ((Map<String, ?>) map).remove(id);
        }
    }

    @SuppressWarnings("unchecked")
    private static void removeAliases(StatManager manager, String id) {
        Object value = getFieldValue(manager, "legacyAliases");
        if (value instanceof Map<?, ?> map) {
            ((Map<String, Object>) map).values().removeIf(entry -> hasStatId(entry, id));
        }
    }

    @SuppressWarnings("unchecked")
    private static void removeFromListField(StatManager manager, String fieldName, String id) {
        Object value = getFieldValue(manager, fieldName);
        if (value instanceof List<?> list) {
            ((List<Object>) list).removeIf(entry -> hasStatId(entry, id));
        }
    }

    @SuppressWarnings("unchecked")
    private static void removeFromTypeCaches(String id) {
        if (MMOItems.plugin.getTypes() == null) {
            return;
        }

        for (Type type : MMOItems.plugin.getTypes().getAll()) {
            ((List<Object>) (List<?>) type.getAvailableStats()).removeIf(entry -> hasStatId(entry, id));
        }
    }

    private static Object getFieldValue(Object target, String fieldName) {
        try {
            Field field = StatManager.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static boolean hasStatId(Object entry, String id) {
        if (entry instanceof ItemStat<?, ?> itemStat) {
            return id.equals(itemStat.getId());
        }

        try {
            Method getId = entry.getClass().getMethod("getId");
            Object value = getId.invoke(entry);
            return id.equals(String.valueOf(value));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }
}
