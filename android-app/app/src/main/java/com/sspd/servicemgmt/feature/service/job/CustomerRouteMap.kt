package com.sspd.servicemgmt.feature.service.job

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.DashPathEffect
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sspd.servicemgmt.BuildConfig
import com.sspd.servicemgmt.core.network.ApiClient
import com.sspd.servicemgmt.core.network.LocationPingDTO
import com.sspd.servicemgmt.core.network.TechnicianVisitDTO
import com.sspd.servicemgmt.core.network.VisitEventDTO
import com.sspd.servicemgmt.core.tracking.LocationClient
import com.sspd.servicemgmt.core.tracking.LocationPermission
import com.sspd.servicemgmt.core.util.PreferenceManager
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val MAP_HEIGHT_DP = 400
private const val TILE_CACHE_MAX_BYTES = 50L * 1024 * 1024
private const val TILE_CACHE_TRIM_BYTES = 40L * 1024 * 1024

private val mapLocationPermissions = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
)

private val streetTiles = XYTileSource(
    "CartoVoyagerKeyed",
    1,
    19,
    256,
    ".png?key=cb1_2u3y_1_b3d4615afe0884a3e637745e",
    arrayOf(
        "https://a.basemaps.cartocdn.com/rastertiles/voyager/",
        "https://b.basemaps.cartocdn.com/rastertiles/voyager/",
        "https://c.basemaps.cartocdn.com/rastertiles/voyager/"
    ),
    "© OpenStreetMap contributors © CARTO"
)

private val osmLock = Any()
@Volatile
private var osmReady = false

