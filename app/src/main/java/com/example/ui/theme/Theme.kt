package com.example.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

val FixedGoogleDarkColorScheme: ColorScheme = darkColorScheme(
    primary = TraceDarkPrimary,
    onPrimary = TraceDarkOnPrimary,
    primaryContainer = TraceDarkPrimaryContainer,
    onPrimaryContainer = TraceDarkOnPrimaryContainer,
    secondary = TraceDarkSecondary,
    onSecondary = TraceDarkOnSecondary,
    secondaryContainer = TraceDarkSecondaryContainer,
    onSecondaryContainer = TraceDarkOnSecondaryContainer,
    background = TraceDarkBackground,
    onBackground = TraceDarkOnBackground,
    surface = TraceDarkSurface,
    onSurface = TraceDarkOnSurface,
    surfaceVariant = TraceDarkSurfaceVariant,
    onSurfaceVariant = TraceDarkOnSurfaceVariant,
    surfaceContainerLowest = TraceDarkSurfaceContainerLowest,
    surfaceContainerLow = TraceDarkSurfaceContainerLow,
    surfaceContainer = TraceDarkSurfaceContainer,
    surfaceContainerHigh = TraceDarkSurfaceContainerHigh,
    surfaceContainerHighest = TraceDarkSurfaceContainerHighest,
    outline = TraceDarkOutline,
    outlineVariant = TraceDarkOutlineVariant,
    error = TraceDarkError
)

val FixedGoogleLightColorScheme: ColorScheme = lightColorScheme(
    primary = TraceLightPrimary,
    onPrimary = TraceLightOnPrimary,
    primaryContainer = TraceLightPrimaryContainer,
    onPrimaryContainer = TraceLightOnPrimaryContainer,
    secondary = TraceLightSecondary,
    onSecondary = TraceLightOnSecondary,
    secondaryContainer = TraceLightSecondaryContainer,
    onSecondaryContainer = TraceLightOnSecondaryContainer,
    background = TraceLightBackground,
    onBackground = TraceLightOnBackground,
    surface = TraceLightSurface,
    onSurface = TraceLightOnSurface,
    surfaceVariant = TraceLightSurfaceVariant,
    onSurfaceVariant = TraceLightOnSurfaceVariant,
    surfaceContainerLowest = TraceLightSurfaceContainerLowest,
    surfaceContainerLow = TraceLightSurfaceContainerLow,
    surfaceContainer = TraceLightSurfaceContainer,
    surfaceContainerHigh = TraceLightSurfaceContainerHigh,
    surfaceContainerHighest = TraceLightSurfaceContainerHighest,
    outline = TraceLightOutline,
    outlineVariant = TraceLightOutlineVariant,
    error = TraceLightError
)

val TraceShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

fun isDynamicColorSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean {
    return sdkInt >= Build.VERSION_CODES.S
}

fun resolveTraceColorScheme(
    context: Context,
    darkTheme: Boolean,
    dynamicColor: Boolean,
    sdkInt: Int = Build.VERSION.SDK_INT
): ColorScheme {
    return if (dynamicColor && sdkInt >= Build.VERSION_CODES.S) {
        if (darkTheme) {
            val dynamicDark = dynamicDarkColorScheme(context)
            // Guarantee dark mode never uses pure black (#000000) as surface or background
            dynamicDark.copy(
                background = if (dynamicDark.background == Color.Black) TraceDarkBackground else dynamicDark.background,
                surface = if (dynamicDark.surface == Color.Black) TraceDarkSurface else dynamicDark.surface,
                surfaceContainerLow = if (dynamicDark.surfaceContainerLow == Color.Black) TraceDarkSurfaceContainerLow else dynamicDark.surfaceContainerLow,
                surfaceContainer = if (dynamicDark.surfaceContainer == Color.Black) TraceDarkSurfaceContainer else dynamicDark.surfaceContainer,
                surfaceContainerHigh = if (dynamicDark.surfaceContainerHigh == Color.Black) TraceDarkSurfaceContainerHigh else dynamicDark.surfaceContainerHigh,
                surfaceContainerHighest = if (dynamicDark.surfaceContainerHighest == Color.Black) TraceDarkSurfaceContainerHighest else dynamicDark.surfaceContainerHighest
            )
        } else {
            dynamicLightColorScheme(context)
        }
    } else {
        if (darkTheme) {
            FixedGoogleDarkColorScheme
        } else {
            FixedGoogleLightColorScheme
        }
    }
}

@Composable
fun TraceTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = resolveTraceColorScheme(
        context = context,
        darkTheme = darkTheme,
        dynamicColor = dynamicColor
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = TraceShapes,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    TraceTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
}
