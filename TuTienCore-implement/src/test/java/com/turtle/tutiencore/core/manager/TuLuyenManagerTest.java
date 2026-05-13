package com.turtle.tutiencore.core.manager;

import java.lang.reflect.Proxy;
import java.util.List;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import com.turtle.tutiencore.api.event.TuViGainEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

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
}
