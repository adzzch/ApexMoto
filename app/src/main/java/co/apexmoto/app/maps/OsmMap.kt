package co.apexmoto.app.maps

import android.graphics.Color as AColor
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.math.abs

data class MapMarker(val id: String, val pos: LatLng, val title: String, val snippet: String = "")

/** Control del mapa desde fuera del composable (mover, ajustar a una ruta, seguir al usuario). */
class OsmMapState {
    internal var view: MapView? = null

    fun moveTo(p: LatLng, zoom: Double? = null) {
        val v = view ?: return
        zoom?.let { v.controller.setZoom(it) }
        v.controller.animateTo(GeoPoint(p.latitude, p.longitude))
    }

    /** Ajusta la cámara para que quepan todos los puntos. */
    fun fit(points: List<LatLng>) {
        val v = view ?: return
        if (points.isEmpty()) return
        val geo = points.map { GeoPoint(it.latitude, it.longitude) }
        val box = BoundingBox.fromGeoPoints(geo)
        if (geo.size < 2 || (abs(box.latitudeSpan) < 1e-4 && abs(box.longitudeSpan) < 1e-4)) moveTo(points.first(), 15.0)
        else v.zoomToBoundingBox(box, true, 120)
    }

    /** Modo guiado: centra en el usuario y gira el mapa para que el rumbo quede hacia arriba. */
    fun follow(p: LatLng, bearing: Float, zoom: Double = 17.0) {
        val v = view ?: return
        v.controller.setZoom(zoom)
        v.controller.animateTo(GeoPoint(p.latitude, p.longitude))
        v.mapOrientation = -bearing
    }

    fun resetRotation() { view?.mapOrientation = 0f }
}

/** Mapa OpenStreetMap (osmdroid). mapType: 1 = estándar, 3 = topográfico. */
@Composable
fun OsmMap(
    modifier: Modifier = Modifier,
    state: OsmMapState = remember { OsmMapState() },
    center: LatLng,
    zoom: Double = 12.0,
    mapType: Int = 1,
    markers: List<MapMarker> = emptyList(),
    route: List<LatLng> = emptyList(),
    myPos: LatLng? = null,
    onMarkerClick: ((String) -> Unit)? = null,
    onTap: ((LatLng) -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val mapView = remember {
        MapView(ctx).apply {
            setMultiTouchControls(true)
            // Dentro de pantallas con scroll, el mapa debe recibir los gestos de arrastre
            setOnTouchListener { view, ev ->
                when (ev.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_MOVE -> view.parent?.requestDisallowInterceptTouchEvent(true)
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> view.parent?.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            minZoomLevel = 3.0
            controller.setZoom(zoom)
            controller.setCenter(GeoPoint(center.latitude, center.longitude))
        }
    }
    DisposableEffect(owner, mapView) {
        state.view = mapView
        val obs = LifecycleEventObserver { _, e ->
            when (e) { Lifecycle.Event.ON_RESUME -> mapView.onResume(); Lifecycle.Event.ON_PAUSE -> mapView.onPause(); else -> {} }
        }
        owner.lifecycle.addObserver(obs)
        mapView.onResume()
        onDispose { owner.lifecycle.removeObserver(obs); mapView.onPause(); mapView.onDetach(); state.view = null }
    }
    AndroidView(modifier = modifier, factory = { mapView }, update = { v ->
        val ts = if (mapType == 3) TileSourceFactory.OpenTopo else TileSourceFactory.MAPNIK
        if (v.tileProvider.tileSource.name() != ts.name()) v.setTileSource(ts)
        val snapshot = listOf(markers, route, myPos, onTap != null)
        if (v.tag != snapshot) {
            v.tag = snapshot
            v.overlays.clear()
            // Crédito "© OpenStreetMap contributors": lo exigen la licencia ODbL y la política de uso de las teselas.
            v.overlays.add(org.osmdroid.views.overlay.CopyrightOverlay(v.context))
            onTap?.let { tap ->
                v.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint): Boolean { tap(LatLng(p.latitude, p.longitude)); return true }
                    override fun longPressHelper(p: GeoPoint) = false
                }))
            }
            if (route.size > 1) v.overlays.add(Polyline().apply {
                setPoints(route.map { GeoPoint(it.latitude, it.longitude) })
                outlinePaint.apply { color = AColor.parseColor("#D94600"); strokeWidth = 14f; strokeCap = Paint.Cap.ROUND }
            })
            markers.forEach { m ->
                v.overlays.add(Marker(v).apply {
                    position = GeoPoint(m.pos.latitude, m.pos.longitude)
                    title = m.title; snippet = m.snippet
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    setOnMarkerClickListener { mk, _ -> if (onMarkerClick != null) onMarkerClick(m.id) else mk.showInfoWindow(); true }
                })
            }
            myPos?.let { p ->
                v.overlays.add(Marker(v).apply {
                    position = GeoPoint(p.latitude, p.longitude)
                    icon = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(AColor.parseColor("#0E7490")); setStroke(6, AColor.WHITE); setSize(44, 44) }
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    setInfoWindow(null)
                })
            }
            v.invalidate()
        }
    })
}
