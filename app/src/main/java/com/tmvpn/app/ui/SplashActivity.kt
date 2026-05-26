package com.tmvpn.app.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.seyit474.tmvpn.R
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
                    listOf(Color(0xFF080D14), Color(0xFF0D1B2A), Color(0xFF080D14))
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
            Image(
                painter = painterResource(R.drawable.logo_tmvpn),
                contentDescription = "TM VPN Logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(220.dp),
            )
        }
    }
}
