package com.apkorganizer.ui.theme

import android.annotation.SuppressLint
import android.os.Build
import android.view.ViewParent
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The frosted-glass ("Transparent Glossy") blur system.
 *
 * Every glass surface — top bars, selection bars, snackbars, the navigation
 * drawer and dialog/sheet windows — is blurred heavily so nothing behind it
 * stays recognizable:
 *
 *  - In-window overlays ([FrostedSurface]) combine three layers: a painted
 *    replica of the glass canvas, a live snapshot of the content behind the
 *    surface blurred with a 52dp radius (Android 12+), and a translucent
 *    glass tint with a glossy rim.
 *  - Dialog and bottom-sheet windows ([DialogBlurBehind]) use the system
 *    window blur, so the whole app melts into a soft haze behind them.
 *  - Below Android 12 the frosted fills fall back to opaque slabs, so the
 *    background never shows through on any device.
 */
object GlassBlur {
    /** Heavy blur radius for the live backdrop snapshot behind glass panels. */
    val radius: Dp = 52.dp

    /** System window blur radius behind dialog / bottom-sheet windows. */
    val dialogRadius: Dp = 64.dp

    /** Default glossy rim alpha (white). */
    val borderAlpha: Float = 42f / 255f

    /** How strongly the live blurred snapshot shows over the painted frost. */
    const val snapshotAlpha: Float = 0.55f
}

/**
 * Shared blur state: one [GraphicsLayer] captures the screen content so glass
 * surfaces can sample (and blur) whatever sits behind them. Owned by the root
 * composable and shared through [LocalGlassBlurState].
 */
class GlassBlurState internal constructor(val layer: GraphicsLayer) {
    /** True while the capture layer is being recorded — surfaces must not
     * sample the layer during that pass to avoid re-entrant recording. */
    internal var isRecording: Boolean = false

    /** Size of the full glass canvas, used to align the painted backdrop. */
    var canvasSize: Size by mutableStateOf(Size.Unspecified)

    /** Position of the capture root in window coordinates. */
    internal var captureOrigin: Offset by mutableStateOf(Offset.Zero)
}

/** Provides the shared [GlassBlurState]; null disables live snapshot blur. */
val LocalGlassBlurState = staticCompositionLocalOf<GlassBlurState?> { null }

/** Creates (or reuses) the shared [GlassBlurState] for this window. */
@Composable
fun rememberGlassBlurState(): GlassBlurState {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBlurState(layer) }
}

/**
 * Captures everything drawn by this subtree into the shared blur layer and
 * draws the captured result. Apply it to the content area that scrolls under
 * floating glass overlays (never to the overlays themselves).
 */
fun Modifier.glassBlurSource(state: GlassBlurState?): Modifier {
    if (state == null) return this
    return this
        .onPlaced { state.captureOrigin = it.positionInRoot() }
        .drawWithContent {
            state.isRecording = true
            try {
                state.layer.record() {
                    this@drawWithContent.drawContent()
                }
            } finally {
                state.isRecording = false
            }
            drawLayer(state.layer)
        }
}

/**
 * Paints the signature glass canvas: the vertical navy gradient plus the two
 * soft azure/teal glows. [origin] is where the canvas' (0, 0) sits in this
 * draw scope's coordinates and [canvas] its full size — panels pass their own
 * position so the painted replica lines up seamlessly with the real canvas.
 *
 * [phase] (`0f..1f`, cyclic) drives the aurora drift of the v1.3.2 redesign:
 * both glows orbit slowly around their home positions and breathe in size,
 * which makes the glass feel alive without any layout work — the animation
 * only re-draws the backdrop layer. Frosted replicas keep `phase = 0` so the
 * opaque floor under glass panels stays perfectly still.
 */
internal fun DrawScope.drawGlassCanvas(
    origin: Offset,
    canvas: Size,
    backdrop: List<Color>,
    glowAColor: Color,
    glowBColor: Color,
    phase: Float = 0f,
) {
    val width = if (canvas.width > 0f) canvas.width else size.width
    val height = if (canvas.height > 0f) canvas.height else size.height

    drawRect(
        Brush.verticalGradient(
            backdrop,
            startY = origin.y,
            endY = origin.y + height,
        ),
    )

    val angle = (phase % 1f) * TWO_PI
    val drift = 30.dp.toPx()
    val waveA = sin(angle)
    val angleB = angle + PI_F
    val waveB = sin(angleB)

    // Matches GlassBackdrop's top-end glow: 360dp box offset (90, -80) dp.
    val glowASide = 360.dp.toPx() * (1f + 0.05f * waveA)
    drawRect(
        brush = Brush.radialGradient(
            listOf(glowAColor.copy(alpha = glowAColor.alpha * (0.85f + 0.15f * waveA)), Color.Transparent),
        ),
        topLeft = Offset(
            x = origin.x + width - glowASide + 90.dp.toPx() + cos(angle) * drift,
            y = origin.y - 80.dp.toPx() + waveA * drift * 0.6f,
        ),
        size = Size(glowASide, glowASide),
    )

    // Matches GlassBackdrop's bottom-start glow: 320dp box offset (-80, 90) dp.
    // Drifts half a cycle out of phase so the two glows never move in lockstep.
    val glowBSide = 320.dp.toPx() * (1f + 0.05f * waveB)
    drawRect(
        brush = Brush.radialGradient(
            listOf(glowBColor.copy(alpha = glowBColor.alpha * (0.85f + 0.15f * waveB)), Color.Transparent),
        ),
        topLeft = Offset(
            x = origin.x - 80.dp.toPx() + cos(angleB) * drift,
            y = origin.y + height - glowBSide + 90.dp.toPx() + waveB * drift * 0.6f,
        ),
        size = Size(glowBSide, glowBSide),
    )
}

