package com.cx330.toolbox.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.cx330.toolbox.R
import com.cx330.toolbox.ui.theme.JournalColors

/**
 * 开屏页：整屏照片全覆盖（用户要求），不留奶油底、不放文案。
 * centerCrop 保证任何屏幕比例都铺满；进入时轻微淡入 + 缩放。
 */
@Composable
fun SplashScreen() {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val alpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 550),
        label = "splash-alpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (appeared) 1f else 1.04f,
        animationSpec = tween(durationMillis = 900),
        label = "splash-scale",
    )

    Image(
        painter = painterResource(R.drawable.splash_photo),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .background(JournalColors.cream)
            .alpha(alpha)
            .scale(scale),
    )
}
