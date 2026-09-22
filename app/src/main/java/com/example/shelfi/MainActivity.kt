package com.example.shelfi

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shelfi.ui.theme.ShelfiTheme
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

private data class Product(
    val name: String,
    val aisle: String,
    val distance: String,
    val color: Color,
    val location: Offset,
    // Later, put the matching PNG/JPG in app/src/main/res/drawable/ and set its name here.
    val imageAsset: String? = null
)

private data class ClassroomPosition(
    val id: String,
    val label: String,
    val location: Offset
)

private enum class AppTab {
    MAP, PRODUCTS, PROFILE
}

private val classroomPositions = listOf(
    ClassroomPosition("entrance", "Eingang", Offset(.12f, .84f)),
    ClassroomPosition("desk", "Pult", Offset(.16f, .22f)),
    ClassroomPosition("window", "Fenster", Offset(.84f, .22f)),
    ClassroomPosition("back", "Rückseite", Offset(.84f, .84f))
)

private val products = listOf(
    Product("Milch", "Tisch 6", "42 m", Color(0xFF4D9DE0), Offset(.70f, .73f), imageAsset = "milch"),
    Product("Brot", "Tisch 2", "28 m", Color(0xFFE8A23A), Offset(.30f, .40f)),
    Product("Äpfel", "Tisch 1", "18 m", Color(0xFFE56B6F), Offset(.30f, .22f), imageAsset = "apfel"),
    Product("Kaffee", "Tisch 5", "55 m", Color(0xFF8B6F47), Offset(.70f, .58f), imageAsset = "kaffee"),
    Product("Alpro Barista Hafermilch", "Tisch 4", "36 m", Color(0xFFB9825B), Offset(.30f, .62f), imageAsset = "barista_hafermilch"),
    Product("Frische Milch von Weihenstephan", "Tisch 3", "31 m", Color(0xFF77A9D8), Offset(.70f, .38f), imageAsset = "milch"),
    Product("Gerolsteiner Wasser", "Tisch 7", "48 m", Color(0xFF5BA9D6), Offset(.52f, .18f), imageAsset = "gerolsteiner"),
    Product(
        "Bäcker Semmel",
        "Tisch 8",
        "24 m",
        Color(0xFFD79A42),
        Offset(.52f, .78f),
        imageAsset = "baecker_semmel"
    )
)

private fun optimizedRoute(
    start: Offset,
    destinations: List<Product>
): List<Product> {
    val remaining = destinations.toMutableList()
    val result = mutableListOf<Product>()
    var current = start
    while (remaining.isNotEmpty()) {
        val next = remaining.minByOrNull {
            val dx = it.location.x - current.x
            val dy = it.location.y - current.y
            dx * dx + dy * dy
        } ?: break
        result += next
        remaining -= next
        current = next.location
    }
    return result
}

