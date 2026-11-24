package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.data.model.CustomPalette
import com.example.data.model.SetTag
import com.example.data.model.ThemeConfig
import com.example.data.model.ThemeMode
import com.example.data.model.ThemePresetId
import com.example.data.model.WorkingSubTag
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

data class ThemePresetDefinition(
    val id: ThemePresetId,
    val dark: CustomPalette,
    val light: CustomPalette
)

data class SetTagColors(
    val text: Color,
    val bg: Color,
    val border: Color
)

data class RepDayColors(
    val isDark: Boolean,
    val canvas: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val surfaceElevated: Color,
    val border: Color,
    val borderStrong: Color,
    val text: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val accentFg: Color,
    val accentHover: Color,
    val accentSoft: Color,
    val accentSoftFg: Color,
    val ok: Color,
    val okSurface: Color,
    val okInput: Color,
    val okFg: Color,
    val danger: Color,
    val dangerSurface: Color,
    val dropOrange: Color,
    val dropOrangeBg: Color,
    val dropOrangeBorder: Color
)

object ThemeEngine {

    val PRESETS: List<ThemePresetDefinition> = listOf(
        ThemePresetDefinition(
            id = ThemePresetId.OBSIDIAN_GOLD,
            dark = CustomPalette("#0B0C0E", "#15171C", "#E8BD4A", "#FAFAFB"),
            light = CustomPalette("#F5F5F3", "#FFFFFF", "#8F6508", "#111317")
        ),
        ThemePresetDefinition(
            id = ThemePresetId.VOLT_IRON,
            dark = CustomPalette("#0E1113", "#1A2024", "#C8F550", "#F2F5F7"),
            light = CustomPalette("#F4F6F4", "#FFFFFF", "#3B6600", "#121614")
        ),
        ThemePresetDefinition(
            id = ThemePresetId.CRIMSON_STEEL,
            dark = CustomPalette("#0D0E10", "#181A1E", "#FF6B6B", "#F8F9FA"),
            light = CustomPalette("#F7F5F5", "#FFFFFF", "#B91C28", "#141213")
        ),
        ThemePresetDefinition(
            id = ThemePresetId.COBALT_TITANIUM,
            dark = CustomPalette("#0B0F15", "#151C26", "#63ADFF", "#F4F7FB"),
            light = CustomPalette("#F3F6FB", "#FFFFFF", "#1558B0", "#0F1724")
        ),
        ThemePresetDefinition(
            id = ThemePresetId.EMERALD_CARBON,
            dark = CustomPalette("#0A0F0D", "#141D19", "#3CE09B", "#F2F7F5"),
            light = CustomPalette("#F3F8F6", "#FFFFFF", "#0B7549", "#0F1A15")
        ),
        ThemePresetDefinition(
            id = ThemePresetId.MONOCHROME_STARK,
            dark = CustomPalette("#08080A", "#151518", "#F4F4F6", "#FAFAFC"),
            light = CustomPalette("#F4F4F6", "#FFFFFF", "#141418", "#111114")
        )
    )

    fun presetFor(id: ThemePresetId): ThemePresetDefinition =
        PRESETS.find { it.id == id } ?: PRESETS.first()

    fun parseHexColor(hex: String, fallback: Color = Color(0xFF0B0C0E)): Color {
        val cleaned = hex.trim().removePrefix("#")
        return when (cleaned.length) {
            6 -> cleaned.toLongOrNull(16)?.let { Color(0xFF000000L or it) } ?: fallback
            8 -> cleaned.toLongOrNull(16)?.let { Color(it) } ?: fallback
            else -> fallback
        }
    }

    fun toHex(color: Color): String {
        val r = (color.red * 255f).roundToInt().coerceIn(0, 255)
        val g = (color.green * 255f).roundToInt().coerceIn(0, 255)
        val b = (color.blue * 255f).roundToInt().coerceIn(0, 255)
        return String.format("#%02X%02X%02X", r, g, b)
    }

    fun mix(a: Color, b: Color, weightB: Float): Color {
        val w = weightB.coerceIn(0f, 1f)
        val inv = 1f - w
        return Color(
            red = (a.red * inv + b.red * w).coerceIn(0f, 1f),
            green = (a.green * inv + b.green * w).coerceIn(0f, 1f),
            blue = (a.blue * inv + b.blue * w).coerceIn(0f, 1f),
            alpha = 1f
        )
    }

