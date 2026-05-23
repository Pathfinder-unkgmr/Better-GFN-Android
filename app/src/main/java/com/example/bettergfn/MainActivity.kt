package com.example.bettergfn

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.bettergfn.theme.BetterGFNTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var gyroSensor: Sensor? = null
    private var webView: WebView? = null
    
    private var isGyroEnabled = false
    private var gyroSensitivity = 1.0f

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Immersive Fullscreen and Keep Screen On
        window.decorView.keepScreenOn = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        setContent {
            BetterGFNTheme {
                MainScreen()
            }
        }
    }

    @Composable
    fun MainScreen() {
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        var resolution by remember { mutableStateOf("1080p") }
        var fps by remember { mutableStateOf("60") }
        var brightness by remember { mutableStateOf(100f) }
        var saturation by remember { mutableStateOf(100f) }
        var gyroEnabled by remember { mutableStateOf(false) }
        var rumbleEnabled by remember { mutableStateOf(false) }
        var touchStickEnabled by remember { mutableStateOf(false) }

        // Update the native class member when toggle changes
        isGyroEnabled = gyroEnabled

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = false,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.width(300.dp),
                    drawerContainerColor = Color(0xFF1E1E1E),
                    drawerContentColor = Color.White
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text("Better GFN Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.height(16.dp))

                        // Video Settings
                        Text("Video", fontWeight = FontWeight.SemiBold, color = Color.LightGray)
                        Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color.DarkGray)
                        
                        Text("Brightness: ${brightness.toInt()}%")
                        Slider(value = brightness, onValueChange = { 
                            brightness = it
                            updateJSState("brightness", it.toInt().toString())
                        }, valueRange = 50f..200f)
                        
                        Text("Saturation: ${saturation.toInt()}%")
                        Slider(value = saturation, onValueChange = { 
                            saturation = it
                            updateJSState("saturation", it.toInt().toString())
                        }, valueRange = 50f..200f)

                        Spacer(modifier = Modifier.height(16.dp))

                        // Controller Settings
                        Text("Controller", fontWeight = FontWeight.SemiBold, color = Color.LightGray)
                        Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color.DarkGray)
                        
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Enable Hardware Rumble")
                            Switch(checked = rumbleEnabled, onCheckedChange = { 
                                rumbleEnabled = it
                                updateJSState("enableRumble", it.toString())
                            })
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Enable Native Gyroscope")
                            Switch(checked = gyroEnabled, onCheckedChange = { 
                                gyroEnabled = it
                                updateJSState("enableGyro", it.toString())
                                if(it) startGyro() else stopGyro()
                            })
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Enable Touch Stick")
                            Switch(checked = touchStickEnabled, onCheckedChange = { 
                                touchStickEnabled = it
                                updateJSState("enableStick", it.toString())
                            })
                        }
                    }
                }
            }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            webView = this
                            
                            // Enable cookie manager
                            val cookieManager = android.webkit.CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            cookieManager.setAcceptThirdPartyCookies(this, true)

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                javaScriptCanOpenWindowsAutomatically = true
                                mediaPlaybackRequiresUserGesture = false
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                // Spoof Desktop User Agent
                                userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                            }
                            webChromeClient = WebChromeClient()
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    injectScript()
                                }
                            }
                            
                            addJavascriptInterface(NativeBridge(this@MainActivity), "AndroidNative")
                            loadUrl("https://play.geforcenow.com")
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Settings Button Overlay
                Button(
                    onClick = { scope.launch { drawerState.open() } },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xAA000000))
                ) {
                    Text("⚙️", fontSize = 24.sp)
                }
            }
        }
    }

    private fun injectScript() {
        try {
            val script = assets.open("better-gfn-injector.js").bufferedReader().use { it.readText() }
            webView?.evaluateJavascript(script, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateJSState(key: String, value: String) {
        val script = "if(window.BetterGFN) window.BetterGFN.updateState('$key', $value);"
        webView?.evaluateJavascript(script, null)
    }

    private fun updateJSAxes(x: Float, y: Float) {
        val script = "if(window.BetterGFN) window.BetterGFN.updateAxes($x, $y);"
        webView?.evaluateJavascript(script, null)
    }

    private fun startGyro() {
        gyroSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    private fun stopGyro() {
        sensorManager.unregisterListener(this)
        updateJSAxes(0f, 0f)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_GYROSCOPE && isGyroEnabled) {
            // Very simple mapping for gyroscope, can be tuned
            val x = Math.max(-1f, Math.min(1f, event.values[1] * gyroSensitivity))
            val y = Math.max(-1f, Math.min(1f, event.values[0] * gyroSensitivity))
            updateJSAxes(x, y)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    inner class NativeBridge(private val context: Context) {
        @JavascriptInterface
        fun playRumble(intensity: Float, duration: Int) {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (vibrator.hasVibrator()) {
                val scaledAmplitude = (intensity * 255).toInt().coerceIn(1, 255)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(duration.toLong(), scaledAmplitude))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(duration.toLong())
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
    }
}
