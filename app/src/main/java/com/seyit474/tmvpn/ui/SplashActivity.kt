package com.seyit474.tmvpn.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import kotlinx.coroutines.delay

@SuppressLint("CustomSplashScreen")
class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SplashScreen {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }
    }
}

@Composable
private fun SplashScreen(onFinished: () -> Unit) {
    var triggered by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue   = if (triggered) 1f else 0.55f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 260f),
        label         = "scale",
    )
    val alpha by animateFloatAsState(
        targetValue   = if (triggered) 1f else 0f,
        animationSpec = tween(500),
        label         = "alpha",
    )

    LaunchedEffect(Unit) {
        triggered = true
        delay(2_400)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0A1628), Color(0xFF0D2448), Color(0xFF070B13))
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .scale(scale)
                .alpha(alpha),
        ) {
            // ── Logo card ──────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF1565C0), Color(0xFF0D47A1))
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // Glow behind shield
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color  = Color(0xFF4A90FF),
                        radius = 55.dp.toPx(),
                        center = Offset(size.width / 2, size.height / 2),
                        alpha  = 0.18f,
                    )
                }

                // Shield + TM + Lock (layered)
                Box(modifier = Modifier.size(110.dp), contentAlignment = Alignment.Center) {
                    // Shield drawn with Canvas
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height

                        // Shield fill gradient
                        val shieldPath = shieldPath(w, h)
                        drawPath(
                            path  = shieldPath,
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFF5BA3FF), Color(0xFF1565C0)),
                                start  = Offset(0f, 0f),
                                end    = Offset(w, h),
                            ),
                        )
                        // Shield stroke
                        drawPath(
                            path  = shieldPath,
                            color = Color(0xFF90CAF9),
                            style = Stroke(width = 2.5.dp.toPx()),
                        )
                    }

                    // "TM" text + lock icon stacked
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.offset(y = (-4).dp),
                    ) {
                        Text(
                            "TM",
                            color      = Color.White,
                            fontSize   = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        // Lock icon drawn inline
                        Canvas(modifier = Modifier.size(22.dp, 20.dp)) {
                            val w  = size.width
                            val h  = size.height
                            val cx = w / 2
                            // Lock shackle (arc)
                            drawArc(
                                color      = Color.White,
                                startAngle = 180f,
                                sweepAngle = 180f,
                                useCenter  = false,
                                topLeft    = Offset(cx - w * 0.3f, 0f),
                                size       = androidx.compose.ui.geometry.Size(w * 0.6f, h * 0.55f),
                                style      = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                            )
                            // Lock body
                            val bodyTop = h * 0.4f
                            drawRoundRect(
                                color        = Color.White,
                                topLeft      = Offset(w * 0.1f, bodyTop),
                                size         = androidx.compose.ui.geometry.Size(w * 0.8f, h * 0.55f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                            )
                            // Keyhole
                            drawCircle(
                                color  = Color(0xFF1565C0),
                                radius = 2.2.dp.toPx(),
                                center = Offset(cx, bodyTop + h * 0.25f),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // App title
            Text(
                "TMVPN",
                color         = Color.White,
                fontSize      = 36.sp,
                fontWeight    = FontWeight.Bold,
                letterSpacing = 2.sp,
            )

            Spacer(Modifier.height(8.dp))

            // Developer line
            Text(
                "Geliştirici TM OGUZ",
                color    = Color(0xFF6B8EC4),
                fontSize = 14.sp,
            )
        }
    }
}

// Shield path helper — returns a proper shield polygon
private fun shieldPath(w: Float, h: Float): Path = Path().apply {
    moveTo(w * 0.50f, h * 0.04f)          // top-center
    lineTo(w * 0.92f, h * 0.16f)          // top-right shoulder
    lineTo(w * 0.92f, h * 0.52f)          // right side mid
    cubicTo(                               // right curve into bottom point
        w * 0.92f, h * 0.74f,
        w * 0.50f, h * 0.96f,
        w * 0.50f, h * 0.96f,
    )
    cubicTo(                               // left curve from bottom point
        w * 0.50f, h * 0.96f,
        w * 0.08f, h * 0.74f,
        w * 0.08f, h * 0.52f,
    )
    lineTo(w * 0.08f, h * 0.16f)          // left side
    close()
}
