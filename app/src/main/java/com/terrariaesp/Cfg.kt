package com.terrariaesp

object Cfg {
    @Volatile var line = true
    @Volatile var box = true
    @Volatile var hp = true
    @Volatile var dist = true
    /** quantos blocos cabem na altura da tela (calibragem do tamanho do bloco) */
    @Volatile var tiles = 24f
    /** limiar de mudanca de pixel: maior = filtra mais */
    @Volatile var sens = 40
}