class MainActivity : ComponentActivity() {
    private var locationPermissionGranted by mutableStateOf(false)
    private var currentPosition by mutableStateOf(classroomPositions.first())
    private val scanner by lazy { GmsBarcodeScanning.getClient(this) }
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        locationPermissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        locationPermissionGranted = hasLocationPermission()
        updatePositionFromIntent(intent)
        if (!locationPermissionGranted) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
        setContent {
            ShelfiTheme(dynamicColor = false) {
                ShelfiApp(
                    locationPermissionGranted = locationPermissionGranted,
                    currentPosition = currentPosition,
                    onScanQr = ::scanQrCode,
                    onFeedback = ::openFeedbackMail
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        updatePositionFromIntent(intent)
    }

    private fun updatePositionFromIntent(intent: Intent?) {
        val id = intent?.data?.getQueryParameter("position") ?: return
        currentPosition = classroomPositions.firstOrNull { it.id == id } ?: currentPosition
    }

    private fun scanQrCode() {
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                updatePositionFromQrValue(barcode.rawValue)
            }
    }

    private fun openFeedbackMail() {
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:akalin@hm.edu")),
                "Feedback senden"
            )
        )
    }

    private fun updatePositionFromQrValue(value: String?) {
        val rawValue = value?.trim().orEmpty()
        val uri = runCatching { android.net.Uri.parse(rawValue) }.getOrNull()
        val position = uri?.getQueryParameter("position")
            ?: rawValue.substringAfter("position=", missingDelimiterValue = "")
                .substringBefore("&")
                .substringBefore("\n")
                .trim()
                .lowercase()
        classroomPositions.firstOrNull { it.id == position }?.let {
            currentPosition = it
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

}

@Composable
private fun ShelfiApp(
    locationPermissionGranted: Boolean,
    currentPosition: ClassroomPosition,
    onScanQr: () -> Unit,
    onFeedback: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(AppTab.MAP) }
    var query by remember { mutableStateOf("") }
    var shoppingList by remember { mutableStateOf(listOf<Product>()) }
    var selectedProduct by remember { mutableStateOf(products.first()) }
    var focusedRouteIndex by remember { mutableStateOf(0) }
    var routeCompleted by remember { mutableStateOf(false) }
    var route by remember { mutableStateOf(emptyList<Product>()) }
    var routeStartPosition by remember { mutableStateOf(currentPosition.location) }
    var routeLocationLabel by remember { mutableStateOf(currentPosition.label) }
    val focusedProduct = route.getOrNull(focusedRouteIndex) ?: selectedProduct
    val filteredProducts = products.filter {
        it.name.contains(query.trim(), ignoreCase = true) ||
            it.aisle.contains(query.trim(), ignoreCase = true)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFEFF4F0)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when (selectedTab) {
                    AppTab.MAP -> MapTab(
                        locationPermissionGranted = locationPermissionGranted,
                        currentPosition = currentPosition,
                        routeLocationLabel = routeLocationLabel,
                        onScanQr = onScanQr,
                        route = route,
                        routeStartPosition = routeStartPosition,
                        selectedProduct = focusedProduct,
                        focusedRouteIndex = focusedRouteIndex,
                        routeCompleted = routeCompleted,
                        onCompleteStop = {
                            if (route.isNotEmpty()) {
                                val completedProduct = route[focusedRouteIndex]
                                val remaining = route.filterNot { it == completedProduct }
                                shoppingList = remaining
                                route = remaining
                                routeStartPosition = completedProduct.location
                                routeLocationLabel = completedProduct.name
                                focusedRouteIndex = 0
                                routeCompleted = remaining.isEmpty()
                            }
                        },
                        onPreviousStop = {
                            if (focusedRouteIndex > 0) {
                                focusedRouteIndex--
                                routeCompleted = false
                            }
                        },
                        onSelectStop = { index ->
                            focusedRouteIndex = index
                            routeCompleted = false
                        }
                    )
                    AppTab.PRODUCTS -> ProductsTab(
                        query = query,
                        onQueryChange = { query = it },
                        products = filteredProducts,
                        shoppingList = shoppingList,
                        onToggleProduct = { product ->
                            val updatedList = if (product in shoppingList) {
                                shoppingList - product
                            } else {
                                shoppingList + product
                            }
                            shoppingList = updatedList
                            route = if (updatedList.isEmpty()) {
                                emptyList()
                            } else if (route.isEmpty()) {
                                optimizedRoute(routeStartPosition, updatedList)
                            } else if (product in route) {
                                route.filter { it in updatedList }
                            } else {
                                route + product
                            }
                            focusedRouteIndex = 0
                            routeCompleted = false
                        },
                        onShowRoute = { selectedTab = AppTab.MAP }
                    )
                    AppTab.PROFILE -> ProfileTab(onFeedback = onFeedback)
                }
            }
            BottomNavigation(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }
    }
}

@Composable
private fun MapTab(
    locationPermissionGranted: Boolean,
    currentPosition: ClassroomPosition,
    routeLocationLabel: String,
    onScanQr: () -> Unit,
    route: List<Product>,
    selectedProduct: Product,
    routeStartPosition: Offset,
    focusedRouteIndex: Int,
    routeCompleted: Boolean,
    onCompleteStop: () -> Unit,
    onPreviousStop: () -> Unit,
    onSelectStop: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Image(
                    painter = painterResource(com.example.shelfi.R.drawable.shelfilogo),
                    contentDescription = "Shelfi Logo",
                    modifier = Modifier
                        .offset(x = (-20).dp, y = 18.dp)
                        .width(205.dp)
                        .height(72.dp)
                )
            }
            Box(
                modifier = Modifier
                    .offset(y = 18.dp)
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEFF4F0)),
                contentAlignment = Alignment.Center
            ) {
                Text("S", color = Color(0xFF457C53), fontWeight = FontWeight.Bold)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Klassenraum · 40 m²", color = Color(0xFF263A2E), fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                if (locationPermissionGranted) "STANDORT AKTIV" else "STANDORT AUS",
                color = if (locationPermissionGranted) Color(0xFF457C53) else Color(0xFFD64545),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        PositionSelector(currentPosition, routeLocationLabel, onScanQr)
        if (route.isNotEmpty()) {
            RouteProgress(
                route = route,
                focusedRouteIndex = focusedRouteIndex,
                onSelectStop = onSelectStop
            )
        }
        StoreMap(
            products = if (route.isEmpty() && !routeCompleted) listOf(selectedProduct) else route,
            startPosition = routeStartPosition,
            focusedProduct = selectedProduct,
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
        DirectionPanel(
            product = selectedProduct,
            routeSize = route.size,
            focusedRouteIndex = focusedRouteIndex,
            routeCompleted = routeCompleted,
            locationPermissionGranted = locationPermissionGranted,
            onCompleteStop = onCompleteStop,
            onPreviousStop = onPreviousStop
        )
    }
}

