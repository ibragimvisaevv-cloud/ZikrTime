package com.zikrtime.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zikrtime.app.ui.theme.Cream
import com.zikrtime.app.ui.theme.Emerald
import com.zikrtime.app.ui.theme.EmeraldDeep
import com.zikrtime.app.ui.theme.EmeraldNight
import com.zikrtime.app.ui.theme.Gold
import com.zikrtime.app.ui.theme.LocalDarkTheme
import kotlin.math.cos
import kotlin.math.sin

// Премиальный стиль: «стекло» поверх мягко переливающегося фона, блик на кнопках, плавные нажатия.

internal val EmeraldBright = Color(0xFF1A7D63)
internal val GoldLight = Color(0xFFFFE7A8)
internal val GoldDark = Color(0xFFC9962F)

/** Фон всего приложения: медленно плывущие пятна изумрудного и золотого света + едва заметный узор. */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    val dark = LocalDarkTheme.current
    val t by rememberInfiniteTransition(label = "aurora").animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(28_000, easing = LinearEasing)),
        label = "aurora-t",
    )
    val base = if (dark) Color(0xFF07100D) else Color(0xFFF7F3EA)
    val blobs = if (dark) {
        listOf(Emerald.copy(alpha = 0.38f), Gold.copy(alpha = 0.10f), Color(0xFF0E6B6B).copy(alpha = 0.22f))
    } else {
        listOf(Emerald.copy(alpha = 0.13f), Gold.copy(alpha = 0.20f), Color(0xFF2E8C7A).copy(alpha = 0.10f))
    }
    Box(modifier.fillMaxSize().background(base)) {
        Canvas(Modifier.fillMaxSize().parallax(14.dp, overscan = true)) {
            val w = size.width
            val h = size.height
            val r = maxOf(w, h) * 0.55f
            val centers = listOf(
                Offset(w * (0.15f + 0.10f * cos(t)), h * (0.10f + 0.06f * sin(t))),
                Offset(w * (0.95f + 0.08f * sin(t * 1.3f)), h * (0.35f + 0.08f * cos(t))),
                Offset(w * (0.30f + 0.12f * sin(t)), h * (0.95f + 0.05f * cos(t * 0.7f))),
            )
            centers.forEachIndexed { i, c ->
                drawCircle(Brush.radialGradient(listOf(blobs[i], Color.Transparent), center = c, radius = r), radius = r, center = c)
            }
        }
        StarPattern(Modifier.fillMaxSize().parallax((-10).dp, overscan = true), color = (if (dark) Gold else Emerald).copy(alpha = if (dark) 0.035f else 0.045f), cell = 88f)
    }
}

/** Плавное «утапливание» при нажатии. */
fun Modifier.pressScale(interaction: MutableInteractionSource, pressedScale: Float = 0.97f): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Бегущий блик, как по стеклу или золоту: проходит раз в несколько секунд. */
fun Modifier.sheen(alpha: Float = 0.28f, periodMs: Int = 3_600): Modifier = this

/** Стеклянная заливка и рамка для карточек. */
@Composable
fun glassFill(): Brush {
    val dark = LocalDarkTheme.current
    if (LocalNatural.current) return Brush.verticalGradient(if (dark) listOf(Color(0xF02B3B4D), Color(0xF01B2837)) else listOf(Color(0xF5F3F6F9), Color(0xE6D7E1EA)))
    val surface = MaterialTheme.colorScheme.surfaceContainer
    return Brush.verticalGradient(listOf(surface, surface))
}

@Composable
fun glassBorder(): BorderStroke {
    val dark = LocalDarkTheme.current
    if (!LocalNatural.current) return BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    return BorderStroke(
        1.dp,
        if (dark) Brush.linearGradient(listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.05f), Gold.copy(alpha = 0.18f)))
        else Brush.linearGradient(listOf(Color.White, Color.White.copy(alpha = 0.35f), Gold.copy(alpha = 0.25f))),
    )
}

/** Стеклянная поверхность с мягкой тенью. */
@Composable
fun Modifier.glass(shape: Shape = RoundedCornerShape(26.dp), elevation: Dp = 10.dp): Modifier {
    val dark = LocalDarkTheme.current
    val border = glassBorder()
    val fill = glassFill()
    return this
        .shadow(if (LocalNatural.current) elevation else 0.dp, shape, ambientColor = Emerald.copy(alpha = if (dark) 0.4f else 0.10f), spotColor = Emerald.copy(alpha = if (dark) 0.5f else 0.16f))
        .clip(shape)
        .background(fill)
        .border(border, shape)
}

