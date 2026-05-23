package com.turtle.tutiencore.core.manager;

import java.lang.reflect.Proxy;
import java.util.List;

import org.bukkit.Color;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import com.turtle.tutiencore.api.event.TuViGainEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TuLuyenManagerTest {

    @Test
    void resolvesHighestTuViBonusPermission() {
        assertEquals(100.0, TuLuyenManager.resolveHighestTuViBonus(List.of(
                "tutiencore.tuvi.bonus.20",
                "tutiencore.tuvi.bonus.100",
                "other.permission"
        )));
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
