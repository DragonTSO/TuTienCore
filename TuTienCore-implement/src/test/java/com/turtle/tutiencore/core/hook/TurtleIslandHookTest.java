package com.turtle.tutiencore.core.hook;

import com.turtleisland.api.TurtleIslandProvider;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TurtleIslandHookTest {

    private final Player player = (Player) Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class<?>[] {Player.class},
            (proxy, method, args) -> null
    );

    @AfterEach
    void tearDown() {
        TurtleIslandProvider.setApi(null);
    }

    @Test
    void readsBonusFromOfficialProviderApi() {
        TurtleIslandProvider.setApi(new FakeTurtleIslandApi(true, 25.0));

        assertEquals(25.0, TurtleIslandHook.readProviderBonusPercent(
                player,
                TurtleIslandProvider.class.getClassLoader()
        ));
    }

    @Test
    void returnsZeroWhenProviderApiSaysPlayerCannotReceiveBonus() {
        TurtleIslandProvider.setApi(new FakeTurtleIslandApi(false, 25.0));

        assertEquals(0.0, TurtleIslandHook.readProviderBonusPercent(
                player,
                TurtleIslandProvider.class.getClassLoader()
        ));
    }

    @Test
    void clampsNegativeProviderBonusToZero() {
        TurtleIslandProvider.setApi(new FakeTurtleIslandApi(true, -5.0));

        assertEquals(0.0, TurtleIslandHook.readProviderBonusPercent(
                player,
                TurtleIslandProvider.class.getClassLoader()
        ));
    }

    @Test
    void readsEligibilityFromOfficialProviderApiEvenWithZeroBonus() {
        TurtleIslandProvider.setApi(new FakeTurtleIslandApi(true, 0.0));

        assertTrue(TurtleIslandHook.readProviderCanReceive(
                player,
                TurtleIslandProvider.class.getClassLoader()
        ));
    }

    @Test
    void returnsZeroWhenProviderApiIsUnavailable() {
        assertEquals(0.0, TurtleIslandHook.readProviderBonusPercent(
                player,
                TurtleIslandProvider.class.getClassLoader()
        ));
    }

    public record FakeTurtleIslandApi(boolean allowed, double bonus) {

        public boolean canReceiveIslandCultivationBonus(Player player) {
            return allowed;
        }

        public double getCultivationTuViBonusPercent(Player player) {
            return bonus;
        }
    }
}
