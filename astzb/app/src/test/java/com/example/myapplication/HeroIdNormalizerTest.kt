package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Test

class HeroIdNormalizerTest {
    @Test
    fun convertsSeasonHeroIdsToBaseIds() {
        assertEquals(100497L, HeroIdNormalizer.normalize(130497L))
        assertEquals(100003L, HeroIdNormalizer.normalize(140003L))
    }

    @Test
    fun keepsBaseAndOtherIdsUnchanged() {
        listOf(0L, 100027L, 129999L, 150001L, -1L).forEach { id ->
            assertEquals(id, HeroIdNormalizer.normalize(id))
        }
    }
}
