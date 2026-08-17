package com.example.myapplication

data class TunnelConfig(
    val targetPackage: String,
    val socksHost: String,
    val socksPort: Int,
)
