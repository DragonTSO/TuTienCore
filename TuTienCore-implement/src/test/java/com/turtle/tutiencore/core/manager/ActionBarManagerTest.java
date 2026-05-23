package com.turtle.tutiencore.core.manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActionBarManagerTest {

    @Test
    void appliesBuiltInHealthPlaceholders() {
        assertEquals("HP 19/20", ActionBarManager.applyBuiltInPlaceholders("HP {health}/{max_health}", 18.6D, 20.0D));
    }
}