@Composable
private fun RouteProgress(
    route: List<Product>,
    focusedRouteIndex: Int,
    onSelectStop: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            "Deine Route · ${focusedRouteIndex + 1} von ${route.size}",
            color = Color(0xFF263A2E),
            fontWeight = FontWeight.Bold
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(route) { product ->
                val index = route.indexOf(product)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (index == focusedRouteIndex) Color(0xFF457C53)
                            else Color.White
                        )
                        .border(
                            1.dp,
                            if (index == focusedRouteIndex) Color(0xFF457C53) else Color(0xFFD9E2EC),
                            RoundedCornerShape(50)
                        )
                        .clickable { onSelectStop(index) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${index + 1}.",
                        color = if (index == focusedRouteIndex) Color.White else Color(0xFF457C53),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        product.name,
                        color = if (index == focusedRouteIndex) Color.White else Color(0xFF334E68),
                        fontSize = 12.sp,
                        fontWeight = if (index == focusedRouteIndex) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductsTab(
    query: String,
    onQueryChange: (String) -> Unit,
    products: List<Product>,
    shoppingList: List<Product>,
    onToggleProduct: (Product) -> Unit,
    onShowRoute: () -> Unit
) {
    var showWaterDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Produkte", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xFF263A2E))
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Was möchtest du finden?", color = Color(0xFF829AB1)) },
            leadingIcon = { SearchGlyph() },
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = Color(0xFF457C53)
            )
        )
        Text("Einkaufsliste (${shoppingList.size})", color = Color(0xFF263A2E), fontWeight = FontWeight.Bold)
        if (shoppingList.isEmpty()) {
            Text("Füge Produkte hinzu, um die optimale Route zu planen.", color = Color(0xFF627D98), fontSize = 13.sp)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shoppingList) { product ->
                    ProductChip(product, selected = true) { onToggleProduct(product) }
                }
            }
            Button(
                onClick = onShowRoute,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF457C53)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Optimale Route anzeigen", fontWeight = FontWeight.Bold)
            }
        }
        Text("Alle Produkte", color = Color(0xFF263A2E), fontWeight = FontWeight.Bold)
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(products) { product ->
                val inList = product in shoppingList
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProductImagePlaceholder(product)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(product.name, color = Color(0xFF263A2E), fontWeight = FontWeight.Bold)
                        Text("${product.aisle} · ${product.distance}", color = Color(0xFF627D98), fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            if (product.name == "Gerolsteiner Wasser" && !inList) {
                                showWaterDialog = true
                            } else {
                                onToggleProduct(product)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (inList) Color(0xFFEFF4F0) else Color(0xFF457C53),
                            contentColor = if (inList) Color(0xFF457C53) else Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(if (inList) "Entfernen" else "Hinzufügen", fontSize = 12.sp)
                    }
                }

            }
        }
    }

    if (showWaterDialog) {
        AlertDialog(
            onDismissRequest = { showWaterDialog = false },
            title = { Text("Hinweis") },
            text = { Text("Müll wird hier nicht verkauft") },
            confirmButton = {
                Button(onClick = { showWaterDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun ProductImagePlaceholder(product: Product) {
    val context = LocalContext.current
    val imageResource = product.imageAsset?.let {
        context.resources.getIdentifier(it, "drawable", context.packageName)
    } ?: 0

    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF0F4F7))
            .border(1.dp, Color(0xFFD9E2EC), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (imageResource != 0) {
            Image(
                painter = painterResource(imageResource),
                contentDescription = product.name,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                "Bild",
                color = Color(0xFF829AB1),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ProfileTab(onFeedback: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(modifier = Modifier.size(76.dp).clip(CircleShape)        .background(Color(0xFFEFF4F0)), contentAlignment = Alignment.Center) {
        Text("S", color = Color(0xFF457C53), fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text("Dein Profil", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFF263A2E))
        Text("Standort und Einstellungen", color = Color(0xFF627D98))
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onFeedback,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF457C53)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Feedback senden", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BottomNavigation(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        BottomNavigationItem("⌖", "Karte", AppTab.MAP, selectedTab, onTabSelected)
        BottomNavigationItem("▦", "Produkte", AppTab.PRODUCTS, selectedTab, onTabSelected)
        BottomNavigationItem("●", "Profil", AppTab.PROFILE, selectedTab, onTabSelected)
    }
}

@Composable
private fun BottomNavigationItem(
    icon: String,
    label: String,
    tab: AppTab,
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit
) {
    val selected = tab == selectedTab
    Column(
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable { onTabSelected(tab) }.padding(horizontal = 16.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, color = if (selected) Color(0xFF457C53) else Color(0xFF829AB1), fontSize = 20.sp)
        Text(label, color = if (selected) Color(0xFF457C53) else Color(0xFF627D98), fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun PositionSelector(
    currentPosition: ClassroomPosition,
    routeLocationLabel: String,
    onScanQr: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Deine Position", color = Color(0xFF627D98), fontSize = 12.sp)
            Text(
                routeLocationLabel,
                color = Color(0xFF457C53),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = onScanQr,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF457C53),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("QR-Code scannen", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ClassroomLegend() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("▣  Tischreihen", color = Color(0xFF486581), fontSize = 11.sp)
        Text("▣  QR-Position", color = Color(0xFF457C53), fontSize = 11.sp)
        Text("Tür  →", color = Color(0xFF486581), fontSize = 11.sp)
    }
}

@Composable
private fun EmptySearch(query: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Kein Produkt gefunden", color = Color(0xFF263A2E), fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text("Keine Treffer für „$query“", color = Color(0xFF627D98))
        }
    }
}

@Composable
private fun ProductChip(product: Product, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Color(0xFF457C53) else Color.White)
            .border(
                width = 1.dp,
                color = if (selected) Color(0xFF457C53) else Color(0xFFD9E2EC),
                shape = RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(product.color)
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            product.name,
            color = if (selected) Color.White else Color(0xFF334E68),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun StoreMap(
    products: List<Product>,
    startPosition: Offset,
    focusedProduct: Product,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFFEFF4F0))
            .border(1.dp, Color(0xFFD6E5E2), RoundedCornerShape(24.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            val w = size.width
            val h = size.height
            val desk = Color(0xFFEFF4F0)
            val deskEdge = Color(0xFFAA6937)

            drawRoundRect(
                color = Color(0xFFF8FCFB),
                topLeft = Offset(8f, 8f),
                size = androidx.compose.ui.geometry.Size(w - 16f, h - 16f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(22f, 22f)
            )
            // Six table squares: three on the left and three on the right.
            for (row in 0..2) {
                val y = h * (.13f + row * .22f)
                drawRoundRect(
                    color = desk,
                    topLeft = Offset(w * .16f, y),
                    size = androidx.compose.ui.geometry.Size(w * .25f, h * .15f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f, 14f)
                )
                drawRoundRect(
                    color = desk,
                    topLeft = Offset(w * .59f, y),
                    size = androidx.compose.ui.geometry.Size(w * .25f, h * .15f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f, 14f)
                )
                drawLine(deskEdge, Offset(w * .22f, y + h * .075f), Offset(w * .35f, y + h * .075f), 3f)
                drawLine(deskEdge, Offset(w * .65f, y + h * .075f), Offset(w * .78f, y + h * .075f), 3f)
            }
            drawLine(Color(0xFFE8A23A), Offset(w * .46f, h * .91f), Offset(w * .56f, h * .91f), 10f)
            for (point in listOf(
                Offset(w * .12f, h * .84f),
                Offset(w * .08f, h * .07f),
                Offset(w * .92f, h * .07f),
                Offset(w * .84f, h * .84f)
            )) {
                val pin = Path().apply {
                    moveTo(point.x, point.y + 17f)
                    cubicTo(
                        point.x - 3f, point.y + 11f,
                        point.x - 12f, point.y + 5f,
                        point.x - 12f, point.y - 4f
                    )
                    arcTo(
                        androidx.compose.ui.geometry.Rect(
                            point.x - 12f,
                            point.y - 16f,
                            point.x + 12f,
                            point.y + 8f
                        ),
                        180f,
                        180f,
                        false
                    )
                    cubicTo(
                        point.x + 12f, point.y + 5f,
                        point.x + 3f, point.y + 11f,
                        point.x, point.y + 17f
                    )
                    close()
                }
                drawPath(pin, Color(0xFFAA6937))
                drawCircle(Color.White, 4f, Offset(point.x, point.y - 4f))
            }

            val start = Offset(w * startPosition.x, h * startPosition.y)
            val targets = products.map { Offset(w * it.location.x, h * it.location.y) }
            // Use the outside corridor, then enter each destination table from its side.
            val rowGaps = listOf(h * .31f, h * .53f, h * .80f)
            val route = Path().apply {
                moveTo(start.x, start.y)
                var previous = start
                targets.forEach { target ->
                    val startSideX = if (previous.x < w * .5f) w * .08f else w * .92f
                    val targetSideX = if (target.x < w * .5f) w * .08f else w * .92f
                    val tableEdgeX = if (target.x < w * .5f) w * .16f else w * .84f
                    val crossingY = rowGaps.minByOrNull { kotlin.math.abs(it - target.y) } ?: h * .80f
                    lineTo(startSideX, previous.y)
                    if (startSideX != targetSideX) {
                        lineTo(startSideX, crossingY)
                        lineTo(targetSideX, crossingY)
                    }
                    lineTo(targetSideX, target.y)
                    lineTo(tableEdgeX, target.y)
                    lineTo(target.x, target.y)
                    previous = target
                }
            }
            drawPath(route, Color(0xFF9EDDD3), style = Stroke(14f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(route, Color(0xFF457C53), style = Stroke(6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            targets.forEachIndexed { index, target ->
                val product = products[index]
                val isFocused = product == focusedProduct
                if (isFocused) {
                    drawCircle(Color(0xFF457C53), 27f, target)
                    drawCircle(Color.White, 22f, target)
                }
                drawCircle(product.color, if (isFocused) 19f else 14f, target)
                drawCircle(Color.White, 6f, target)
            }
            drawCircle(Color.White, 28f, start)
            drawCircle(Color(0xFF2F80ED), 20f, start)
            drawCircle(Color.White, 9f, start)
        }
        Text(
            when {
                products.size > 1 -> "${products.size} STOPPS"
                products.size == 1 -> products.first().aisle.uppercase()
                else -> "ROUTE ABGESCHLOSSEN"
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(24.dp),
            color = Color(0xFF457C53),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DirectionPanel(
    product: Product,
    routeSize: Int,
    focusedRouteIndex: Int,
    routeCompleted: Boolean,
    locationPermissionGranted: Boolean,
    onCompleteStop: () -> Unit,
    onPreviousStop: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF263A2E))
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color(0xFF1B405E)),
            contentAlignment = Alignment.Center
        ) {
            Text("↗", color = Color(0xFF67D5C4), fontSize = 38.sp, fontWeight = FontWeight.Light)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                when {
                    routeCompleted -> "Einkauf vorbei"
                    routeSize > 1 -> "Stopp ${focusedRouteIndex + 1}: ${product.name}"
                    else -> "Zu ${product.name}"
                },
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "${product.aisle}  ·  ${product.distance} entfernt" +
                    if (locationPermissionGranted) "  ·  Standort aktiv" else "",
                color = Color(0xFFB8CCE0),
                fontSize = 13.sp
            )
        }
        if (routeCompleted) {
            Text(
                "Einkauf vorbei",
                color = Color(0xFF67D5C4),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        } else {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF457C53))
                    .clickable(enabled = !routeCompleted) {
                        onCompleteStop()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "✓",
                    color = if (!routeCompleted) Color.White else Color(0xFFB8CCE0),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
    if (routeSize > 1 && (focusedRouteIndex > 0 || routeCompleted)) {
        Button(
            onClick = onPreviousStop,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFEFF4F0),
                contentColor = Color(0xFF457C53)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("← Zum vorherigen Produkt", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SearchGlyph() {
    Canvas(modifier = Modifier.size(20.dp)) {
        drawCircle(Color(0xFF627D98), radius = 6f, center = Offset(8f, 8f), style = Stroke(2.5f))
        drawLine(Color(0xFF627D98), Offset(12.5f, 12.5f), Offset(17f, 17f), 2.5f, cap = StrokeCap.Round)
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 840)
@Composable
private fun ShelfiPreview() {
    ShelfiTheme(dynamicColor = false) {
        ShelfiApp(
            locationPermissionGranted = false,
            currentPosition = classroomPositions.first(),
            onScanQr = {},
            onFeedback = {}
        )
    }
}
