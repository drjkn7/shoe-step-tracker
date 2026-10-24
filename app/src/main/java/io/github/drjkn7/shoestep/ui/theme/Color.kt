package io.github.drjkn7.shoestep.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Silver Matte colour tokens (docs/DESIGN.md section 3). Light and night share these names, so
 * components only ever ask for a token, never a literal colour. Gradients are kept as colour stops
 * and turned into brushes by the helpers below.
 */
@Immutable
data class SilverColors(
    val isNight: Boolean,
    /** Screen backdrop, top to bottom (stop position 0..1 to colour). */
    val backdrop: List<Pair<Float, Color>>,
    val card: Color,
    /** Form card (Add / Edit shoe), a step darker than [card]. No night value in DESIGN.md: same as card. */
    val cardRegister: Color,
    /** Secondary buttons and icon buttons: a horizontal sheen, light edges and a darker centre. */
    val sheen: List<Color>,
    /** Inputs, unselected chips, switch track (the pressed-in look). */
    val inset: Color,
    /** Pop-ups, top to bottom. */
    val dialog: List<Color>,
    val edgeHighlight: Color,
    val divider: Color,
    val scrim: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val textOnAccent: Color,
    /** Links, values, "About" counts. */
    val accentInk: Color,
    /** Danger text (Unlink tag, Delete in menus, form errors). */
    val danger: Color,
    /** Filled Delete button. */
    val dangerFill: Color,
    /** Primary buttons, Add shoe, Running badge, selected chips; left to right. */
    val accent: List<Color>,
    val shadows: SilverShadows,
)

/** Colours used by the depth recipes (Depth.kt); night shadows are darker and highlights fainter. */
@Immutable
data class SilverShadows(
    val raisedKey: Color,
    val raisedAmbient: Color,
    val raisedHighlight: Color,
    val floatingKey: Color,
    val floatingAmbient: Color,
    val floatingHighlight: Color,
    val pressedDark: Color,
    val pressedLight: Color,
    val accentGlow: Color,
)

private val Ink = Color(0xFF1E2230)
private val GlowPurple = Color(0xFF9A4BDB)

/** Night only: the faint blue tight shadow under cards and pop-ups. */
private val NavyGlow = Color(0xFF3D68FF)

/** The accent gradient is the same in both themes. */
private val Accent = listOf(Color(0xFF7942DE), Color(0xFFA03CD0), Color(0xFFC92E87))

val LightSilverColors = SilverColors(
    isNight = false,
    backdrop = listOf(
        0f to Color(0xFFD1DDEC),
        0.08f to Color(0xFFE3E7ED),
        0.15f to Color(0xFFE0E3E8),
        1f to Color(0xFF52749E),
    ),
    card = Color(0xFFDEE2E9),
    cardRegister = Color(0xFFDADEE4),
    sheen = listOf(Color(0xFFE5EBF6), Color(0xFFC9D0DC), Color(0xFFDCE3F0)),
    inset = Color(0xFFBDC3CE),
    dialog = listOf(Color(0xFFE5E8ED), Color(0xFFD1D6DE)),
    edgeHighlight = Color.White.copy(alpha = 0.70f),
    divider = Ink.copy(alpha = 0.10f),
    scrim = Ink.copy(alpha = 0.42f),
    textPrimary = Ink,
    textMuted = Color(0xFF3F4658),
    textOnAccent = Color.White,
    accentInk = Color(0xFF59319A),
    danger = Color(0xFFA02334),
    dangerFill = Color(0xFFA02334),
    accent = Accent,
    shadows = SilverShadows(
        raisedKey = Ink.copy(alpha = 0.22f),
        raisedAmbient = Ink.copy(alpha = 0.12f),
        raisedHighlight = Color.White.copy(alpha = 0.75f),
        floatingKey = Ink.copy(alpha = 0.30f),
        floatingAmbient = Ink.copy(alpha = 0.14f),
        floatingHighlight = Color.White.copy(alpha = 0.80f),
        pressedDark = Ink.copy(alpha = 0.25f),
        pressedLight = Color.White.copy(alpha = 0.50f),
        accentGlow = GlowPurple.copy(alpha = 0.30f),
    ),
)

/** Night "Balanced": steel navy with a darker navy tint (approved 2026-10-24 on the comparison canvas). */
val NightSilverColors = SilverColors(
    isNight = true,
    backdrop = listOf(
        0f to Color(0xFF1A253D),
        0.08f to Color(0xFF161F36),
        0.15f to Color(0xFF121A2F),
        1f to Color(0xFF050914),
    ),
    card = Color(0xFF151F34),
    cardRegister = Color(0xFF151F34),
    sheen = listOf(Color(0xFF2B3650), Color(0xFF20293F), Color(0xFF28324A)),
    inset = Color(0xFF0D1322),
    dialog = listOf(Color(0xFF212B42), Color(0xFF192136)),
    edgeHighlight = Color.White.copy(alpha = 0.10f),
    divider = Color(0xFFC8D7FF).copy(alpha = 0.09f),
    scrim = Color(0xFF020610).copy(alpha = 0.62f),
    textPrimary = Color(0xFFE8EDF6),
    textMuted = Color(0xFFA7B3CA),
    textOnAccent = Color.White,
    accentInk = Color(0xFFC4A6FF),
    danger = Color(0xFFFF8A95),
    dangerFill = Color(0xFFC2394B),
    accent = Accent,
    shadows = SilverShadows(
        raisedKey = Color.Black.copy(alpha = 0.45f),
        raisedAmbient = NavyGlow.copy(alpha = 0.18f),
        raisedHighlight = Color.White.copy(alpha = 0.10f),
        floatingKey = Color.Black.copy(alpha = 0.55f),
        floatingAmbient = NavyGlow.copy(alpha = 0.18f),
        floatingHighlight = Color.White.copy(alpha = 0.12f),
        pressedDark = Color.Black.copy(alpha = 0.55f),
        pressedLight = Color(0xFF8CAAFF).copy(alpha = 0.08f),
        accentGlow = GlowPurple.copy(alpha = 0.35f),
    ),
)

fun SilverColors.backdropBrush(): Brush = Brush.verticalGradient(*backdrop.toTypedArray())
fun SilverColors.sheenBrush(): Brush = Brush.horizontalGradient(sheen)
fun SilverColors.dialogBrush(): Brush = Brush.verticalGradient(dialog)
fun SilverColors.accentBrush(): Brush = Brush.horizontalGradient(accent)

/**
 * Shoe colours, the same in both themes. The index is the stored accentColor of a pair: never
 * reorder. Colour is never the only cue (the name is always shown).
 */
val ShoeColors = listOf(
    Color(0xFFFF6B6B), // Red
    Color(0xFFFF9F43), // Orange
    Color(0xFFF7C548), // Amber
    Color(0xFF67B88D), // Green
    Color(0xFF2EC4B6), // Teal
    Color(0xFF46A9E8), // Blue
    Color(0xFF5A67D8), // Indigo
    Color(0xFFEC4899), // Pink
)
