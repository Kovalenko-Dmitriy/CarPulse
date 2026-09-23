package com.carpulse.obd.ui.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.carpulse.obd.data.prefs.ThemeMode
import java.util.Calendar

/**
 * Возвращает true, если сейчас ночь (с 20:00 до 7:00).
 */
fun isNightTime(): Boolean {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return hour >= 20 || hour < 7
}

/**
 * Реактивный трекер «ночь / день». Обновляется:
 *  - мгновенно при ручном изменении времени пользователем (ACTION_TIME_CHANGED);
 *  - при смене часового пояса или даты;
 *  - раз в минуту от системы (ACTION_TIME_TICK);
 *  - при возврате приложения на передний план (ON_RESUME) — на случай пропущенных событий.
 */
@Composable
private fun rememberNightTicker(): State<Boolean> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val night = remember { mutableStateOf(isNightTime()) }

    // 1) Системные broadcast'ы
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                night.value = isNightTime()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)          // каждую минуту
            addAction(Intent.ACTION_TIME_CHANGED)       // пользователь изменил время вручную
            addAction(Intent.ACTION_TIMEZONE_CHANGED)   // сменил часовой пояс
            addAction(Intent.ACTION_DATE_CHANGED)       // сменилась дата
        }
        // На Android 13+ для registerReceiver можно указать RECEIVER_NOT_EXPORTED
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    // 2) Дополнительная проверка при возврате приложения на передний план
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                night.value = isNightTime()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return night
}

/**
 * Определяет, должна ли сейчас использоваться тёмная тема.
 */
@Composable
fun shouldUseDarkTheme(mode: ThemeMode): Boolean {
    val systemDark = isSystemInDarkTheme()

    return when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.AUTO -> rememberNightTicker().value
    }
}

private val LightColors = lightColorScheme(
    primary = PulseRed,
    onPrimary = Color.White,
    primaryContainer = PulseRedLight,
    onPrimaryContainer = Color.White,
    secondary = PulseBlue,
    onSecondary = Color.White,
    tertiary = PulseAmber,
    background = LightBg,
    onBackground = LightOnBg,
    surface = LightSurface,
    onSurface = LightOnBg,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    error = PulseRedDark,
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = PulseRedLight,
    onPrimary = Color.Black,
    primaryContainer = PulseRedDark,
    onPrimaryContainer = Color.White,
    secondary = PulseBlueLight,
    onSecondary = Color.Black,
    tertiary = PulseAmber,
    background = DarkBg,
    onBackground = DarkOnBg,
    surface = DarkSurface,
    onSurface = DarkOnBg,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    error = PulseRedLight,
    onError = Color.Black
)

@Composable
fun CarPulseTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit
) {
    val dark = shouldUseDarkTheme(themeMode)

    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = CarPulseTypography,
        content = content
    )
}