/** Тёмно-изумрудный «премиальный» блок со звёздным узором, золотой рамкой и бликом. */
@Composable
fun GlassHero(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    if (!LocalNatural.current) {
        Box(modifier.fillMaxWidth().clip(shape).background(Color(0xFF263A49))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape), content = content)
        return
    }
    val glow by rememberInfiniteTransition(label = "hero").animateFloat(
        initialValue = 0.15f,
        targetValue = 0.32f,
        animationSpec = infiniteRepeatable(tween(4_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "hero-glow",
    )
    Box(
        modifier
            .fillMaxWidth()
            .shadow(18.dp, shape, ambientColor = Emerald, spotColor = EmeraldDeep)
            .clip(shape)
            .background(Brush.linearGradient(listOf(EmeraldBright, Emerald, EmeraldDeep, EmeraldNight)))
            .border(BorderStroke(1.dp, Brush.linearGradient(listOf(GoldLight.copy(alpha = 0.7f), Gold.copy(alpha = 0.15f), GoldLight.copy(alpha = 0.45f)))), shape),
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(
                Brush.radialGradient(listOf(Gold.copy(alpha = glow), Color.Transparent), center = Offset(size.width * 0.85f, 0f), radius = size.width * 0.7f),
                radius = size.width * 0.7f,
                center = Offset(size.width * 0.85f, 0f),
            )
        }
        StarPattern(Modifier.matchParentSize().parallax(10.dp, overscan = true), color = Gold.copy(alpha = 0.11f))
        Box(Modifier.matchParentSize().sheen(alpha = 0.10f, periodMs = 6_000))
        content()
    }
}

/** Главная кнопка: изумрудный градиент, бегущий блик и мягкое нажатие. */
@Composable
fun ShimmerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    if (!LocalNatural.current) {
        androidx.compose.material3.Button(onClick = onClick, enabled = enabled,
            modifier = modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) {
            Text(text, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
        }
        return
    }
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .pressScale(interaction)
            .shadow(if (enabled) 12.dp else 0.dp, shape, ambientColor = Emerald, spotColor = Emerald)
            .clip(shape)
            .background(Brush.linearGradient(if (enabled) listOf(EmeraldBright, Emerald, EmeraldDeep) else listOf(Color.Gray, Color.Gray)))
            .border(BorderStroke(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent))), shape)
            .sheen()
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

/** Золотая кнопка для тёмных блоков (будильник, знакомство, блокировка). */
@Composable
fun GoldButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .pressScale(interaction)
            .shadow(14.dp, shape, ambientColor = GoldDark, spotColor = GoldDark)
            .clip(shape)
            .background(Brush.linearGradient(listOf(GoldLight, Gold, GoldDark)))
            .sheen(alpha = 0.45f)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = Color(0xFF2B1E00),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

/** Небольшая золотистая плашка (тема вопроса, «Партнёр» и т. п.). */
@Composable
fun CategoryChip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(listOf(Gold.copy(alpha = 0.30f), GoldLight.copy(alpha = 0.40f))))
            .border(1.dp, Gold.copy(alpha = 0.45f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (LocalDarkTheme.current) GoldLight else Color(0xFF5C4310))
    }
}

/** Плавающая стеклянная панель вкладок со «скользящей» подсветкой выбранной вкладки. */
@Composable
fun <T> GlassNavBar(
    items: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    item: @Composable (T, Boolean) -> Unit,
) {
    val dark = LocalDarkTheme.current
    val shape = RoundedCornerShape(30.dp)
    val index = items.indexOf(selected).coerceAtLeast(0)
    val position by animateFloatAsState(index.toFloat(), spring(dampingRatio = 0.72f, stiffness = 380f), label = "nav")
    Box(
        modifier
            .fillMaxWidth()
            .shadow(20.dp, shape, ambientColor = Emerald.copy(alpha = 0.3f), spotColor = Emerald.copy(alpha = 0.35f))
            .clip(shape)
            .background(if (dark) Color(0xFF1B2C39) else Color(0xFFF6F8F9))
            .border(glassBorder(), shape),
    ) {
        Canvas(Modifier.matchParentSize().padding(6.dp)) {
            val w = size.width / items.size
            val left = w * position
            drawRoundRect(
                Brush.linearGradient(
                    if (dark) listOf(Emerald.copy(alpha = 0.9f), EmeraldDeep) else listOf(Color(0xFFDFF1E9), Color(0xFFCBE8DC)),
                    start = Offset(left, 0f), end = Offset(left + w, size.height),
                ),
                topLeft = Offset(left, 0f),
                size = androidx.compose.ui.geometry.Size(w, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx()),
            )
        }
        Row(Modifier.fillMaxWidth().padding(6.dp)) {
            items.forEach { t ->
                val interaction = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 58.dp)
                        .pressScale(interaction, 0.92f)
                        .clickable(interactionSource = interaction, indication = null) { onSelect(t) },
                    contentAlignment = Alignment.Center,
                ) { item(t, t == selected) }
            }
        }
    }
}
