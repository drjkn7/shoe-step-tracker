package io.github.drjkn7.shoestep.ui.common

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import io.github.drjkn7.shoestep.R
import io.github.drjkn7.shoestep.data.model.ShoeType

@StringRes
fun ShoeType.labelRes(): Int = when (this) {
    ShoeType.ROAD_RUNNING -> R.string.type_road_running
    ShoeType.TRAIL_RUNNING -> R.string.type_trail_running
    ShoeType.HIKING -> R.string.type_hiking
    ShoeType.WALKING -> R.string.type_walking
    ShoeType.GYM_TRAINING -> R.string.type_gym_training
    ShoeType.CASUAL -> R.string.type_casual
    ShoeType.FORMAL_WORK -> R.string.type_formal_work
    ShoeType.OTHER -> R.string.type_other
}

/** The fixed 8-colour accent palette. Index = ShoeEntity.accentColor; never reorder. */
data class AccentColour(val colour: Color, @StringRes val nameRes: Int)

val ShoePalette: List<AccentColour> = listOf(
    AccentColour(Color(0xFFE53935), R.string.colour_red),
    AccentColour(Color(0xFFFB8C00), R.string.colour_orange),
    AccentColour(Color(0xFFFFB300), R.string.colour_amber),
    AccentColour(Color(0xFF43A047), R.string.colour_green),
    AccentColour(Color(0xFF00897B), R.string.colour_teal),
    AccentColour(Color(0xFF1E88E5), R.string.colour_blue),
    AccentColour(Color(0xFF3949AB), R.string.colour_indigo),
    AccentColour(Color(0xFFD81B60), R.string.colour_pink),
)

fun accentColour(index: Int): AccentColour = ShoePalette.getOrElse(index) { ShoePalette[0] }
