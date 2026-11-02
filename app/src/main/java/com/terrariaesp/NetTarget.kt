package com.terrariaesp

data class NetTarget(
    val kind: Int,
    val id: Int,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val life: Int,
    val maxLife: Int,
    val name: String
)
