package com.jagajaga.calendarmap

import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import androidx.compose.runtime.rememberUpdatedState

/** A request to move the camera; [id] makes repeated requests to the same spot distinct. */
data class CameraRequest(
    val id: Int,
    val center: LatLon? = null,
    val fitRadiusKm: Int? = null,
    val fitPoints: List<LatLon>? = null,
    val zoom: Double? = null,
)

private class MapHolder {
    var lastCameraId = -1
    var lastOverlayKey: Any? = null
    var onPlaceClick: (Place) -> Unit = {}
}

@Composable
fun EventMap(
    places: List<Place>,
    myLocation: LatLon?,
    /** Draw this radius circle around [myLocation]; null = no circle. */
    radiusKm: Int?,
    selectedKeys: Set<String>,
    route: RouteResult?,
    camera: CameraRequest,
    onPlaceClick: (Place) -> Unit,
    onViewportChanged: (Bounds) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val holder = remember { MapHolder() }
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 3.0
            controller.setZoom(4.0)
            controller.setCenter(GeoPoint(48.0, 10.0))
        }
    }
    val viewportCallback = rememberUpdatedState(onViewportChanged)
    DisposableEffect(mapView) {
        fun report() {
            val b = mapView.boundingBox
            viewportCallback.value(Bounds(b.latNorth, b.latSouth, b.lonEast, b.lonWest))
        }
        // Debounced so panning doesn't rebuild pins on every frame.
        val listener = DelayedMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean { report(); return false }
            override fun onZoom(event: ZoomEvent?): Boolean { report(); return false }
        }, 250)
        mapView.addMapListener(listener)
        mapView.addOnFirstLayoutListener { _, _, _, _, _ -> report() }
        onDispose { mapView.removeMapListener(listener) }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier.clipToBounds(),
        update = { map ->
            val overlayKey = listOf(places, myLocation, radiusKm, selectedKeys, route)
            if (overlayKey != holder.lastOverlayKey) {
                holder.lastOverlayKey = overlayKey
                rebuildOverlays(map, places, myLocation, radiusKm, selectedKeys, route) { holder.onPlaceClick(it) }
            }
            holder.onPlaceClick = onPlaceClick
            if (camera.id != holder.lastCameraId) {
                holder.lastCameraId = camera.id
                moveCamera(map, camera, myLocation)
            }
        },
    )
}

private fun rebuildOverlays(
    map: MapView,
    places: List<Place>,
    me: LatLon?,
    radiusKm: Int?,
    selected: Set<String>,
    route: RouteResult?,
    onPlaceClick: (Place) -> Unit,
) {
    map.overlays.clear()
    if (me != null && radiusKm != null) {
        val circle = Polygon(map).apply {
            points = Polygon.pointsAsCircle(GeoPoint(me.lat, me.lon), radiusKm * 1000.0)
            fillPaint.color = Color.argb(28, 26, 115, 232)
            outlinePaint.color = Color.argb(160, 26, 115, 232)
            outlinePaint.strokeWidth = 3f
            setOnClickListener { _, _, _ -> false }
            infoWindow = null
        }
        map.overlays.add(circle)
    }
    if (route != null && route.path.size >= 2) {
        map.overlays.add(Polyline(map).apply {
            setPoints(route.path.map { GeoPoint(it.lat, it.lon) })
            outlinePaint.color = Color.argb(220, 26, 115, 232)
            outlinePaint.strokeWidth = 10f
            outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
            outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
            if (route.estimated) outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(24f, 16f), 0f)
            infoWindow = null
            setOnClickListener { _, _, _ -> false }
        })
    }
    if (me != null) {
        map.overlays.add(Marker(map).apply {
            position = GeoPoint(me.lat, me.lon)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = MyLocationDot.create(map.context)
            title = "You are here"
            setOnMarkerClickListener { _, _ -> true }
        })
    }
    val order = route?.orderByKey.orEmpty()
    // Draw later events first so the soonest ones end up on top; picked ones above all.
    val isPicked = { p: Place -> p.events.any { it.key in selected || it.key in order } }
    places.sortedWith(compareBy<Place> { isPicked(it) }.thenByDescending { it.events.first().begin }).forEach { place ->
        map.overlays.add(Marker(map).apply {
            position = GeoPoint(place.point.lat, place.point.lon)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = PinIcons.get(
                map.context, PinIcons.linesFor(place, selected, order),
                place.events.first().color, highlighted = isPicked(place),
            )
            setOnMarkerClickListener { _, _ ->
                onPlaceClick(place)
                true
            }
        })
    }
    map.overlays.add(CopyrightOverlay(map.context))
    map.invalidate()
}

private fun moveCamera(map: MapView, req: CameraRequest, me: LatLon?) {
    val action = {
        val center = req.center ?: me
        when {
            !req.fitPoints.isNullOrEmpty() -> {
                val pts = req.fitPoints.map { GeoPoint(it.lat, it.lon) }
                if (pts.size == 1) {
                    map.controller.setZoom(15.0)
                    map.controller.setCenter(pts[0])
                } else {
                    map.zoomToBoundingBox(BoundingBox.fromGeoPointsSafe(pts).increaseByScale(1.3f), false, 64)
                }
            }
            req.zoom != null && center != null -> {
                map.controller.setZoom(req.zoom)
                map.controller.setCenter(GeoPoint(center.lat, center.lon))
            }
            req.fitRadiusKm != null && center != null -> {
                val r = req.fitRadiusKm * 1000.0
                val pts = Polygon.pointsAsCircle(GeoPoint(center.lat, center.lon), r)
                map.zoomToBoundingBox(BoundingBox.fromGeoPointsSafe(pts), false, 48)
            }
            center != null -> {
                map.controller.setZoom(maxOf(map.zoomLevelDouble, 14.0))
                map.controller.animateTo(GeoPoint(center.lat, center.lon))
            }
        }
    }
    if (map.width > 0 && map.height > 0) action() else map.addOnFirstLayoutListener { _, _, _, _, _ -> action() }
}