// Float mirrors of kotlin.math.PI so the drift math stays Float-only.
private const val PI_F = 3.14159265f
private const val TWO_PI = 2f * PI_F

/** Paints the full glass canvas across this element (used by [com.apkorganizer.GlassBackdrop]). */
@Composable
fun Modifier.glassCanvas(phase: Float = 0f): Modifier {
    val scheme = MaterialTheme.colorScheme
    val backdrop = AppGradients.backdrop
    val glowA = scheme.primary.copy(alpha = 0.22f)
    val glowB = scheme.secondary.copy(alpha = 0.15f)
    return this.drawBehind {
        drawGlassCanvas(
            origin = Offset.Zero,
            canvas = size,
            backdrop = backdrop,
            glowAColor = glowA,
            glowBColor = glowB,
            phase = phase,
        )
    }
}

/**
 * Specular "wet" highlight of the glass language (v1.3.2): a soft white
 * vertical sheen across the top half plus a faint diagonal light streak.
 * Apply it **after** `background` and before `border` so it sits on top of
 * the fill and under the rim, e.g. on hero cards and list tiles.
 */
fun Modifier.glassSheen(): Modifier = this.drawBehind {
    drawRect(
        brush = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
            startY = 0f,
            endY = size.height * 0.50f,
        ),
    )
    drawRect(
        brush = Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.05f),
                Color.Transparent,
                Color.Transparent,
                Color.White.copy(alpha = 0.03f),
            ),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        ),
    )
}

/**
 * A heavily frosted glass panel. Nothing behind the panel stays visible:
 * a painted replica of the glass canvas forms an opaque floor, a live snapshot
 * of the content behind is blurred over it (Android 12+), and the glass tint
 * plus glossy rim complete the "Transparent Glossy" look.
 *
 * Used by the top bars, selection bars, snackbar and navigation drawer —
 * the overlays that float above scrolling content.
 */
@Composable
fun FrostedSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    tint: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    border: BorderStroke? = BorderStroke(1.dp, Color.White.copy(alpha = GlassBlur.borderAlpha)),
    useSnapshot: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = LocalGlassBlurState.current
    val scheme = MaterialTheme.colorScheme
    val backdrop = AppGradients.backdrop
    val glowA = scheme.primary.copy(alpha = 0.22f)
    val glowB = scheme.secondary.copy(alpha = 0.15f)
    var positionInRoot by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .onPlaced { positionInRoot = it.positionInRoot() }
            .clip(shape),
    ) {
        // 1) Painted replica of the glass canvas — opaque floor so nothing
        //    behind the panel can ever show through (works on every API level).
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawGlassCanvas(
                        origin = Offset(-positionInRoot.x, -positionInRoot.y),
                        canvas = state?.canvasSize ?: Size.Unspecified,
                        backdrop = backdrop,
                        glowAColor = glowA,
                        glowBColor = glowB,
                    )
                },
        )

        // 2) Live snapshot of what is behind, heavily blurred (Android 12+).
        if (useSnapshot && state != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = GlassBlur.snapshotAlpha }
                    .blur(GlassBlur.radius)
                    .drawBehind {
                        if (!state.isRecording) {
                            translate(
                                state.captureOrigin.x - positionInRoot.x,
                                state.captureOrigin.y - positionInRoot.y,
                            ) {
                                drawLayer(state.layer)
                            }
                        }
                    },
            )
        }

        // 3) Glass tint.
        Box(Modifier.fillMaxSize().background(tint))

        // 4) Specular sheen — the v1.3.2 glossy highlight that makes every
        //    bar read as polished glass rather than a flat translucent slab.
        Box(Modifier.fillMaxSize().glassSheen())

        // 5) Glossy rim.
        if (border != null) {
            Box(Modifier.fillMaxSize().border(border, shape))
        }

        content()
    }
}

/**
 * Applies the system blur-behind to the dialog / bottom-sheet window that
 * hosts this composable, so the whole app melts into a soft haze behind it
 * (Android 12+). Below Android 12 this is a no-op — the opaque frosted fills
 * from [glassDialogContainer] keep the background hidden instead.
 */
@SuppressLint("NewApi")
@Composable
fun DialogBlurBehind(radius: Dp = GlassBlur.dialogRadius) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val view = LocalView.current
        val radiusPx = (radius.value * view.resources.displayMetrics.density).roundToInt()
        SideEffect {
            var parent: ViewParent? = view.parent
            var window: Window? = null
            while (parent != null) {
                val current = parent
                if (current is DialogWindowProvider) {
                    window = current.window
                    break
                }
                parent = current.parent
            }
            val dialogWindow = window ?: return@SideEffect
            dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            val attrs = dialogWindow.attributes
            attrs.blurBehindRadius = radiusPx
            dialogWindow.attributes = attrs
        }
    }
}

/**
 * Container fill for dialog / bottom-sheet glass: translucent over the real
 * window blur on Android 12+, an opaque frosted slab below — either way the
 * background stays invisible behind the glass.
 */
@Composable
fun glassDialogContainer(): Color {
    val scheme = MaterialTheme.colorScheme
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        scheme.surfaceContainerHighest
    } else if (LocalIsDarkTheme.current) {
        AppPalette.darkGlassSlab
    } else {
        AppPalette.lightGlassSlab
    }
}
