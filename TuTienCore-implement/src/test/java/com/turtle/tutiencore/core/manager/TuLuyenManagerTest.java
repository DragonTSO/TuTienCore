package com.turtle.tutiencore.core.manager;

import java.lang.reflect.Proxy;
import java.util.List;

import org.bukkit.Color;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import com.turtle.tutiencore.api.event.TuViGainEvent;
import com.turtle.tutiencore.core.model.CuboidZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TuLuyenManagerTest {

    @Test
    void resolvesStackedTuViBonusPermission() {
        assertEquals(80.0, TuLuyenManager.resolveTuViBonus(List.of(
                "tutiencore.tuvi.bonus.30",
                "tutiencore.tuvi.bonus.50",
                "other.permission"
        ), true));
    }

    @Test
    void resolvesHighestTuViBonusPermissionWhenStackingIsDisabled() {
        assertEquals(50.0, TuLuyenManager.resolveTuViBonus(List.of(
                "tutiencore.tuvi.bonus.30",
                "tutiencore.tuvi.bonus.50",
                "other.permission"
        ), false));
    }

    @Test
    void ignoresInvalidTuViBonusPermission() {
        assertEquals(0.0, TuLuyenManager.resolveHighestTuViBonus(List.of(
                "tutiencore.tuvi.bonus.nope"
        )));
    }

    @Test
    void appliesTeamBonusHologramPlaceholders() {
        assertEquals(List.of("&aTông môn: &f+20% &7x1.2"),
                TuLuyenManager.applyTeamBonusPlaceholders(
                        List.of("&aTông môn: &f+{team_bonus}% &7x{team_multiplier}"),
                        20.0
                ));
    }

    @Test
    void hidesTeamBonusHologramLinesWhenBonusInactive() {
        assertEquals(List.of(),
                TuLuyenManager.applyTeamBonusPlaceholders(
                        List.of("&aTông môn: &f+{team_bonus}%"),
                        0.0
                ));
    }

    @Test
    void appliesTotalCultivationBonusPlaceholderIncludingInfusion() {
        assertEquals("&fBonus: &a+35% &8| &5Lua Than +20%",
                TuLuyenManager.applyRewardPlaceholders(
                        "&fBonus: &a+{total_bonus}% &8| &5Lua Than +{infusion}%",
                        10.0,
                        5.0,
                        10.0,
                        3.0,
                        20.0,
                        13.8,
                        false
                ));
    }

    @Test
    void resolvesFlySwordTuViBonusFromConfiguredMmoItem() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("fly-sword.tuvi-buffs.enabled", true);
        config.set("fly-sword.tuvi-buffs.swords.FLY_SWORD.THANH_PHONG_KIEM.tuvi-bonus-percent", 50.0);
        config.set("fly-sword.tuvi-buffs.swords.FLY_SWORD.DINH_BA_KIEM.tuvi-bonus-percent", 75.0);
        config.set("fly-sword.tuvi-buffs.swords.FLY_SWORD.HA_CAM_KIEM.tuvi-bonus-percent", 100.0);
        config.set("fly-sword.tuvi-buffs.swords.FLY_SWORD.NETHER_KIEM.tuvi-bonus-percent", 150.0);

        assertEquals(50.0, TuLuyenManager.resolveFlySwordTuViBonusPercent(config, "fly-sword", "thanh phong kiem"));
        assertEquals(75.0, TuLuyenManager.resolveFlySwordTuViBonusPercent(config, "FLY_SWORD", "DINH_BA_KIEM"));
        assertEquals(100.0, TuLuyenManager.resolveFlySwordTuViBonusPercent(config, "FLY_SWORD", "HA-CAM-KIEM"));
        assertEquals(150.0, TuLuyenManager.resolveFlySwordTuViBonusPercent(config, "FLY_SWORD", "NETHER_KIEM"));
    }

    @Test
    void resolvesWeatherSpeedIntervalOnlyWhenActive() {
        assertEquals(75, TuLuyenManager.resolveWeatherSpeedInterval(100, 25.0, true));
        assertEquals(100, TuLuyenManager.resolveWeatherSpeedInterval(100, 25.0, false));
        assertEquals(1, TuLuyenManager.resolveWeatherSpeedInterval(2, 90.0, true));
    }

    @Test
    void appliesHaCamCompletionBonusAfterTotalReward() {
        assertEquals(260.0, TuLuyenManager.applyFlySwordCompletionBonus(200.0, 30.0));
        assertEquals(200.0, TuLuyenManager.applyFlySwordCompletionBonus(200.0, 0.0));
    }

    @Test
    void resolvesAfkZoneTuViBonusFromZone() {
        CuboidZone zone = new CuboidZone("afk", null, null);
        zone.setTuViBonusPercent(25.0D);

        assertEquals(25.0D, TuLuyenManager.resolveAfkZoneTuViBonus(zone));
        assertEquals(0.0D, TuLuyenManager.resolveAfkZoneTuViBonus(null));
    }

    @Test
    void createsTuLuyenGainEventWithSupportedSource() {
        Player player = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[] {Player.class},
                (proxy, method, args) -> null
        );

        TuViGainEvent event = TuLuyenManager.createTuLuyenGainEvent(player, 12.5);

        assertSame(player, event.getPlayer());
        assertEquals(12.5, event.getAmount());
        assertEquals("tuluyen", event.getSource());
    }

    @Test
    void triggersTuLuyenQuestMilestoneOnlyAtConfiguredBoundary() {
        assertFalse(TuLuyenManager.shouldTriggerTuLuyenQuestMilestone(1L, 36_000L));
        assertFalse(TuLuyenManager.shouldTriggerTuLuyenQuestMilestone(35_999L, 36_000L));
        assertTrue(TuLuyenManager.shouldTriggerTuLuyenQuestMilestone(36_000L, 36_000L));
        assertTrue(TuLuyenManager.shouldTriggerTuLuyenQuestMilestone(72_000L, 36_000L));
    }

    @Test
    void doesNotTriggerTuLuyenQuestMilestoneForInvalidInputs() {
        assertFalse(TuLuyenManager.shouldTriggerTuLuyenQuestMilestone(0L, 36_000L));
        assertFalse(TuLuyenManager.shouldTriggerTuLuyenQuestMilestone(36_000L, 0L));
        assertFalse(TuLuyenManager.shouldTriggerTuLuyenQuestMilestone(-10L, 36_000L));
    }

    @Test
    void textDisplayStyleDisablesDefaultBackground() {
        FakeTextDisplay display = new FakeTextDisplay();

        TuLuyenManager.applyTextDisplayStyle(display);

        assertTrue(display.shadowed);
        assertTrue(display.shadow);
        assertTrue(display.textShadow);
        assertFalse(display.defaultBackground);
        assertFalse(display.useDefaultBackground);
        assertEquals(0, display.backgroundColor.getAlpha());
        assertEquals(0, Color.fromARGB(display.background).getAlpha());
    }

    public static class FakeTextDisplay {
        boolean shadowed;
        boolean shadow;
        boolean textShadow;
        boolean defaultBackground = true;
        boolean useDefaultBackground = true;
        Color backgroundColor = Color.fromARGB(255, 0, 0, 0);
        int background = Color.fromARGB(255, 0, 0, 0).asARGB();

        public void setShadowed(boolean shadowed) {
            this.shadowed = shadowed;
        }

        public void setShadow(boolean shadow) {
            this.shadow = shadow;
        }

        public void setTextShadow(boolean textShadow) {
            this.textShadow = textShadow;
        }

        public void setDefaultBackground(boolean defaultBackground) {
            this.defaultBackground = defaultBackground;
        }

        public void setUseDefaultBackground(boolean useDefaultBackground) {
            this.useDefaultBackground = useDefaultBackground;
        }

        public void setBackgroundColor(Color backgroundColor) {
            this.backgroundColor = backgroundColor;
        }

        public void setBackground(Color backgroundColor) {
            this.backgroundColor = backgroundColor;
        }

        public void setBackground(int background) {
            this.background = background;
        }
    }
}
