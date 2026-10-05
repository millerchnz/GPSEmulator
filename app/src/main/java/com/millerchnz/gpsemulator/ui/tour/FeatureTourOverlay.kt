package com.millerchnz.gpsemulator.ui.tour

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Full-screen coach marks. The dimmed layer blocks taps so switches are not flipped by accident.
 * The bottom bar is left undimmed so the tabs stay obvious while Next moves between screens.
 */
@Composable
fun FeatureTourOverlay(controller: FeatureTourController) {
    val step = controller.step ?: return
    val target = controller.bounds[step.anchor]
    val navBounds = controller.bounds[TourAnchors.BOTTOM_NAV]
    // If a target never lays out, still show the card so Next and Skip all stay reachable.
    val targetMissing = target == null
    var showUnanchored by remember(step.anchor) { mutableStateOf(false) }
    LaunchedEffect(step.anchor, targetMissing) {
        if (!targetMissing) {
            showUnanchored = false
        } else {
            delay(400)
            showUnanchored = true
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(1f)
            // Final pass so Next and Skip all receive the gesture first. Anything left over is
            // eaten here, which keeps the map from panning under the scrim.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        event.changes.forEach { change ->
                            if (!change.isConsumed) change.consume()
                        }
                    }
                }
            },
    ) {
        Scrim(target = target, navBounds = navBounds)
        if (target != null) {
            SpeechBubble(
                step = step,
                stepNumber = controller.index + 1,
                stepCount = TourSteps.size,
                isLast = controller.isLast,
                target = target,
                navTop = navBounds?.top,
                onNext = controller::next,
                onSkipAll = controller::skipAll,
            )
        } else if (showUnanchored) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                TourBubbleCard(
                    step = step,
                    stepNumber = controller.index + 1,
                    stepCount = TourSteps.size,
                    isLast = controller.isLast,
                    onNext = controller::next,
                    onSkipAll = controller::skipAll,
                )
            }
        }
    }
}

@Composable
private fun Scrim(
    target: Rect?,
    navBounds: Rect?,
) {
    val primary = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val pad = with(density) { 8.dp.toPx() }
    val radius = with(density) { 16.dp.toPx() }
    val stroke = with(density) { 3.dp.toPx() }
    Canvas(Modifier.fillMaxSize()) {
        val path = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(0f, 0f, size.width, size.height))
            if (target != null) {
                addRoundRect(RoundRect(target.inflate(pad), CornerRadius(radius, radius)))
            }
            if (navBounds != null) {
                addRect(navBounds)
            }
        }
        drawPath(path, Color.Black.copy(alpha = 0.62f))
        if (target != null) {
            val hole = target.inflate(pad)
            drawRoundRect(
                color = primary,
                topLeft = hole.topLeft,
                size = hole.size,
                cornerRadius = CornerRadius(radius, radius),
                style = Stroke(width = stroke),
            )
        }
    }
}

@Composable
private fun SpeechBubble(
    step: TourStep,
    stepNumber: Int,
    stepCount: Int,
    isLast: Boolean,
    target: Rect,
    navTop: Float?,
    onNext: () -> Unit,
    onSkipAll: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        var bubbleHeight by remember(step.anchor) { mutableIntStateOf(0) }
        val margin = with(density) { 16.dp.toPx() }
        val tailHeight = with(density) { 12.dp.toPx() }
        val gap = with(density) { 10.dp.toPx() }
        val screenW = with(density) { maxWidth.toPx() }
        val screenH = with(density) { maxHeight.toPx() }
        val statusTop = WindowInsets.statusBars.getTop(density).toFloat()
        val limitBottom = navTop ?: screenH
        val bubbleW = minOf(screenW - margin * 2, with(density) { 420.dp.toPx() })
        val measuredH = bubbleHeight.toFloat()
        val usedH = if (measuredH > 0f) measuredH else with(density) { 220.dp.toPx() }

        val spaceAbove = target.top - statusTop
        val spaceBelow = limitBottom - target.bottom
        val needed = usedH + tailHeight + gap
        val tailOnTop = spaceBelow >= needed || spaceBelow >= spaceAbove

        val rawTop = if (tailOnTop) {
            target.bottom + gap + tailHeight
        } else {
            target.top - gap - tailHeight - usedH
        }
        val minTop = statusTop + margin
        val maxTop = (limitBottom - margin - usedH).coerceAtLeast(minTop)
        val top = rawTop.clamp(minTop, maxTop)
        val left = (target.center.x - bubbleW / 2f).clamp(margin, (screenW - margin - bubbleW).coerceAtLeast(margin))
        val tailHalf = with(density) { 11.dp.toPx() }
        val tailInset = with(density) { 22.dp.toPx() }
        val tailCenter = (target.center.x - left).clamp(tailInset, (bubbleW - tailInset).coerceAtLeast(tailInset))

        val bubbleColor = MaterialTheme.colorScheme.surface
        Column(
            Modifier
                .offset { IntOffset(left.roundToInt(), (top - if (tailOnTop) tailHeight else 0f).roundToInt()) }
                .width(with(density) { bubbleW.toDp() })
                .graphicsLayer { alpha = if (bubbleHeight == 0) 0f else 1f },
        ) {
            if (tailOnTop) {
                BubbleTail(
                    pointsUp = true,
                    color = bubbleColor,
                    modifier = Modifier.offset {
                        IntOffset((tailCenter - tailHalf).roundToInt(), 1)
                    },
                )
            }
            TourBubbleCard(
                step = step,
                stepNumber = stepNumber,
                stepCount = stepCount,
                isLast = isLast,
                onNext = onNext,
                onSkipAll = onSkipAll,
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { bubbleHeight = it.height }
                    .zIndex(1f),
                color = bubbleColor,
            )
            if (!tailOnTop) {
                BubbleTail(
                    pointsUp = false,
                    color = bubbleColor,
                    modifier = Modifier.offset {
                        IntOffset((tailCenter - tailHalf).roundToInt(), -1)
                    },
                )
            }
        }
    }
}

@Composable
private fun TourBubbleCard(
    step: TourStep,
    stepNumber: Int,
    stepCount: Int,
    isLast: Boolean,
    onNext: () -> Unit,
    onSkipAll: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
) {
    Surface(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        color = color,
        shadowElevation = 10.dp,
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "$stepNumber of $stepCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = step.body,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onSkipAll) {
                    Text("Skip all")
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = onNext) {
                    Text(if (isLast) "Done" else "Next")
                }
            }
        }
    }
}

@Composable
private fun BubbleTail(
    pointsUp: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Spacer(
        modifier
            .size(width = 22.dp, height = 12.dp)
            .drawBehind {
                val path = Path().apply {
                    if (pointsUp) {
                        moveTo(0f, size.height)
                        lineTo(size.width / 2f, 0f)
                        lineTo(size.width, size.height)
                    } else {
                        moveTo(0f, 0f)
                        lineTo(size.width / 2f, size.height)
                        lineTo(size.width, 0f)
                    }
                    close()
                }
                drawPath(path, color)
            },
    )
}

private fun Float.clamp(min: Float, max: Float): Float = when {
    max < min -> min
    else -> coerceIn(min, max)
}
