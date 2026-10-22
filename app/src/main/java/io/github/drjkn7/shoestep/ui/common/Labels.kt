package io.github.drjkn7.shoestep.ui.common

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import io.github.drjkn7.shoestep.ui.theme.ShoeColors
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
    R.string.colour_red,
    R.string.colour_orange,
    R.string.colour_amber,
    R.string.colour_green,
    R.string.colour_teal,
    R.string.colour_blue,
    R.string.colour_indigo,
    R.string.colour_pink,
).mapIndexed { index, name -> AccentColour(ShoeColors[index], name) }

fun accentColour(index: Int): AccentColour = ShoePalette.getOrElse(index) { ShoePalette[0] }
