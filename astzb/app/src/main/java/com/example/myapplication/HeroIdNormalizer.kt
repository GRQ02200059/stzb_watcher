package com.example.myapplication

object HeroIdNormalizer {
    fun normalize(heroId: Long): Long = when {
        heroId in 130000L..139999L -> heroId - 30000L
        heroId in 140000L..149999L -> heroId - 40000L
        else -> heroId
    }
}
