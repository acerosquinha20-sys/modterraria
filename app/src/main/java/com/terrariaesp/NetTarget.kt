package com.terrariaesp

/** Uma entidade recebida do mod. x/y/w/h sao fracao (0..1) da tela do jogo; vx/vy em fracao por ms. */
data class NetTarget(
    val kind: Int,      // 0 = player, 1 = NPC hostil, 2 = voce
    val id: Int,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val life: Int,
    val maxLife: Int,
    val dist: Int,      // distancia em blocos (calculada pelo jogo)
    val name: String,
    val vx: Float = 0f,
    val vy: Float = 0f
)

/** Um pacote decodificado. atMs = System.nanoTime()/1e6 no momento da chegada. */
class NetSnapshot(val atMs: Long, val seq: Int, val targets: List<NetTarget>)
