package com.sonu.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.ViewGroup
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.sonu.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

sealed class AppScreen {
    object Login : AppScreen()
    data class Home(val userName: String) : AppScreen()
    data class WebView(val url: String) : AppScreen()
}

data class UpdateInfo(
    val versionName: String,
    val changelog: String,
    val downloadUrl: String
)

class MainActivity : ComponentActivity() {

    private var geolocationCallback: GeolocationPermissions.Callback? = null
    private var geolocationOrigin: String? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            geolocationCallback?.invoke(geolocationOrigin, true, false)
        } else {
            geolocationCallback?.invoke(geolocationOrigin, false, false)
            Toast.makeText(this, "Location permission is required for station detection.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        checkAndRequestLocationPermissions()

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Login) }
                    var showWebsiteSelectionDialog by remember { mutableStateOf(false) }
                    var loggedInUserName by remember { mutableStateOf("") }

                    // Check for App Updates from GitHub on startup (current versionCode = 2)
                    CheckAppUpdates(currentVersionCode = 2)

                    when (val screen = currentScreen) {
                        is AppScreen.Login -> {
                            LoginScreen(
                                onLoginSuccess = { userName ->
                                    loggedInUserName = userName
                                    currentScreen = AppScreen.Home(userName)
                                },
                                onAccessDeniedExit = { reason ->
                                    Toast.makeText(this, reason, Toast.LENGTH_LONG).show()
                                    finishAffinity()
                                    exitProcess(0)
                                }
                            )
                        }
                        is AppScreen.Home -> {
                            HomeScreen(
                                userName = screen.userName,
                                onMagicClick = {
                                    showWebsiteSelectionDialog = true
                                }
                            )

                            if (showWebsiteSelectionDialog) {
                                AlertDialog(
                                    onDismissRequest = { showWebsiteSelectionDialog = false },
                                    title = {
                                        Text(
                                            text = "Select IRCTC Version",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp,
                                            color = Color.White
                                        )
                                    },
                                    text = {
                                        Text(
                                            text = "Choose which website version you would like to open:",
                                            fontSize = 14.sp,
                                            color = Color.White.copy(alpha = 0.85f)
                                        )
                                    },
                                    containerColor = Color(0xFF101828),
                                    shape = RoundedCornerShape(24.dp),
                                    confirmButton = {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    showWebsiteSelectionDialog = false
                                                    currentScreen = AppScreen.WebView("https://www.irctc.co.in/nget/train-search")
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)),
                                                shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier.fillMaxWidth().height(50.dp)
                                            ) {
                                                Text(
                                                    text = "Old Website",
                                                    color = Color(0xFF1A237E),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp
                                                )
                                            }

                                            Button(
                                                onClick = {
                                                    showWebsiteSelectionDialog = false
                                                    currentScreen = AppScreen.WebView("https://www.irctc.co.in/eticket/train-search")
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                                                shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier.fillMaxWidth().height(50.dp)
                                            ) {
                                                Text(
                                                    text = "New Website",
                                                    color = Color(0xFF0D47A1),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp
                                                )
                                            }
                                        }
                                    },
                                    dismissButton = {}
                                )
                            }
                        }
                        is AppScreen.WebView -> {
                            IRCTCWebViewScreen(
                                targetUrl = screen.url,
                                onBackToHome = {
                                    currentScreen = AppScreen.Home(loggedInUserName)
                                },
                                onGeolocationRequest = { origin, callback ->
                                    geolocationOrigin = origin
                                    geolocationCallback = callback
                                    checkAndRequestLocationPermissions()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun checkAndRequestLocationPermissions() {
        val fineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!fineLocation || !coarseLocation) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }
}

@Composable
fun CheckAppUpdates(currentVersionCode: Int = 2) {
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadStatusText by remember { mutableStateOf("Preparing download...") }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("https://raw.githubusercontent.com/imsonuksingh/AutomationAPK/main/update.json")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 6000
                connection.readTimeout = 6000

                if (connection.responseCode == 200) {
                    val stream = connection.inputStream
                    val reader = BufferedReader(InputStreamReader(stream))
                    val responseText = reader.use { it.readText() }

                    val json = JSONObject(responseText)
                    val serverVersionCode = json.optInt("versionCode", 1)
                    val serverVersionName = json.optString("versionName", "1.0")
                    val changelog = json.optString("changelog", "Bug fixes and performance improvements.")
                    val downloadUrl = json.optString("downloadUrl", "https://github.com/imsonuksingh/AutomationAPK")

                    if (serverVersionCode > currentVersionCode) {
                        withContext(Dispatchers.Main) {
                            updateInfo = UpdateInfo(
                                versionName = serverVersionName,
                                changelog = changelog,
                                downloadUrl = downloadUrl
                            )
                            showUpdateDialog = true
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Already on Latest Version (v1.1)", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore network errors silently during background update check
            }
        }
    }

    fun downloadAndInstallApk(apkUrl: String) {
        isDownloading = true
        downloadStatusText = "Downloading Update APK..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                var currentUrl = apkUrl
                var connection: HttpURLConnection
                var responseCode: Int

                // Follow HTTP/HTTPS & GitHub S3 redirects manually
                while (true) {
                    val url = URL(currentUrl)
                    connection = url.openConnection() as HttpURLConnection
                    connection.instanceFollowRedirects = true
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    connection.connect()

                    responseCode = connection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_MOVED_PERM || 
                        responseCode == HttpURLConnection.HTTP_MOVED_TEMP || 
                        responseCode == 307 || responseCode == 308) {
                        val location = connection.getHeaderField("Location")
                        if (location != null) {
                            currentUrl = location
                            connection.disconnect()
                            continue
                        }
                    }
                    break
                }

                val fileLength = connection.contentLength
                val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                val apkFile = File(downloadsDir, "update.apk")

                if (apkFile.exists()) apkFile.delete()

                val inputStream = connection.inputStream
                val outputStream = FileOutputStream(apkFile)

                val buffer = ByteArray(8192)
                var total: Long = 0
                var count: Int

                while (inputStream.read(buffer).also { count = it } != -1) {
                    total += count.toLong()
                    if (fileLength > 0) {
                        downloadProgress = (total.toFloat() / fileLength.toFloat())
                        downloadStatusText = "Downloading Update: ${(downloadProgress * 100).toInt()}%"
                    } else {
                        downloadStatusText = "Downloading Update: ${(total / 1024)} KB"
                    }
                    outputStream.write(buffer, 0, count)
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()
                connection.disconnect()

                // Check if file is valid APK (> 50KB)
                if (apkFile.length() < 50000) {
                    withContext(Dispatchers.Main) {
                        isDownloading = false
                        Toast.makeText(context, "Opening direct update link in browser...", Toast.LENGTH_LONG).show()
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, apkUrl.toUri())
                            context.startActivity(intent)
                        } catch (ex: Exception) {
                            // ignore
                        }
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    downloadStatusText = "Download Complete! Launching Installer..."
                    delay(500)
                    isDownloading = false
                    showUpdateDialog = false

                    // Launch Package Installer via FileProvider
                    try {
                        val apkUri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.provider",
                            apkFile
                        )
                        val installIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(apkUri, "application/vnd.android.package-archive")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        context.startActivity(installIntent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(context, "Error launching installer. Please grant install permission.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isDownloading = false
                    Toast.makeText(context, "Download failed. Redirecting to release page...", Toast.LENGTH_LONG).show()
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, apkUrl.toUri())
                        context.startActivity(intent)
                    } catch (ex: Exception) {
                        // ignore
                    }
                }
            }
        }
    }

    if (showUpdateDialog && updateInfo != null) {
        val info = updateInfo!!
        AlertDialog(
            onDismissRequest = { if (!isDownloading) showUpdateDialog = false },
            title = {
                Text(
                    text = "New Update Available (v${info.versionName})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.White
                )
            },
            text = {
                Column {
                    Text(
                        text = "A new version of the app is available! Here is what's new:",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = info.changelog,
                            fontSize = 13.sp,
                            color = Color(0xFF00E5FF),
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    if (isDownloading) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = downloadStatusText,
                            fontSize = 13.sp,
                            color = Color(0xFFFFD700),
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = Color(0xFF00E5FF),
                            trackColor = Color.Gray.copy(alpha = 0.3f)
                        )
                    }
                }
            },
            containerColor = Color(0xFF101828),
            shape = RoundedCornerShape(24.dp),
            confirmButton = {
                if (!isDownloading) {
                    Button(
                        onClick = {
                            downloadAndInstallApk(info.downloadUrl)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text(
                            text = "Download & Install Update",
                            color = Color(0xFF1A237E),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            },
            dismissButton = {
                if (!isDownloading) {
                    TextButton(onClick = { showUpdateDialog = false }) {
                        Text(text = "Later", color = Color.Gray)
                    }
                }
            }
        )
    }
}

@Composable
fun LoginScreen(
    onLoginSuccess: (String) -> Unit,
    onAccessDeniedExit: (String) -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val sharedPrefs = remember { context.getSharedPreferences("app_license_prefs", Context.MODE_PRIVATE) }
    
    val savedKey = remember { sharedPrefs.getString("saved_license_key", "") ?: "" }
    var licenseKeyInput by remember { mutableStateOf(savedKey) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { 
        mutableStateOf(if (savedKey.isNotEmpty()) "Status: Found saved key '$savedKey'. Verifying..." else "Status: Waiting for key input...") 
    }

    val currentDeviceId = remember {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "UNKNOWN_DEVICE"
    }

    // Continuous blinking animation for Blocked status & Linked status
    val blinkTransition = rememberInfiniteTransition(label = "StatusBlink")
    val blinkAlpha by blinkTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 450, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BlinkAlpha"
    )

    fun verifyAndProcessKey(keyToVerify: String, isAutoCheck: Boolean = false) {
        if (keyToVerify.isEmpty()) {
            statusMessage = "Status: Error - Please enter your license key!"
            return
        }

        isLoading = true

        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.Main) {
                statusMessage = if (isAutoCheck) "Status: [1/4] Found saved key '$keyToVerify'..." else "Status: [1/4] Initializing connection..."
            }
            delay(200)

            withContext(Dispatchers.Main) {
                statusMessage = "Status: [2/4] Connecting to Firebase Realtime Server..."
            }
            delay(200)

            withContext(Dispatchers.Main) {
                statusMessage = "Status: [3/4] Querying key record '$keyToVerify'..."
            }

            try {
                val encodedKey = URLEncoder.encode(keyToVerify, "UTF-8")
                val url = URL("https://irctc-a6c64-default-rtdb.firebaseio.com/keys/$encodedKey.json")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 8000
                connection.readTimeout = 8000

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val stream = connection.inputStream
                    val reader = BufferedReader(InputStreamReader(stream))
                    val responseText = reader.use { it.readText() }

                    // UNREGISTERED / WRONG KEY -> CRASH & EXIT APP
                    if (responseText == "null" || responseText.trim().isEmpty()) {
                        withContext(Dispatchers.Main) {
                            isLoading = false
                            sharedPrefs.edit().clear().apply()
                            statusMessage = "Status: Access Denied - Key Not Found!"
                            delay(600)
                            Toast.makeText(context, "Access Denied: Invalid Key!", Toast.LENGTH_LONG).show()
                            onAccessDeniedExit("Access Denied: Invalid Key!")
                        }
                    } else {
                        // Key IS Registered -> Parse details
                        val json = JSONObject(responseText)
                        val isBlocked = json.optBoolean("isBlocked", false)
                        val isActive = json.optBoolean("isActive", true)
                        val userName = json.optString("userName", "User")
                        val expiryTimestamp = json.optLong("expiryTimestamp", -1L)
                        val savedDeviceId = json.optString("deviceId", "")
                        val currentTimestamp = System.currentTimeMillis()

                        // 1. CHECK IF KEY IS BLOCKED BY ADMIN
                        if (isBlocked || !isActive) {
                            withContext(Dispatchers.Main) {
                                isLoading = false
                                sharedPrefs.edit().clear().apply()
                                statusMessage = "Status: Key Blocked!"
                                Toast.makeText(context, "Key Blocked by Admin!", Toast.LENGTH_LONG).show()
                                // DO NOT CRASH - Stay on login screen with Blinking Red Status
                            }
                            return@launch
                        }

                        // 2. CHECK IF KEY IS EXPIRED
                        if (expiryTimestamp != -1L && currentTimestamp > expiryTimestamp) {
                            withContext(Dispatchers.Main) {
                                isLoading = false
                                sharedPrefs.edit().clear().apply()
                                statusMessage = "Status: Access Denied - Key Expired!"
                                Toast.makeText(context, "Key Expired!", Toast.LENGTH_LONG).show()
                                // DO NOT CRASH - Stay on login screen
                            }
                            return@launch
                        }

                        // 3. CHECK DEVICE BINDING (1 Key = 1 Device)
                        if (savedDeviceId.isEmpty() || savedDeviceId == "null") {
                            // First time login for this key -> Link current device ID
                            withContext(Dispatchers.Main) {
                                statusMessage = "Status: [4/4] Linking device & verifying..."
                            }

                            val patchUrl = URL("https://irctc-a6c64-default-rtdb.firebaseio.com/keys/$encodedKey/deviceId.json")
                            val patchConn = patchUrl.openConnection() as HttpURLConnection
                            patchConn.requestMethod = "PUT"
                            patchConn.doOutput = true
                            patchConn.setRequestProperty("Content-Type", "application/json")
                            val jsonPayload = "\"$currentDeviceId\""
                            patchConn.outputStream.use { os -> os.write(jsonPayload.toByteArray()) }
                            patchConn.responseCode // execute update

                            withContext(Dispatchers.Main) {
                                isLoading = false
                                sharedPrefs.edit()
                                    .putString("saved_license_key", keyToVerify)
                                    .putString("saved_user_name", userName)
                                    .apply()

                                statusMessage = "Status: Key Verified! Welcome, $userName!"
                                Toast.makeText(context, "Login Successful!", Toast.LENGTH_SHORT).show()
                                Toast.makeText(context, "Welcome, $userName!", Toast.LENGTH_LONG).show()
                                onLoginSuccess(userName)
                            }
                        } else if (savedDeviceId == currentDeviceId) {
                            // Device matches!
                            withContext(Dispatchers.Main) {
                                isLoading = false
                                sharedPrefs.edit()
                                    .putString("saved_license_key", keyToVerify)
                                    .putString("saved_user_name", userName)
                                    .apply()

                                statusMessage = "Status: Key Verified! Welcome, $userName!"
                                Toast.makeText(context, "Login Successful!", Toast.LENGTH_SHORT).show()
                                Toast.makeText(context, "Welcome, $userName!", Toast.LENGTH_LONG).show()
                                onLoginSuccess(userName)
                            }
                        } else {
                            // DEVICE MISMATCH -> Key is linked to another device!
                            withContext(Dispatchers.Main) {
                                isLoading = false
                                sharedPrefs.edit().clear().apply()
                                statusMessage = "Status: Already linked with another device!"
                                Toast.makeText(context, "Already linked with another device!", Toast.LENGTH_LONG).show()
                                // DO NOT CRASH - Stay on login screen with Blinking Green Status
                            }
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        statusMessage = "Status: Access Denied - Server Error ($responseCode)!"
                        delay(600)
                        Toast.makeText(context, "Access Denied: Server Error ($responseCode)!", Toast.LENGTH_LONG).show()
                        onAccessDeniedExit("Access Denied: Server Error!")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isLoading = false
                    statusMessage = "Status: Access Denied - Connection Failure!"
                    delay(600)
                    Toast.makeText(context, "Access Denied: Connection Failure!", Toast.LENGTH_LONG).show()
                    onAccessDeniedExit("Access Denied: Connection Failure!")
                }
            }
        }
    }

    // Auto-check on launch ONLY if a saved key exists
    LaunchedEffect(Unit) {
        if (savedKey.isNotEmpty()) {
            verifyAndProcessKey(savedKey, isAutoCheck = true)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Background Image
        Image(
            painter = painterResource(id = R.drawable.app_back),
            contentDescription = "Background Image",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Dark overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF101828).copy(alpha = 0.92f))
                .padding(28.dp)
        ) {
            Text(
                text = "Enter License Key",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = licenseKeyInput,
                onValueChange = { licenseKeyInput = it.trim() },
                placeholder = {
                    Text(text = "Enter 6-character key", color = Color.Gray)
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFFFD700),
                    unfocusedBorderColor = Color.Gray
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Real-time Console Status Box with Blinking animation for Blocked key and Linked status
            val isBlockedStatus = statusMessage.contains("Blocked")
            val isLinkedStatus = statusMessage.contains("linked")
            val isDeniedStatus = statusMessage.contains("Denied")
            val isBlinkingStatus = isBlockedStatus || isLinkedStatus

            val statusColor = when {
                isLinkedStatus -> Color(0xFF00FF66) // Bright Green for Already Linked
                isBlockedStatus || isDeniedStatus -> Color(0xFFFF1744) // Red for Blocked / Denied
                else -> Color(0xFFFFD700) // Gold for Normal Progress
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .border(BorderStroke(1.dp, if (isLinkedStatus) Color(0xFF00FF66).copy(alpha = 0.6f) else if (isBlockedStatus) Color(0xFFFF1744) else Color(0xFF00E5FF).copy(alpha = 0.4f)), RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = statusMessage,
                    color = statusColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.graphicsLayer {
                        alpha = if (isBlinkingStatus) blinkAlpha else 1f
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    verifyAndProcessKey(licenseKeyInput.trim(), isAutoCheck = false)
                },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color(0xFF1A237E),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Submit Key",
                        color = Color(0xFF1A237E),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun DigitalClock() {
    var currentTime by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            val formatter = SimpleDateFormat("EEEE, dd MMM yyyy | hh:mm:ss a", Locale.getDefault())
            currentTime = formatter.format(Date())
            delay(1000L)
        }
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(
            text = currentTime,
            color = Color(0xFFFFD700), // Gold color
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun AnimatedMagicButton(onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "MagicAnimation")
    
    // Rotating angle for particle orbits
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rotation"
    )

    // Pulsing scale for particles
    val particlePulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Pulse"
    )

    // Mostly Transparent Light Pastel Colors Palette (alpha = 0.25f)
    val lightColors = listOf(
        Color(0xFFFFF59D).copy(alpha = 0.25f), // Transparent Soft Yellow
        Color(0xFF80DEEA).copy(alpha = 0.25f), // Transparent Soft Cyan
        Color(0xFFF48FB1).copy(alpha = 0.25f), // Transparent Soft Pink
        Color(0xFFCE93D8).copy(alpha = 0.25f), // Transparent Soft Lavender
        Color(0xFFAED581).copy(alpha = 0.25f), // Transparent Soft Lime
        Color(0xFFFFCC80).copy(alpha = 0.25f)  // Transparent Soft Peach
    )

    val colorProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (lightColors.size - 1).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ColorProgress"
    )

    // Interpolate / Fading transition between transparent light colors
    val currentIndex = colorProgress.toInt().coerceIn(0, lightColors.size - 2)
    val nextIndex = (currentIndex + 1).coerceIn(0, lightColors.size - 1)
    val fraction = colorProgress - currentIndex

    val animatedTransparentColor = lerp(lightColors[currentIndex], lightColors[nextIndex], fraction)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth(0.72f)
            .height(72.dp)
    ) {
        // Outer Moving Dot Particles ONLY
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .scale(particlePulse)
        ) {
            val centerPoint = center
            val buttonWidth = size.width
            val buttonHeight = size.height
            val radiusX = buttonWidth / 2f
            val radiusY = buttonHeight / 2f

            val rainbowColors = listOf(
                Color(0xFFFFD700), // Gold
                Color(0xFFFF1744), // Neon Red
                Color(0xFFD500F9), // Magenta
                Color(0xFF00E5FF), // Cyan
                Color(0xFF76FF03), // Lime
                Color(0xFFFFD700)  // Gold
            )

            // Orbiting dot particles surrounding the button
            val particleCount = 18
            for (i in 0 until particleCount) {
                val angleRad = Math.toRadians((rotationAngle + i * (360f / particleCount)).toDouble())
                val particleX = centerPoint.x + (radiusX + 12f) * kotlin.math.cos(angleRad).toFloat()
                val particleY = centerPoint.y + (radiusY + 12f) * kotlin.math.sin(angleRad).toFloat()
                val particleColor = rainbowColors[i % rainbowColors.size]

                drawCircle(
                    color = particleColor,
                    radius = (5 + (i % 3) * 2).toFloat(),
                    center = Offset(particleX, particleY)
                )
            }
        }

        // Inner Magic Button with Transparent Glassmorphic Animated Color
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = animatedTransparentColor
            ),
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.5f)),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
        ) {
            Text(
                text = "Magic",
                color = Color.White, // Glowing White Text for glass background
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun HomeScreen(
    userName: String,
    onMagicClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Background Image from app-back.jpg
        Image(
            painter = painterResource(id = R.drawable.app_back),
            contentDescription = "Background Image",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Subtle dark overlay for contrast
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.25f))
        )

        // Content layout: Header + Clock at top, Center Image in middle, Magic button near bottom
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Image, Digital Clock and Welcome Banner
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.header_codex),
                    contentDescription = "Powered By CodeXdev. MyGalaxy",
                    modifier = Modifier.fillMaxWidth(0.85f),
                    contentScale = ContentScale.Fit
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Real-time Digital Clock with Date, Time, and Day
                DigitalClock()

                Spacer(modifier = Modifier.height(8.dp))

                // Welcome Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF00E5FF).copy(alpha = 0.2f))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Welcome, $userName",
                        color = Color(0xFF00E5FF),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Center Image from 1qaaqc40696.jpg
            Image(
                painter = painterResource(id = R.drawable.center_image),
                contentDescription = "Center Image",
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .clip(RoundedCornerShape(20.dp)),
                contentScale = ContentScale.Fit
            )

            // Animated Moving Particle Magic Button near bottom
            AnimatedMagicButton(onClick = onMagicClick)

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun IRCTCWebViewScreen(
    targetUrl: String,
    onBackToHome: () -> Unit,
    onGeolocationRequest: (String, GeolocationPermissions.Callback) -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var currentWebView: WebView? by remember { mutableStateOf(null) }

    BackHandler(enabled = true) {
        if (currentWebView?.canGoBack() == true) {
            currentWebView?.goBack()
        } else {
            onBackToHome()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            setGeolocationEnabled(true)
                            
                            // Desktop User-Agent for Desktop Payment QR and features
                            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false

                            cacheMode = WebSettings.LOAD_DEFAULT
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            allowFileAccess = true
                            allowContentAccess = true
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val url = request?.url?.toString() ?: return false
                                if (url.startsWith("upi://") || 
                                    url.startsWith("intent://") || 
                                    url.startsWith("tel:") || 
                                    url.startsWith("mailto:") ||
                                    url.contains("paytm") ||
                                    url.contains("phonepe") ||
                                    url.contains("gpay")) {
                                    try {
                                        val intent = if (url.startsWith("intent://")) {
                                            Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                                        } else {
                                            Intent(Intent.ACTION_VIEW, url.toUri())
                                        }
                                        context.startActivity(intent)
                                        return true
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                                return false
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onGeolocationPermissionsShowPrompt(
                                origin: String?,
                                callback: GeolocationPermissions.Callback?
                            ) {
                                if (origin != null && callback != null) {
                                    onGeolocationRequest(origin, callback)
                                }
                            }

                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                super.onProgressChanged(view, newProgress)
                                isLoading = newProgress < 100
                            }
                        }

                        currentWebView = this
                        loadUrl(targetUrl)
                    }
                },
                update = { webView ->
                    currentWebView = webView
                }
            )

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}
