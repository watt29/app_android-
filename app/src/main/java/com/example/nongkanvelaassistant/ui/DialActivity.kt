package com.example.nongkanvelaassistant.ui

import android.Manifest
import android.content.pm.PackageManager
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.nongkanvelaassistant.data.TelecomCallController
import com.example.nongkanvelaassistant.theme.NongKanvelaAssistantTheme

class DialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val initialNumber = intent?.data?.schemeSpecificPart.orEmpty()
        setContent {
            NongKanvelaAssistantTheme {
                var number by rememberSaveable { mutableStateOf(initialNumber) }
                // A fresh app launch must always start at the contacts list; do not restore the previous dial-screen state.
                var isShowingContacts by remember { mutableStateOf(initialNumber.isBlank()) }
                var contacts by remember { mutableStateOf(emptyList<DeviceContact>()) }
                var query by rememberSaveable { mutableStateOf("") }
                val requestContactsPermission = androidx.activity.compose.rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        contacts = loadPhoneContacts()
                        isShowingContacts = true
                    }
                }
                val bg = Color(0xFF0B0F19)

                LaunchedEffect(Unit) {
                    if (initialNumber.isBlank() && ContextCompat.checkSelfPermission(this@DialActivity, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                        contacts = loadPhoneContacts()
                    } else if (initialNumber.isBlank()) {
                        requestContactsPermission.launch(Manifest.permission.READ_CONTACTS)
                    }
                }

                if (isShowingContacts) {
                    val visibleContacts = contacts.filter {
                        it.name.contains(query, ignoreCase = true) || it.number.contains(query)
                    }
                    Column(
                        modifier = Modifier.fillMaxSize().background(bg).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("รายชื่อ", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text("ค้นหาชื่อหรือเบอร์") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 20.sp)
                        )
                        if (visibleContacts.isEmpty()) {
                            Text("ไม่พบรายชื่อ", color = Color.White, fontSize = 20.sp, modifier = Modifier.padding(32.dp))
                        } else {
                            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp)) {
                                items(visibleContacts, key = { "${it.contactId}:${it.number}" }) { contact ->
                                    Column(
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            number = contact.number
                                            isShowingContacts = false
                                        }.padding(vertical = 18.dp, horizontal = 12.dp)
                                    ) {
                                        Text(contact.name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                                        Text(contact.number, color = Color(0xFFB7C1D6), fontSize = 18.sp)
                                    }
                                }
                            }
                        }
                        Button(
                            onClick = { isShowingContacts = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                        ) { Text("กลับ", fontSize = 20.sp, color = Color.White) }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize().background(bg).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically)
                    ) {
                        Text("โทรออก", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = number,
                            onValueChange = { number = it },
                            label = { Text("หมายเลขโทรศัพท์") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 26.sp, textAlign = TextAlign.Center)
                        )
                        Button(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(this@DialActivity, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                                    contacts = loadPhoneContacts()
                                    isShowingContacts = true
                                } else {
                                    requestContactsPermission.launch(Manifest.permission.READ_CONTACTS)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                        ) { Text("รายชื่อในโทรศัพท์", fontSize = 22.sp, color = Color.White) }
                        Button(
                            onClick = {
                                if (number.isNotBlank()) TelecomCallController(this@DialActivity).placeNormalCall(number)
                            },
                            modifier = Modifier.size(180.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))
                        ) { Text("โทร", fontSize = 28.sp, color = Color.Black) }
                        Button(
                            onClick = ::finish,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                        ) { Text("กลับ", fontSize = 20.sp, color = Color.White) }
                    }
                }
            }
        }
    }

    private fun loadPhoneContacts(): List<DeviceContact> {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return emptyList()
        val contacts = mutableListOf<DeviceContact>()
        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val rawIdIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID)
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext() && nameIndex >= 0 && numberIndex >= 0) {
                val name = cursor.getString(nameIndex).orEmpty()
                val number = cursor.getString(numberIndex).orEmpty()
                if (name.isNotBlank() && number.isNotBlank()) {
                    contacts += DeviceContact(
                        contactId = if (idIndex >= 0) cursor.getLong(idIndex) else 0L,
                        rawContactId = if (rawIdIndex >= 0) cursor.getLong(rawIdIndex) else 0L,
                        name = name,
                        number = number
                    )
                }
            }
        }
        return contacts
    }
}
