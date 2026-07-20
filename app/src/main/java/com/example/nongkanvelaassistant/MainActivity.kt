package com.example.nongkanvelaassistant

import android.Manifest
import android.content.pm.PackageManager
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.nongkanvelaassistant.ui.VoiceAssistantViewModel
import com.example.nongkanvelaassistant.theme.NongKanvelaAssistantTheme
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.Intent
import android.content.Context
import android.os.BatteryManager
import android.app.role.RoleManager
import com.example.nongkanvelaassistant.data.GroqKeyResolver
import com.example.nongkanvelaassistant.service.EmergencyService
import com.example.nongkanvelaassistant.data.TelecomCallController



class MainActivity : ComponentActivity() {

    private val viewModel: VoiceAssistantViewModel by viewModels()
    private var batteryShutdownReceiver: BroadcastReceiver? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var pendingCallIntent: Intent? = null

    private var wasLowBatteryNotified = false
    private var wasDisconnected = false
    private var disconnectTime: Long = 0L

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (audioGranted) {
            viewModel.startListening()
        } else {
            Toast.makeText(this, "ต้องอนุญาตให้ใช้ไมโครโฟนก่อนนะคะ", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestCallPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val callIntent = pendingCallIntent
        pendingCallIntent = null
        if (granted && callIntent != null) {
            try {
                val number = callIntent.data?.schemeSpecificPart ?: return@registerForActivityResult
                if (!TelecomCallController(this).placeNormalCall(number)) {
                    startActivity(TelecomCallController(this).createDialFallbackIntent(number))
                }
            } catch (e: Exception) {
                Toast.makeText(this, "ไม่สามารถโทรออกจากเครื่องนี้ได้ค่ะ", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "กรุณาอนุญาตสิทธิ์โทรออกเพื่อให้โทรอัตโนมัติค่ะ", Toast.LENGTH_LONG).show()
        }
    }

    private val requestDialerRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val message = if (result.resultCode == RESULT_OK) "ตั้งน้องกาลเวลาเป็นแอปโทรศัพท์หลักแล้วค่ะ" else "ยังไม่ได้ตั้งเป็นแอปโทรศัพท์หลักค่ะ"
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            try {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    OutputStreamWriter(outputStream, "UTF-8").use { writer ->
                        writer.write("\uFEFF") // BOM for Excel UTF-8
                        writer.write("วัน/เวลา,ผู้พูด,ข้อความ,พิกัด\n")
                        val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        
                        viewModel.conversationHistory.value.forEach { log ->
                            val timeStr = df.format(Date(log.timestamp))
                            // Escape quotes for CSV
                            val escapedMessage = log.message.replace("\"", "\"\"")
                            writer.write("${"\""}${timeStr}${"\""},${"\""}${log.sender}${"\""},${"\""}${escapedMessage}${"\""},${"\""}${log.location}${"\""}\n")
                        }
                    }
                }
                Toast.makeText(this, "ดาวน์โหลดประวัติเรียบร้อยแล้ว", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "เกิดข้อผิดพลาดในการบันทึกไฟล์", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        
        setupBatteryAndShutdownMonitor()
        setupNetworkMonitor()
        ContextCompat.startForegroundService(this, Intent(this, EmergencyService::class.java))

        setContent {
            NongKanvelaAssistantTheme {
                val uiState by viewModel.uiState.collectAsState()

                // LaunchedEffect to watch actionIntent changes
                LaunchedEffect(uiState.actionIntent) {
                    uiState.actionIntent?.let { intent ->
                        if (intent.action == Intent.ACTION_CALL && ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                            pendingCallIntent = intent
                            requestCallPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                        } else try {
                            startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity, "ไม่สามารถเปิดแอปที่สั่งการได้ค่ะ", Toast.LENGTH_SHORT).show()
                        }
                        viewModel.clearActionIntent()
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VoiceAssistantScreen(
                        viewModel = viewModel,
                        onMicClick = { checkMicrophonePermissionAndListen() },
                        onRequestDefaultDialer = { requestDefaultDialerRole() }
                    )
                }
            }
        }
    }

    private fun checkMicrophonePermissionAndListen() {
        val hasAudio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (hasAudio) {
            viewModel.startListening()
        } else {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        }
    }

    private fun requestDefaultDialerRole() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) && !roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                requestDialerRoleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER))
            } else {
                Toast.makeText(this, "น้องกาลเวลาเป็นแอปโทรศัพท์หลักอยู่แล้วค่ะ", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupBatteryAndShutdownMonitor() {
        batteryShutdownReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_BATTERY_CHANGED -> {
                        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                        val batteryPct = level * 100 / scale.toFloat()
                        
                        // Notify low battery if below 15% and not already notified
                        if (batteryPct <= 15f && !wasLowBatteryNotified) {
                            viewModel.sendAdminNotification("⚠️ แจ้งเตือน: แบตเตอรี่โทรศัพท์ของอุ่นใจเหลือน้อยมาก (${batteryPct.toInt()}%) กรุณาเสียบสายชาร์จด้วยค่ะ")
                            wasLowBatteryNotified = true
                        } else if (batteryPct > 20f) {
                            wasLowBatteryNotified = false // Reset low battery flag when charged
                        }
                    }
                    Intent.ACTION_SHUTDOWN, "android.intent.action.QUICKBOOT_POWEROFF" -> {
                        viewModel.sendAdminNotification("⚠️ แจ้งเตือนด่วน: เครื่องโทรศัพท์ของอุ่นใจกำลังจะดับ/ปิดเครื่องลงในขณะนี้!", isSync = true)
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_SHUTDOWN)
            addAction("android.intent.action.QUICKBOOT_POWEROFF")
        }
        ContextCompat.registerReceiver(this, batteryShutdownReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    private fun setupNetworkMonitor() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                if (wasDisconnected) {
                    val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val lostTimeStr = df.format(Date(disconnectTime))
                    viewModel.sendAdminNotification("⚠️ แจ้งเตือน: โทรศัพท์ของอุ่นใจกลับมาออนไลน์เชื่อมต่ออินเทอร์เน็ตได้แล้วค่ะ (ก่อนหน้านี้ขาดการเชื่อมต่อนับตั้งแต่เวลา $lostTimeStr)")
                    wasDisconnected = false
                }
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                if (!wasDisconnected) {
                    wasDisconnected = true
                    disconnectTime = System.currentTimeMillis()
                }
            }
        }
        
        try {
            connectivityManager?.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                networkCallback!!
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        batteryShutdownReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        networkCallback?.let {
            try {
                connectivityManager?.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

@Composable
fun VoiceAssistantScreen(
    viewModel: VoiceAssistantViewModel,
    onMicClick: () -> Unit,
    onRequestDefaultDialer: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val groqKeysCsv by viewModel.groqKeysString.collectAsState()
    val emergencySystemEnabled by viewModel.emergencySystemEnabled.collectAsState()
    var showSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var showGroqDialog by rememberSaveable { mutableStateOf(false) }
    var groqKeysDraft by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(showGroqDialog) {
        if (showGroqDialog) {
            groqKeysDraft = groqKeysCsv
        }
    }

    val bgDark = Color(0xFF0B0F19)
    val panelDark = Color(0xFF1E293B)
    val textLight = Color(0xFFF8FAFC)
    val textMuted = Color(0xFF94A3B8)
    val accentNeon = Color(0xFF00D9FF)
    val errorColor = Color(0xFFEF5350)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgDark)
            .padding(horizontal = 38.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Nong Kanvela",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = textLight,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "AI Assistant",
                    fontSize = 15.sp,
                    color = accentNeon,
                    fontWeight = FontWeight.Medium,
                )
            }
            IconButton(
                onClick = { showSettingsDialog = true },
                modifier = Modifier.size(52.dp)
            ) {
                Text("⚙️", fontSize = 28.sp)
            }
        }

        // AI Response Bubble
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = panelDark),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = uiState.aiResponse,
                    fontSize = 18.sp,
                    color = textLight,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 26.sp,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // User Speech Transcription
        Text(
            text = uiState.transcribedText,
            fontSize = 15.sp,
            color = if (uiState.errorMessage != null) errorColor else textMuted,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Microphone Button
        Button(
            onClick = {
                if (uiState.isListening) {
                    viewModel.stopListening()
                } else {
                    onMicClick()
                }
            },
            modifier = Modifier
                .size(80.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (uiState.isListening) errorColor else panelDark
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 8.dp,
                pressedElevation = 2.dp
            ),
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = if (uiState.isListening) "กำลังฟัง" else "พูดกับอุ่นใจ",
                modifier = Modifier.size(46.dp),
                tint = Color.White
            )
        }
        
        Spacer(modifier = Modifier.height(22.dp))

        // Text Input Bar for manual typing
        var textInput by remember { mutableStateOf("") }
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = { 
                    Text(
                        "พิมพ์ถามตรงนี้...",
                        color = textMuted
                    ) 
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentNeon,
                    unfocusedBorderColor = panelDark,
                    focusedContainerColor = panelDark,
                    unfocusedContainerColor = panelDark,
                    focusedTextColor = textLight,
                    unfocusedTextColor = textLight
                )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = {
                    if (textInput.isNotBlank()) {
                        viewModel.processUserText(textInput)
                        textInput = ""
                    }
                },
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accentNeon),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text("ส่ง", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = bgDark)
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            containerColor = panelDark,
            title = { Text("ตั้งค่า", color = textLight) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (emergencySystemEnabled) "⚠️ ระบบฉุกเฉิน: เปิดอยู่" else "⚠️ ระบบฉุกเฉิน: ปิดอยู่",
                        color = if (emergencySystemEnabled) accentNeon else errorColor
                    )
                    Button(
                        onClick = { viewModel.setEmergencySystemEnabled(!emergencySystemEnabled) },
                        colors = ButtonDefaults.buttonColors(containerColor = if (emergencySystemEnabled) errorColor else accentNeon)
                    ) {
                        Text(if (emergencySystemEnabled) "ปิดระบบฉุกเฉิน" else "เปิดระบบฉุกเฉิน", color = bgDark)
                    }
                    Button(
                        onClick = onRequestDefaultDialer,
                        colors = ButtonDefaults.buttonColors(containerColor = accentNeon)
                    ) {
                        Text("ตั้งเป็นแอปโทรศัพท์หลัก", color = bgDark)
                    }
                    TextButton(onClick = { showSettingsDialog = false; showGroqDialog = true }) {
                        Text("ตั้งค่า Groq API", color = accentNeon)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSettingsDialog = false }) { Text("เสร็จสิ้น", color = accentNeon) } }
        )
    }

    if (showGroqDialog) {
        AlertDialog(
            onDismissRequest = { showGroqDialog = false },
            title = { Text("ตั้งค่า Groq API") },
            text = {
                Column {
                    Text(
                        text = "วาง Groq API key ได้ 1 คีย์หรือหลายคีย์ คั่นด้วยเครื่องหมายจุลภาค ระบบจะเก็บแบบเข้ารหัสในเครื่อง",
                        fontSize = 13.sp,
                        color = textMuted
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = groqKeysDraft,
                        onValueChange = { groqKeysDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Groq API key") },
                        placeholder = { Text("gsk_...") },
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setGroqKeys(groqKeysDraft)
                        showGroqDialog = false
                        Toast.makeText(
                            context,
                            "บันทึก Groq API เรียบร้อยแล้ว",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                ) {
                    Text("บันทึก")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGroqDialog = false }) {
                    Text("ยกเลิก")
                }
            }
        )
    }
}