private fun ensureOsmConfigured(context: Context): Boolean {
    if (osmReady) return true
    return runCatching {
        synchronized(osmLock) {
            if (osmReady) return@runCatching true
            val app = context.applicationContext
            val base = File(app.cacheDir, "osmdroid").apply { mkdirs() }
            Configuration.getInstance().apply {
                userAgentValue = "SSPD-Manager/${BuildConfig.VERSION_NAME} (${app.packageName})"
                osmdroidBasePath = base
                osmdroidTileCache = File(base, "tiles").apply { mkdirs() }
                tileFileSystemCacheMaxBytes = TILE_CACHE_MAX_BYTES
                tileFileSystemCacheTrimBytes = TILE_CACHE_TRIM_BYTES
                load(app, app.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            }
            osmReady = true
        }
        true
    }.getOrDefault(false)
}

private fun validPoint(latitude: Double?, longitude: Double?): GeoPoint? {
    if (latitude == null || longitude == null) return null
    if (latitude == 0.0 && longitude == 0.0) return null
    if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
    return GeoPoint(latitude, longitude)
}

private fun validPings(rows: List<LocationPingDTO>): List<LocationPingDTO> = rows.filter {
    validPoint(it.latitude, it.longitude) != null && (it.accuracy == null || it.accuracy <= 100.0)
}

private fun haversineMeters(a: GeoPoint, b: GeoPoint): Double {
    val earthRadius = 6_371_000.0
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLng = Math.toRadians(b.longitude - a.longitude)
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val h = sin(dLat / 2) * sin(dLat / 2) +
        cos(lat1) * cos(lat2) * sin(dLng / 2) * sin(dLng / 2)
    return earthRadius * 2 * atan2(sqrt(h), sqrt(1 - h))
}

private fun formatDistance(meters: Double): String = when {
    meters < 1000 -> "${meters.toInt()} m"
    else -> String.format("%.1f km", meters / 1000)
}

private suspend fun fetchMyLocation(context: Context): GeoPoint? = runCatching {
    val fix = LocationClient(context).current()
    if (fix.accuracy != null && fix.accuracy > 100) return@runCatching null
    GeoPoint(fix.latitude, fix.longitude)
}.getOrNull()

@Composable
fun CustomerRouteMap(
    customerName: String,
    destinationLatitude: Double?,
    destinationLongitude: Double?,
    visit: TechnicianVisitDTO? = null,
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager(context) }
    val scope = rememberCoroutineScope()
    val destination = validPoint(destinationLatitude, destinationLongitude)
    var pings by remember(visit?.id) { mutableStateOf<List<LocationPingDTO>>(emptyList()) }
    var routeLoading by remember(visit?.id) { mutableStateOf(visit?.id != null) }
    var routeInfo by remember(visit?.id) { mutableStateOf<String?>(null) }
    var showFullScreen by remember { mutableStateOf(false) }
    var myLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var locationNote by remember { mutableStateOf<String?>(null) }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val ok = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!ok) {
            locationNote = "တည်နေရာ ခွင့်ပြုချက် မပေးထားပါ"
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            myLocation = fetchMyLocation(context)
            if (myLocation == null) locationNote = "GPS တည်နေရာ မရပါ"
        }
    }

    suspend fun loadRoute() {
        val visitId = visit?.id
        if (visitId == null) {
            routeLoading = false
            pings = emptyList()
            return
        }
        routeLoading = true
        routeInfo = null
        try {
            val response = ApiClient.service.getMyVisitPings(
                ApiClient.bearer(prefs.authToken), visitId
            )
            if (response.isSuccessful) {
                pings = validPings(response.body()?.data.orEmpty())
                if (pings.isEmpty() && destination == null) {
                    routeInfo = "သွားခဲ့သော GPS လမ်းကြောင်း မရှိသေးပါ"
                }
            } else {
                routeInfo = response.body()?.message ?: "GPS လမ်းကြောင်း ဖတ်မရပါ (${response.code()})"
            }
        } catch (_: Exception) {
            routeInfo = "Internet/Server ချိတ်ဆက်မှုမရှိပါ"
        } finally {
            routeLoading = false
        }
    }

    LaunchedEffect(visit?.id) { loadRoute() }

    LaunchedEffect(destination) {
        if (destination == null) return@LaunchedEffect
        if (LocationPermission.granted(context)) {
            myLocation = fetchMyLocation(context)
            if (myLocation == null) locationNote = "GPS တည်နေရာ မရပါ"
        }
    }

    val events = visit?.events.orEmpty().filter {
        validPoint(it.latitude, it.longitude) != null
    }
    val visitCurrent = validPoint(visit?.latitude, visit?.longitude)
    val technicianPoint = myLocation ?: visitCurrent
    val canShowMap = destination != null || pings.isNotEmpty() || events.isNotEmpty() || technicianPoint != null
    val distanceText = remember(technicianPoint, destination) {
        if (technicianPoint != null && destination != null) {
            "Customer ဆီသို့ ခန့်မှန်း ${formatDistance(haversineMeters(technicianPoint, destination))}"
        } else null
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Customer နေရာ — $customerName", style = MaterialTheme.typography.titleSmall)
            distanceText?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            when {
                !canShowMap ->
                    Text("Customer GPS နှင့် Route history မရှိသေးပါ")
                else -> Box(
                    Modifier
                        .fillMaxWidth()
                        .height(MAP_HEIGHT_DP.dp)
                ) {
                    RouteMapView(
                        destination = destination,
                        customerName = customerName,
                        pings = pings,
                        events = events,
                        technicianPoint = technicianPoint,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (routeLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                        )
                    }
                }
            }

            locationNote?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (!routeLoading) routeInfo?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!routeLoading && pings.isNotEmpty()) {
                Text(
                    "GPS points ${pings.size} ခု · Accuracy 100m အောက် data များ",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showFullScreen = true },
                    enabled = canShowMap
                ) { Text("မြေပုံအပြည့်ကြည့်ရန်") }
                if (!LocationPermission.granted(context) && destination != null) {
                    OutlinedButton(onClick = { locationPermission.launch(mapLocationPermissions) }) {
                        Text("ကျွန်တော် တည်နေရာ")
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { scope.launch { loadRoute() } },
                    enabled = !routeLoading && visit?.id != null
                ) { Text("ပြန်ဖတ်မည်") }
                Button(
                    onClick = { destination?.let { openNavigation(context, it) } },
                    enabled = destination != null
                ) { Text("Google Maps လမ်းညွှန်") }
            }
        }
    }

    if (showFullScreen) {
        Dialog(
            onDismissRequest = { showFullScreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Customer နေရာ", style = MaterialTheme.typography.titleMedium)
                            Text(customerName, style = MaterialTheme.typography.bodySmall)
                            distanceText?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        OutlinedButton(onClick = { showFullScreen = false }) { Text("ပိတ်မည်") }
                    }
                    HorizontalDivider()
                    RouteMapView(
                        destination = destination,
                        customerName = customerName,
                        pings = pings,
                        events = events,
                        technicianPoint = technicianPoint,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                    routeInfo?.let {
                        Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteMapView(
    destination: GeoPoint?,
    customerName: String,
    pings: List<LocationPingDTO>,
    events: List<VisitEventDTO>,
    technicianPoint: GeoPoint?,
    modifier: Modifier
) {
    val context = LocalContext.current
    val mapView = remember {
        runCatching {
            if (!ensureOsmConfigured(context)) error("osmdroid")
            MapView(context).apply {
                setTileSource(streetTiles)
                setMultiTouchControls(true)
                isTilesScaledToDpi = true
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        }.getOrNull()
    }
    if (mapView == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("မြေပုံ မဖွင့်နိုင်", style = MaterialTheme.typography.bodySmall)
        }
        return
    }
    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { map ->
            map.overlays.clear()
            val routePoints = pings.mapNotNull { validPoint(it.latitude, it.longitude) }
            if (routePoints.size >= 2) {
                map.overlays.add(Polyline(map).apply {
                    setPoints(routePoints)
                    outlinePaint.color = AndroidColor.rgb(37, 99, 235)
                    outlinePaint.strokeWidth = 8f
                })
            }
            if (technicianPoint != null && destination != null) {
                map.overlays.add(Polyline(map).apply {
                    setPoints(listOf(technicianPoint, destination))
                    outlinePaint.color = AndroidColor.rgb(34, 197, 94)
                    outlinePaint.strokeWidth = 6f
                    outlinePaint.pathEffect = DashPathEffect(floatArrayOf(24f, 16f), 0f)
                })
            }
            fun marker(point: GeoPoint?, title: String, snippet: String? = null) {
                if (point == null) return
                map.overlays.add(Marker(map).apply {
                    position = point
                    this.title = title
                    this.snippet = snippet
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                })
            }
            marker(destination, "Customer", customerName)
            marker(technicianPoint, "ကျွန်တော်")
            routePoints.firstOrNull()?.let { marker(it, "Start", pings.firstOrNull()?.recordedAt) }
            if (routePoints.size >= 2) {
                routePoints.lastOrNull()?.let { marker(it, "Latest GPS", pings.lastOrNull()?.recordedAt) }
            }
            events.forEach { event ->
                marker(
                    validPoint(event.latitude, event.longitude),
                    event.eventType ?: "Visit Event",
                    event.occurredAt
                )
            }

            val allPoints = buildList {
                destination?.let(::add)
                technicianPoint?.let(::add)
                addAll(routePoints)
                events.mapNotNullTo(this) { validPoint(it.latitude, it.longitude) }
            }.distinctBy { "${it.latitude},${it.longitude}" }
            map.post {
                if (allPoints.size > 1) {
                    map.zoomToBoundingBox(BoundingBox.fromGeoPoints(allPoints), true, 80)
                } else if (allPoints.size == 1) {
                    map.controller.setZoom(16.0)
                    map.controller.setCenter(allPoints.first())
                }
                map.invalidate()
            }
        }
    )
}

private fun openNavigation(context: Context, destination: GeoPoint) {
    val appIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("google.navigation:q=${destination.latitude},${destination.longitude}")
    ).apply { setPackage("com.google.android.apps.maps") }
    if (appIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(appIntent)
    } else {
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(
                    "https://www.google.com/maps/dir/?api=1&destination=${destination.latitude},${destination.longitude}"
                )
            )
        )
    }
}
