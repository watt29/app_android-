package com.example.nongkanvelaassistant.ui

import android.os.Bundle
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nongkanvelaassistant.service.CallSessionManager
import com.example.nongkanvelaassistant.theme.NongKanvelaAssistantTheme

class CallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setContent {
            NongKanvelaAssistantTheme {
                val state by CallSessionManager.uiState.collectAsStateWithLifecycle()
                if (!state.isVisible) finish()
                val bg = Color(0xFF0B0F19)
                Column(
                    modifier = Modifier.fillMaxSize().background(bg).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text(if (state.isIncoming) "สายเข้า" else if (state.isActive) "กำลังสนทนา" else "กำลังโทร", color = Color(0xFF00D9FF), fontSize = 22.sp)
                    Text(state.displayName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 34.sp, textAlign = TextAlign.Center)
                    if (state.isIncoming && state.isUnknownCaller) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF5B4300))
                        ) {
                            Row(
                                modifier = Modifier.padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = "สายไม่อยู่ในรายชื่อ", tint = Color(0xFFFACC15), modifier = Modifier.size(42.dp))
                                Column(modifier = Modifier.padding(start = 14.dp)) {
                                    Text("ไม่อยู่ในรายชื่อ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                    Text("หากไม่แน่ใจ ไม่ต้องรับสาย", color = Color.White, fontSize = 17.sp)
                                }
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Button(onClick = CallSessionManager::toggleMute, modifier = Modifier.size(112.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))) {
                                Icon(if (state.isMuted) Icons.Default.MicOff else Icons.Default.Mic, contentDescription = if (state.isMuted) "เปิดไมโครโฟน" else "ปิดไมโครโฟน", modifier = Modifier.size(48.dp))
                            }
                            Text(if (state.isMuted) "เปิดไมค์" else "ปิดไมค์", color = Color.White, fontSize = 18.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Button(onClick = CallSessionManager::toggleSpeaker, modifier = Modifier.size(112.dp), colors = ButtonDefaults.buttonColors(containerColor = if (state.isSpeakerOn) Color(0xFF00D9FF) else Color(0xFF334155))) {
                                Icon(Icons.Default.VolumeUp, contentDescription = "ลำโพง", modifier = Modifier.size(48.dp), tint = if (state.isSpeakerOn) bg else Color.White)
                            }
                            Text("ลำโพง", color = Color.White, fontSize = 18.sp)
                        }
                    }
                    if (state.isIncoming) Button(onClick = CallSessionManager::answer, modifier = Modifier.size(160.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))) { Icon(Icons.Default.Call, contentDescription = "รับสาย", modifier = Modifier.size(70.dp), tint = Color.White) }
                    Button(onClick = CallSessionManager::disconnect, modifier = Modifier.size(160.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))) { Icon(Icons.Default.CallEnd, contentDescription = "วางสาย", modifier = Modifier.size(70.dp), tint = Color.White) }
                }
            }
        }
    }
}
