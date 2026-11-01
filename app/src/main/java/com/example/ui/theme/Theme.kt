package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val BanglaStudioDarkScheme = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = OnEmeraldPrimary,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = OnEmeraldContainer,
    secondary = CyanSecondary,
    onSecondary = OnCyanSecondary,
    secondaryContainer = CyanContainer,
    onSecondaryContainer = OnCyanContainer,
    tertiary = AmberTertiary,
    onTertiary = OnAmberTertiary,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = OnAmberContainer,
    background = StudioBackground,
    onBackground = OnStudioBackground,
    surface = StudioSurface,
    onSurface = OnStudioSurface,
    surfaceVariant = StudioSurfaceVariant,
    onSurfaceVariant = OnStudioSurfaceVariant,
    outline = StudioOutline,
    error = ErrorCoral,
    onError = OnErrorCoral,
    errorContainer = ErrorCoralContainer,
    onErrorContainer = OnErrorCoralContainer
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = BanglaStudioDarkScheme,
        typography = Typography,
        content = content
    )
}
