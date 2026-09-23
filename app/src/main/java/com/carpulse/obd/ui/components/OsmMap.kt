package com.carpulse.obd.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * Обёртка osmdroid MapView в Compose.
 *
 * osmdroid Configuration инициализируется ОДИН РАЗ в CarPulseApp.onCreate():
 *  - userAgentValue
 *  - osmdroidBasePath / osmdroidTileCache (внутренняя папка приложения)
 *  - cacheMapTileCount / tileDownloadThreads / expirationOverrideDuration
 *
 * Повторный Configuration.getInstance().load() здесь НЕ вызываем:
 * он сбрасывает всё перечисленное на дефолтные значения, из-за чего
 * на Android 10+ тайлы не могут записаться в /sdcard/osmdroid/
 * и карта остаётся серой сеткой.
 *
 * @param points      список координат трека (lat, lon) — порядок важен
 * @param strokeColor цвет линии трека (ARGB)
 * @param strokeWidth толщина линии в пикселях
 * @param showMarkers показывать ли старт/финиш маркеры
 */
@Composable
fun OsmMap(
    points: List<Pair<Double, Double>>,
    modifier: Modifier = Modifier,
    strokeColor: Int = 0xFF1E88E5.toInt(),
    strokeWidth: Float = 12f,
    showMarkers: Boolean = true,
) {
    val ctx = LocalContext.current

    // Единственный экземпляр MapView на весь жизненный цикл композиции.
    val mapView = remember {
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)   // OSM стандартный
            setMultiTouchControls(true)
            controller.setZoom(15.0)
            setUseDataConnection(true)
        }
    }

    // osmdroid требует onResume/onPause; onDetach освобождает ресурсы.
    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    // Обновляем оверлеи при изменении points.
    LaunchedEffect(points) {
        mapView.overlays.clear()

        if (points.size > 1) {
            val geoPoints = points.map { GeoPoint(it.first, it.second) }

            // Линия трека
            val polyline = Polyline().apply {
                setPoints(geoPoints)
                outlinePaint.color = strokeColor
                outlinePaint.strokeWidth = strokeWidth
                outlinePaint.isAntiAlias = true
            }
            mapView.overlays.add(polyline)

            // Маркеры старта и финиша
            if (showMarkers) {
                val startMarker = Marker(mapView).apply {
                    position = geoPoints.first()
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "Старт"
                    icon = makeCircleDrawable(ctx, 0xFF2E7D32.toInt())
                }
                val endMarker = Marker(mapView).apply {
                    position = geoPoints.last()
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "Финиш"
                    icon = makeCircleDrawable(ctx, 0xFFC62828.toInt())
                }
                mapView.overlays.add(startMarker)
                mapView.overlays.add(endMarker)
            }

            // Центрируем камеру на весь маршрут
            mapView.post {
                try {
                    mapView.zoomToBoundingBox(
                        org.osmdroid.util.BoundingBox.fromGeoPoints(geoPoints),
                        true,
                        100   // padding в пикселях
                    )
                } catch (e: Exception) {
                    mapView.controller.setCenter(geoPoints.first())
                }
            }
        } else if (points.size == 1) {
            // Одна точка — просто центрируем
            mapView.controller.setCenter(GeoPoint(points[0].first, points[0].second))
        }

        mapView.invalidate()
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier.fillMaxSize()
    )
}

/** Рисуем простой цветной круг для маркеров старт/финиш. */
private fun makeCircleDrawable(ctx: Context, color: Int): Drawable {
    val size = 40
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        this.color = color
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2f, paint)

    // Белая обводка
    val strokePaint = android.graphics.Paint().apply {
        isAntiAlias = true
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 4f
        this.color = android.graphics.Color.WHITE
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4f, strokePaint)

    return BitmapDrawable(ctx.resources, bmp)
}