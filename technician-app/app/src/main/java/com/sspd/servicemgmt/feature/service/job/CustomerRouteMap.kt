package com.sspd.servicemgmt.feature.service.job

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.sspd.servicemgmt.core.tracking.LocationClient
import com.sspd.servicemgmt.core.tracking.LocationFix
import com.sspd.servicemgmt.core.tracking.LocationPermission
import com.sspd.servicemgmt.core.ui.theme.BorderColor
import com.sspd.servicemgmt.core.ui.theme.CardBg
import com.sspd.servicemgmt.core.ui.theme.Danger
import com.sspd.servicemgmt.core.ui.theme.TextMuted
import com.sspd.servicemgmt.core.ui.theme.Violet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.util.concurrent.TimeUnit

private data class RouteResult(
    val points: List<GeoPoint>,
    val distanceMeters: Double,
    val durationSeconds: Double
)

private val routeClient = OkHttpClient.Builder()
    .connectTimeout(12, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .build()

@Composable
fun CustomerRouteMap(
    customerName: String,
    destinationLatitude: Double?,
    destinationLongitude: Double?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentFix by remember { mutableStateOf<LocationFix?>(null) }
    var route by remember { mutableStateOf<RouteResult?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var routeLoading by remember { mutableStateOf(false) }

    fun loadCurrentLocation() {
        if (!LocationPermission.granted(context)) return
        scope.launch {
            routeLoading = true
            runCatching { LocationClient(context).current() }
                .onSuccess {
                    currentFix = it
                    message = null
                }
                .onFailure { message = it.message ?: "လက်ရှိ GPS မရပါ" }
            routeLoading = false
        }
    }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val allowed = granted[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            granted[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (allowed) loadCurrentLocation() else message = "Location permission လိုအပ်ပါသည်"
    }

    LaunchedEffect(destinationLatitude, destinationLongitude) {
        if (destinationLatitude != null && destinationLongitude != null &&
            LocationPermission.granted(context)
        ) loadCurrentLocation()
    }

    LaunchedEffect(currentFix, destinationLatitude, destinationLongitude) {
        val fix = currentFix ?: return@LaunchedEffect
        val destLat = destinationLatitude ?: return@LaunchedEffect
        val destLng = destinationLongitude ?: return@LaunchedEffect
        routeLoading = true
        runCatching { fetchRoute(fix.latitude, fix.longitude, destLat, destLng) }
            .onSuccess {
                route = it
                message = null
            }
            .onFailure {
                route = null
                message = "လမ်းကြောင်း server မရသေးပါ။ Customer marker ကို ဆက်ကြည့်နိုင်သည်။"
            }
        routeLoading = false
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("သွားရမည့် Customer GPS Map", color = Violet)
            if (destinationLatitude == null || destinationLongitude == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.LocationOff, null, tint = Danger)
                    Text(
                        "$customerName အတွက် GPS location မသတ်မှတ်ရသေးပါ",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
                return@Column
            }

            NativeRouteMap(
                destination = GeoPoint(destinationLatitude, destinationLongitude),
                customerName = customerName,
                current = currentFix?.let { GeoPoint(it.latitude, it.longitude) },
                routePoints = route?.points.orEmpty()
            )

            route?.let {
                Text(
                    "လမ်းအကွာအဝေး ${"%.1f".format(it.distanceMeters / 1000)} km · " +
                        "ခန့်မှန်း ${kotlin.math.ceil(it.durationSeconds / 60).toInt()} မိနစ်",
                    color = Violet,
                    fontSize = 12.sp
                )
            }
            if (routeLoading) Text("GPS/လမ်းကြောင်း ရယူနေသည်…", color = TextMuted, fontSize = 12.sp)
            message?.let { Text(it, color = Danger, fontSize = 12.sp) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (LocationPermission.granted(context)) loadCurrentLocation()
                        else locationPermission.launch(LocationPermission.required)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.MyLocation, null)
                    Text("Route ပြမည်", fontSize = 12.sp)
                }
                Button(
                    onClick = {
                        val uri = Uri.parse(
                            "https://www.google.com/maps/dir/?api=1&destination=" +
                                "$destinationLatitude,$destinationLongitude"
                        )
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.Map, null)
                    Text("လမ်းညွှန်", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun NativeRouteMap(
    destination: GeoPoint,
    customerName: String,
    current: GeoPoint?,
    routePoints: List<GeoPoint>
) {
    val context = LocalContext.current
    val map = remember {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidBasePath = File(context.cacheDir, "osmdroid")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
        }
        MapView(context).apply {
            setMultiTouchControls(true)
            setTileSource(esriStreetTiles())
            controller.setZoom(16.0)
            controller.setCenter(destination)
        }
    }

    DisposableEffect(map) {
        map.onResume()
        onDispose {
            map.onPause()
            map.onDetach()
        }
    }

    AndroidView(
        factory = { map },
        modifier = Modifier.fillMaxWidth().height(300.dp),
        update = {
            it.overlays.clear()
            Marker(it).apply {
                position = destination
                title = customerName
                snippet = "Customer GPS"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                it.overlays.add(this)
            }
            current?.let { point ->
                Marker(it).apply {
                    position = point
                    title = "လက်ရှိနေရာ"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    it.overlays.add(this)
                }
            }
            if (routePoints.size > 1) {
                it.overlays.add(0, Polyline().apply {
                    outlinePaint.color = Color.rgb(79, 70, 229)
                    outlinePaint.strokeWidth = 10f
                    setPoints(routePoints)
                })
            }
            val visible = buildList {
                add(destination)
                current?.let(::add)
                addAll(routePoints)
            }
            if (visible.size > 1) {
                it.post {
                    it.zoomToBoundingBox(BoundingBox.fromGeoPoints(visible), true, 70)
                }
            } else {
                it.controller.setCenter(destination)
                it.controller.setZoom(16.0)
            }
            it.invalidate()
        }
    )
}

private fun esriStreetTiles() = object : OnlineTileSourceBase(
    "Esri World Street Map",
    0,
    19,
    256,
    "",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/"),
    "Tiles © Esri"
) {
    override fun getTileURLString(index: Long): String =
        baseUrl +
            MapTileIndex.getZoom(index) + "/" +
            MapTileIndex.getY(index) + "/" +
            MapTileIndex.getX(index)
}

private suspend fun fetchRoute(
    fromLatitude: Double,
    fromLongitude: Double,
    toLatitude: Double,
    toLongitude: Double
): RouteResult = withContext(Dispatchers.IO) {
    val url = "https://router.project-osrm.org/route/v1/driving/" +
        "$fromLongitude,$fromLatitude;$toLongitude,$toLatitude" +
        "?overview=full&geometries=geojson&steps=false"
    routeClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
        if (!response.isSuccessful) error("OSRM ${response.code}")
        val json = JSONObject(response.body?.string().orEmpty())
        val route = json.getJSONArray("routes").getJSONObject(0)
        val coordinates = route.getJSONObject("geometry").getJSONArray("coordinates")
        val points = buildList {
            for (index in 0 until coordinates.length()) {
                val coordinate = coordinates.getJSONArray(index)
                add(GeoPoint(coordinate.getDouble(1), coordinate.getDouble(0)))
            }
        }
        RouteResult(points, route.getDouble("distance"), route.getDouble("duration"))
    }
}
