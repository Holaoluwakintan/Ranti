package com.holaoluwakintan.ranti.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.holaoluwakintan.ranti.R

/**
 * The palette. v1.0: light and dark. Colours are read through getters backed by snapshot state,
 * so flipping [dark] recomposes every screen without losing the navigation stack.
 */
object C {
    var dark by mutableStateOf(false)

    val Bg: Color get() = if (dark) Color(0xFF120A1F) else Color(0xFFFBF3F7)
    val Card: Color get() = if (dark) Color(0xFF1E1430) else Color(0xFFFFFFFF)
    val Ink: Color get() = if (dark) Color(0xFFF6EFF9) else Color(0xFF1E1633)
    val InkSoft: Color get() = if (dark) Color(0xFFB9ACCB) else Color(0xFF6E6585)
    val InkFaint: Color get() = if (dark) Color(0xFF7F7395) else Color(0xFFA79FB8)
    val Line: Color get() = if (dark) Color(0xFF30244A) else Color(0xFFEFE4EC)
    val Plum = Color(0xFF24163A)
    val PlumDeep = Color(0xFF160C26)
    val PlumMid = Color(0xFF3A2556)
    val Coral = Color(0xFFFF6A3D)
    val CoralSoft: Color get() = if (dark) Color(0xFF3B1E2C) else Color(0xFFFFE6DA)
    val Gold = Color(0xFFFFB23F)
    val GoldSoft: Color get() = if (dark) Color(0xFF33261A) else Color(0xFFFFF1D6)
    val Mint = Color(0xFF1FA971)
    val MintSoft: Color get() = if (dark) Color(0xFF12302A) else Color(0xFFE2F6EC)
    val WhatsApp = Color(0xFF1DAA61)
    val Danger = Color(0xFFE5484D)
    val DangerSoft: Color get() = if (dark) Color(0xFF3A1A1E) else Color(0xFFFFE5E5)

    fun resolve(mode: String, systemDark: Boolean): Boolean = mode == "DARK" || (mode != "LIGHT" && systemDark)
}

val Sunset = Brush.linearGradient(listOf(C.Coral, C.Gold))
val NightSky = Brush.verticalGradient(listOf(C.PlumDeep, C.Plum, C.PlumMid))

val Fraunces = FontFamily(
    Font(R.font.fraunces_regular, FontWeight.Normal),
    Font(R.font.fraunces_semibold, FontWeight.SemiBold),
)
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

private fun typography() = Typography(
    displaySmall = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp, color = C.Ink),
    headlineMedium = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp, color = C.Ink),
    headlineSmall = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, color = C.Ink),
    titleLarge = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, color = C.Ink),
    titleMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, color = C.Ink),
    titleSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, color = C.Ink),
    bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, color = C.Ink),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, color = C.InkSoft),
    bodySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, color = C.InkSoft),
    labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.2.sp, color = C.InkSoft),
    labelSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, color = C.InkSoft),
)

@Composable
fun RantiTheme(content: @Composable () -> Unit) {
    val dark = C.dark
    val scheme = if (dark) darkColorScheme(
        primary = C.Coral, onPrimary = Color.White, secondary = C.Gold, background = C.Bg, onBackground = C.Ink,
        surface = C.Card, onSurface = C.Ink, surfaceVariant = C.Card, onSurfaceVariant = C.InkSoft, outline = C.Line,
        primaryContainer = C.CoralSoft, onPrimaryContainer = C.Ink, surfaceContainerHigh = C.Card, surfaceContainer = C.Card,
        surfaceContainerHighest = C.Line,
    ) else lightColorScheme(
        primary = C.Coral, onPrimary = Color.White, secondary = C.Gold, background = C.Bg, onBackground = C.Ink,
        surface = C.Card, onSurface = C.Ink, surfaceVariant = C.Bg, onSurfaceVariant = C.InkSoft, outline = C.Line,
        primaryContainer = C.CoralSoft, onPrimaryContainer = C.Ink,
    )
    MaterialTheme(colorScheme = scheme, typography = typography(), content = content)
}

/** Springy press feedback for anything clickable (v1.0). */
@Composable
fun Modifier.pressScale(interaction: androidx.compose.foundation.interaction.MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val s by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.96f else 1f, androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 600f), label = "press")
    return this.graphicsLayer { scaleX = s; scaleY = s }
}

// ---------- Shared building blocks ----------

@Composable
fun RCard(modifier: Modifier = Modifier, padding: Dp = 18.dp, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(
        modifier
            .let { if (onClick != null) it.pressScale(src) else it }
            .shadow(if (C.dark) 0.dp else 10.dp, shape, ambientColor = Color(0x1A2A1640), spotColor = Color(0x1F2A1640))
            .clip(shape)
            .background(C.Card)
            .let { if (C.dark) it.border(1.dp, C.Line, shape) else it }
            .let { if (onClick != null) it.clickable(interactionSource = src, indication = androidx.compose.material3.ripple(), onClick = onClick) else it }
            .padding(padding),
        content = content,
    )
}

@Composable
fun GradientButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        modifier
            .pressScale(src)
            .height(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) Sunset else Brush.linearGradient(listOf(C.Line, C.Line)))
            .clickable(interactionSource = src, indication = androidx.compose.material3.ripple(color = Color.White), enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) Color.White else C.InkFaint)
    }
}

@Composable
fun SoftButton(text: String, modifier: Modifier = Modifier, bg: Color = C.CoralSoft, fg: Color = C.Ink, onClick: () -> Unit) {
    Box(
        modifier.height(50.dp).clip(RoundedCornerShape(16.dp)).background(bg).clickable(onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = fg, textAlign = TextAlign.Center) }
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .then(if (selected) Modifier.background(Sunset) else Modifier.background(C.Card).border(1.dp, C.Line, shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium), color = if (selected) Color.White else C.Ink)
    }
}

@Composable
fun Avatar(name: String, size: Dp = 48.dp, strong: Boolean = false) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        Modifier.size(size).clip(CircleShape).background(if (strong) Sunset else Brush.linearGradient(listOf(C.CoralSoft, C.GoldSoft))),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.42f).sp, color = if (strong) Color.White else C.Coral)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, modifier = modifier.padding(top = 22.dp, bottom = 10.dp))
}

@Composable
fun TopBar(title: String, onBack: () -> Unit, action: (@Composable () -> Unit)? = null, dark: Boolean = false) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = if (dark) Color.White else C.Ink) }
        Text(title, style = MaterialTheme.typography.titleMedium, color = if (dark) Color.White else C.Ink, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun CountBadge(days: Long, size: Dp = 64.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(Sunset), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (days == 0L) {
                Text("🎉", fontSize = (size.value * 0.36f).sp)
            } else {
                Text("$days", fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.38f).sp, color = Color.White, lineHeight = (size.value * 0.4f).sp)
                Text(if (days == 1L) "day" else "days", fontFamily = Inter, fontSize = (size.value * 0.15f).sp, color = Color.White.copy(alpha = 0.9f), lineHeight = (size.value * 0.17f).sp)
            }
        }
    }
}
