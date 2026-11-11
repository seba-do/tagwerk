package me.ttdrp.tagwerk.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/** So viel Platz muss eine Liste unten lassen, damit nichts unter der schwebenden Leiste verschwindet. */
@Composable
fun navClearance(): Dp =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 87.dp

@Composable
fun RoundIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(size * 0.6f))
    }
}

/** Kurze Beschriftung in Großbuchstaben, auf Wunsch mit Pfeil zum Auf- und Zuklappen. */
@Composable
fun PillLabel(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: Color = MaterialTheme.colorScheme.onBackground,
    leading: ImageVector? = null,
    expanded: Boolean? = null,
    tight: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    // Wie in der Vorlage: Aufgeklappt zeigt der Pfeil nach unten.
    val rotation by animateFloatAsState(if (expanded == false) 180f else 0f, tween(200), label = "chevron")
    Row(
        modifier
            .clip(CircleShape)
            .background(container)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(start = if (leading != null) 10.dp else 14.dp, end = if (expanded != null) 10.dp else 14.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (leading != null) Icon(leading, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Text(
            text.uppercase(Locale.GERMAN),
            style = MaterialTheme.typography.labelMedium.let { if (tight) it.copy(letterSpacing = 0.2.sp) else it },
            color = content,
        )
        if (expanded != null) {
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = content,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(rotation),
            )
        }
    }
}

/** Auswahl: gewählt in Akzentfarbe, sonst wie die übrigen Chips. */
@Composable
fun ChoicePill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PillLabel(
        text = label,
        modifier = modifier,
        container = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
        onClick = onClick,
    )
}

/** Der eine auffällige Knopf eines Screens. */
@Composable
fun InversePill(
    text: String?,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    description: String? = null,
) {
    Row(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.inverseSurface)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.size(22.dp))
        }
        if (text != null) {
            Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.inverseOnSurface)
        }
    }
}

@Composable
fun SmallPlus(tint: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(20.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
    }
}

/** Leerer Platzhalter: lädt ein, ohne etwas zu fordern. */
@Composable
fun DashedCard(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    showPlus: Boolean = true,
) {
    val line by animateColorAsState(
        if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        tween(120),
        label = "zone-line",
    )
    val fill by animateColorAsState(
        if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
        tween(120),
        label = "zone-fill",
    )
    val textColor = if (highlighted) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(CardShape)
            .background(fill)
            .drawBehind {
                val width = 1.5.dp.toPx()
                drawRoundRect(
                    line,
                    topLeft = Offset(width / 2, width / 2),
                    size = Size(size.width - width, size.height - width),
                    cornerRadius = CornerRadius(20.dp.toPx()),
                    style = Stroke(width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
                )
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
        )
        if (showPlus) SmallPlus(MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Gruppierte Einstellungskarte. */
@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(23.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        content = content,
    )
}

@Composable
fun GroupDivider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

@Composable
fun plainFieldColors(): TextFieldColors = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
)

private val OrbDark = Color(0xFF9F85FF)
private val OrbLight = Color(0xFFCDC0FB)

/** Eigenes Zeichen für den Assistenten: eine leuchtende Kugel mit Funkeln, keine Figur. */
@Composable
fun AiOrb(size: Dp, modifier: Modifier = Modifier, glow: Boolean = false) {
    Canvas(modifier.size(size)) {
        val radius = this.size.minDimension / 2f
        val core = if (glow) radius * 0.72f else radius
        if (glow) {
            drawCircle(Brush.radialGradient(listOf(OrbLight.copy(alpha = 0.45f), Color.Transparent), center, radius), radius)
            drawCircle(Brush.verticalGradient(listOf(Color.White, OrbDark), center.y - core, center.y + core), core)
        } else {
            drawCircle(Brush.verticalGradient(listOf(OrbDark, OrbLight), 0f, this.size.height), core)
        }
        drawSparkle(center, core * 0.42f, Color.White)
        drawSparkle(center + Offset(core * 0.42f, -core * 0.42f), core * 0.15f, Color.White)
    }
}

private fun DrawScope.drawSparkle(at: Offset, reach: Float, color: Color) {
    val waist = reach * 0.16f
    val path = Path().apply {
        moveTo(at.x, at.y - reach)
        lineTo(at.x + waist, at.y - waist)
        lineTo(at.x + reach, at.y)
        lineTo(at.x + waist, at.y + waist)
        lineTo(at.x, at.y + reach)
        lineTo(at.x - waist, at.y + waist)
        lineTo(at.x - reach, at.y)
        lineTo(at.x - waist, at.y - waist)
        close()
    }
    drawPath(path, color)
}