    private fun srgbChannelToLinear(c: Float): Double {
        val v = c.toDouble().coerceIn(0.0, 1.0)
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    fun relativeLuminance(color: Color): Double {
        val r = srgbChannelToLinear(color.red)
        val g = srgbChannelToLinear(color.green)
        val b = srgbChannelToLinear(color.blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    fun contrastRatio(a: Color, b: Color): Double {
        val l1 = relativeLuminance(a)
        val l2 = relativeLuminance(b)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    fun resolveEffectivePalette(config: ThemeConfig, systemDark: Boolean): CustomPalette {
        val wantDark = when (config.mode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> systemDark
        }
        val preset = presetFor(config.presetId)
        val basePreset = if (wantDark) preset.dark else preset.light
        if (!config.useCustomColors) return basePreset
        return (if (wantDark) config.customDark else config.customLight) ?: basePreset
    }

    fun deriveColors(palette: CustomPalette): RepDayColors {
        val canvas = parseHexColor(palette.canvas, Color(0xFF0B0C0E))
        val surface = parseHexColor(palette.surface, Color(0xFF15171C))
        val accent = parseHexColor(palette.accent, Color(0xFFE8BD4A))
        val text = parseHexColor(palette.text, Color(0xFFFAFAFB))

        val isDark = relativeLuminance(canvas) < 0.25

        val surfaceAlt = if (isDark) mix(canvas, surface, 0.55f) else mix(surface, canvas, 0.65f)
        val surfaceElevated = if (isDark) mix(surface, Color.White, 0.07f) else mix(surface, Color.Black, 0.05f)
        val border = mix(surface, text, 0.14f)
        val borderStrong = mix(surface, text, 0.24f)
        val textSecondary = if (isDark) mix(text, surface, 0.26f) else mix(text, surface, 0.32f)
        val textMuted = if (isDark) mix(text, surface, 0.44f) else mix(text, surface, 0.48f)

        val darkInk = Color(0xFF0E1013)
        val accentFg = if (contrastRatio(accent, darkInk) >= contrastRatio(accent, Color.White)) {
            darkInk
        } else {
            Color.White
        }
        val accentHover = if (isDark) mix(accent, Color.White, 0.14f) else mix(accent, Color.Black, 0.12f)
        val accentSoft = if (isDark) mix(surface, accent, 0.18f) else mix(surface, accent, 0.14f)
        val accentSoftFg = if (isDark) mix(accent, Color.White, 0.22f) else mix(accent, Color.Black, 0.28f)

        val ok = if (isDark) Color(0xFF7CE8A5) else Color(0xFF157A44)
        val okSurface = if (isDark) mix(surface, Color(0xFF1E824C), 0.22f) else mix(surface, Color(0xFF157A44), 0.11f)
        val okInput = if (isDark) mix(surface, Color(0xFF1E824C), 0.34f) else mix(surface, Color(0xFF157A44), 0.18f)
        val okFg = if (isDark) Color(0xFFE7FBEF) else Color(0xFF0E4E2B)

        val danger = if (isDark) Color(0xFFFF8A80) else Color(0xFFC62828)
        val dangerSurface = if (isDark) mix(surface, Color(0xFFD32F2F), 0.24f) else mix(surface, Color(0xFFC62828), 0.10f)

        val dropOrange = if (isDark) Color(0xFFFF9E66) else Color(0xFFB5430A)
        val dropOrangeBg = if (isDark) Color(0xFF382013) else Color(0xFFFDE9DD)
        val dropOrangeBorder = if (isDark) Color(0xFFFF9E66).copy(alpha = 0.60f) else Color(0xFFD9652B).copy(alpha = 0.50f)

        return RepDayColors(
            isDark = isDark,
            canvas = canvas,
            surface = surface,
            surfaceAlt = surfaceAlt,
            surfaceElevated = surfaceElevated,
            border = border,
            borderStrong = borderStrong,
            text = text,
            textSecondary = textSecondary,
            textMuted = textMuted,
            accent = accent,
            accentFg = accentFg,
            accentHover = accentHover,
            accentSoft = accentSoft,
            accentSoftFg = accentSoftFg,
            ok = ok,
            okSurface = okSurface,
            okInput = okInput,
            okFg = okFg,
            danger = danger,
            dangerSurface = dangerSurface,
            dropOrange = dropOrange,
            dropOrangeBg = dropOrangeBg,
            dropOrangeBorder = dropOrangeBorder
        )
    }

    fun setTagColors(tag: SetTag, subTag: WorkingSubTag?, colors: RepDayColors): SetTagColors {
        val isDark = colors.isDark
        return when (tag) {
            SetTag.WARMUP -> if (isDark) {
                SetTagColors(Color(0xFFF5C451), Color(0xFF332910), Color(0xFFF5C451).copy(alpha = 0.60f))
            } else {
                SetTagColors(Color(0xFF8C5B00), Color(0xFFFBF1D4), Color(0xFFC9911A).copy(alpha = 0.50f))
            }
            SetTag.WORKING -> when (subTag) {
                WorkingSubTag.TOP -> SetTagColors(
                    text = if (isDark) colors.accent else colors.accentSoftFg,
                    bg = colors.accentSoft,
                    border = colors.accent.copy(alpha = 0.75f)
                )
                WorkingSubTag.BACKOFF -> if (isDark) {
                    SetTagColors(Color(0xFF8CE8A9), Color(0xFF153121), Color(0xFF8CE8A9).copy(alpha = 0.60f))
                } else {
                    SetTagColors(Color(0xFF136B3D), Color(0xFFDFF5E7), Color(0xFF258F56).copy(alpha = 0.50f))
                }
                null -> SetTagColors(
                    text = if (isDark) colors.accent else colors.accentSoftFg,
                    bg = colors.accentSoft,
                    border = colors.accent.copy(alpha = 0.60f)
                )
            }
            SetTag.DROP -> SetTagColors(colors.dropOrange, colors.dropOrangeBg, colors.dropOrangeBorder)
            SetTag.MYO -> if (isDark) {
                SetTagColors(Color(0xFFC6A4FF), Color(0xFF281C3B), Color(0xFFC6A4FF).copy(alpha = 0.60f))
            } else {
                SetTagColors(Color(0xFF6830B8), Color(0xFFEFE6FD), Color(0xFF8D59DA).copy(alpha = 0.50f))
            }
            SetTag.PARTIALS -> if (isDark) {
                SetTagColors(Color(0xFF7FD1FF), Color(0xFF132A38), Color(0xFF7FD1FF).copy(alpha = 0.60f))
            } else {
                SetTagColors(Color(0xFF0B6399), Color(0xFFE1F3FC), Color(0xFF2B8AC2).copy(alpha = 0.50f))
            }
        }
    }
}
