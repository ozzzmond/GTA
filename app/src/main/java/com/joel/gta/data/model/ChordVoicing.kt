package com.joel.gta.data.model

data class ChordVoicing(
    val chord: String,
    val baseFret: Int = 1,
    val frets: List<Int>, // 6 elements: string 6 (low E) to string 1 (high e). -1 = muted (X), 0 = open (O), >0 = fret
    val fingers: List<Int> = emptyList(), // 1=index, 2=middle, 3=ring, 4=pinky, 0=none
    val barres: List<Int> = emptyList()
)
