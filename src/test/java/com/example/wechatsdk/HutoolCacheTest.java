package com.example.wechatsdk;

import com.example.wechatsdk.util.HutoolCache;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HutoolCacheTest {

    @Test
    void shouldStoreAndReadValue() {
        HutoolCache.set("unit-test-key", "hello", 60);
        assertEquals("hello", HutoolCache.get("unit-test-key"));
    }

    @Test
    void shouldReturnNullForUnknownKey() {
        assertNull(HutoolCache.get("unit-test-missing"));
    }

}
