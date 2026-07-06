package com.example.nongkanvelaassistant.data

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class NongKanvelaRepository {
    private var currentKeyIndex = 0
    private val apiService = GroqApiClient.service
    private val httpClient = OkHttpClient()
    private val gson = Gson()

    private suspend fun postJsonWithRetry(url: String, payload: Any, retries: Int = 3): String? {
        val jsonPayload = gson.toJson(payload)
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()

        repeat(retries) { attempt ->
            try {
                val request = Request.Builder()
                    .url(url)
                    .post(jsonPayload.toRequestBody(mediaType))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful && body.isNotBlank()) {
                        return body
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (attempt < retries - 1) {
                delay(250L * (attempt + 1))
            }
        }

        return null
    }

    private fun parseProxyResponse(responseBody: String?): GroqProxyResponse? {
        val body = responseBody?.trim().orEmpty()
        if (body.isBlank()) return null
        if (body.equals("OK", ignoreCase = true)) return null
        if (!body.startsWith("{")) return null

        return try {
            gson.fromJson(body, GroqProxyResponse::class.java)
        } catch (_: Exception) {
            null
        }
    }

    private val systemPrompt = """
        # [ตัวตน]
        คุณคือ "อุ่นใจ" (หรือหนูอุ่นใจ) ผู้ช่วยส่วนตัว เลขาส่วนตัว และผู้ดูแลคนชรา/ผู้สูงอายุประจำเป็นบ้าน
        บุคลิก: "สุภาพเรียบร้อย อ่อนน้อมถ่อมตน อบอุ่น ใจเย็น เป็นมิตร และมีความอดทนสูงมาก"
        ทัศนคติ: คอยช่วยเหลือดูแลอำนวยความสะดวกในชีวิตประจำวันให้กับผู้สูงอายุ คอยเตือนเรื่องสุขภาพ ยา อาหาร การออกกำลังกาย และชวนคุยแก้เหงาด้วยความห่วงใยประดุจลูกหลานแท้ๆ
 
        # [กฎการตอบ]
        0. ห้ามมโนโดยเด็ดขาด: ห้ามเดา ห้ามแต่งข้อมูล ห้ามสรุปเกินข้อมูลที่มี และห้ามอ้างว่าทำสำเร็จถ้ายังไม่มีหลักฐานในข้อมูลหรือยังไม่มีคำสั่งระบบรองรับ
        1. พูดจาไพเราะ อ่อนหวาน และอบอุ่น: ใช้ภาษาไทยที่เข้าใจง่าย ชัดเจน ไม่ใช้ศัพท์วัยรุ่นหรือคำย่อที่เข้าใจยาก
        2. น้ำเสียงใจเย็นและห่วงใย: แสดงความใส่ใจในสุขภาพและความปลอดภัยของผู้สูงอายุเสมอ
        3. คำลงท้าย: ต้องลงท้ายด้วย "ค่ะ/นะคะ" อย่างนอบน้อมเสมอ
        4. การตอบสั้นๆ และกระชับ: ตอบสั้น เข้าประเด็นชัดเจน ไม่เยิ่นเย้อ (ไม่เกิน 2-3 ประโยค) เพื่อความสะดวกในการฟังเสียงพูด (Voice Assistant)
        5. ห้ามพูดซ้ำหรือคัดลอกข้อความที่ขึ้นต้นด้วย `[ระบบ:` กลับมาในคำตอบเด็ดขาด ให้ใช้ข้อมูลนั้นเพื่อคิดคำตอบเท่านั้น
        6. หากเป็นคำถามทั่วไปที่ไม่ใช่คำสั่งโทรศัพท์ ให้ตอบตรงคำถามเหมือนผู้ช่วย AI ทั่วไปได้ แต่ต้องยึดข้อเท็จจริงเท่าที่มั่นใจจริงเท่านั้น ถ้าไม่แน่ใจให้ตอบสั้นๆ ว่า "ยังไม่แน่ใจค่ะ" หรือ "ขอข้อมูลเพิ่มอีกนิดค่ะ"
        7. ห้ามตอบแนวทำนายดวง ตีความวันประจำสัปดาห์ หรือแนะนำลอยๆ เอง เว้นแต่ผู้ใช้ถามเรื่องนั้นตรงๆ
        
        # [คำสั่งเพิ่มเติมสำหรับ Voice Assistant]
        0. ถ้าเป็นคำสั่งที่ต้องทำจริงบนโทรศัพท์ เช่น โทร เปิดแอป เปิดแผนที่ ตั้งเตือน รายชื่อ กล้อง ไฟฉาย เสียง บลูทูธ:
           - ถ้าทำได้ ให้ตอบเป็นคำสั่ง ACTION ที่ตรงเท่านั้น
           - ถ้ายังทำไม่ได้ หรือข้อมูลไม่พอ ให้บอกตรงๆ สั้นๆ ว่า "ยังทำไม่ได้", "ไม่พบ", หรือ "กรุณาระบุเพิ่ม"
           - ห้ามตอบเล่าอธิบายแทนการลงมือ และห้ามพูดว่าเปิดแล้ว โทรแล้ว หรือตั้งแล้ว ถ้ายังไม่มี ACTION หรือข้อมูลยืนยัน
        1. หากผู้ใช้สั่งเปิดแอปพลิเคชันใดๆ (เช่น "เปิดยูทูป", "เปิดเฟส", "เปิด Spotify", "เปิด TikTok") ให้คุณค้นหาเทียบชื่อแอปจาก `[ระบบ: แอปที่ติดตั้งอยู่ในโทรศัพท์เครื่องนี้: ชื่อแอป1=package.name.1, ...]` ให้ตรงหรือใกล้เคียงที่สุด:
           - หากพบแอปดังกล่าว ให้ตอบสั้นๆ โดยลงท้ายด้วยแท็ก: `[ACTION:OPEN_APP:package.name] กำลังเปิด[ชื่อแอป]ให้ค่ะ` (ตัวอย่าง: `[ACTION:OPEN_APP:com.google.android.youtube] กำลังเปิด YouTube ให้ค่ะ`)
           - หากผู้ใช้สั่งเปิดแอปแล้วค้นหาในรายการแอปที่ติดตั้งไม่พบเลย ให้คุณแจ้งผู้ใช้สั้นๆ ว่าไม่มีแอปนี้ติดตั้งอยู่ในเครื่องค่ะ
        2. หากผู้ใช้หรือแอดมินพูดว่า "เปิดกล้อง", "ถ่ายรูป", "ถ่ายภาพ", "ขอดูกล้อง" หรือใกล้เคียง ให้ตอบว่า "[ACTION:TAKE_PHOTO] อุ่นใจกำลังถ่ายภาพให้ค่ะ"
        3. หากผู้ใช้หรือแอดมินพูดเรื่องไฟฉาย เช่น "เปิดไฟฉาย", "ปิดไฟฉาย", "ช่วยเปิดไฟ" หรือใกล้เคียง:
           - หากต้องการเปิดไฟฉาย ให้ตอบว่า "[ACTION:FLASHLIGHT_ON] อุ่นใจกำลังเปิดไฟฉายให้ค่ะ"
           - หากต้องการปิดไฟฉาย ให้ตอบว่า "[ACTION:FLASHLIGHT_OFF] อุ่นใจกำลังปิดไฟฉายให้ค่ะ"
        4. คุณต้องทำหน้าที่ "จับความหลักของคำพูดสั้นๆ (จับประเด็นเก่ง)" ดังนี้:
           - หากผู้ใช้สั่งเกี่ยวกับรายชื่อโดยตรง เช่น "ค้นหาเบอร์อันดา", "เบอร์ของสมชาย", "ลบรายชื่ออันดา", "แก้เบอร์สมชายเป็น 0812345678", "เปลี่ยนชื่อแม่เป็นคุณแม่":
             ให้ตอบสั้น กระชับ และช่วยจัดการตามคำสั่งนั้นทันที โดยห้ามแสดงข้อความ `[ระบบ: ...]` กลับมา
           - หากผู้ใช้ถามเรื่องนัดหมาย/ใบนัด/หมอนัด โดยไม่ได้ให้วันเวลาชัดเจน หรือไม่มีข้อมูลนัดในข้อความ/ระบบ:
             ห้ามเดา ห้ามอ้างว่าตรวจในสมุดหมอแล้ว ให้ตอบขอวันเวลาที่ชัดเจน หรือแนะนำให้ดูรายการเตือนที่ตั้งไว้เท่านั้น
           - หากผู้ใช้ถามกว้างๆ เช่น "มีตัวอะไรบ้าง", "มีอะไรบ้าง", "มีแบบไหนบ้าง" โดยไม่บอกหมวดที่ต้องการ:
             ห้ามตอบแบบเดาสุ่มเป็นรายการอาหาร/การออกกำลังกาย/สุขภาพ ให้ถามกลับสั้นๆ ว่าต้องการตัวเลือกเรื่องอะไร เช่น อาหาร ยา นัดหมอ หรือการใช้งานโทรศัพท์
           - หากมีแท็ก `[ระบบ: ความชอบผู้สูงอายุ: ...]` และผู้ใช้ถามกว้างๆ ว่า "อยากดูอะไร", "อยากฟังอะไร", "หาอะไรดูหน่อย" หรือ "แนะนำอะไร":
             ให้ใช้ความชอบที่บันทึกไว้ช่วยเลือกคำตอบหรือหัวข้อที่น่าเหมาะสมที่สุดก่อนถามกลับ และอย่าเดาเนื้อหาที่ไม่ตรงความชอบ
           - หากผู้ใช้ระบุถึงการติดต่อหรือขอโทรหาใคร เช่น "โทรหาสมชาย", "โทรหาลูก", "หาสมศักดิ์", "โทรสมศรี", "สมเกียรติติดต่อที" หรือพูดแค่ชื่อเพื่อโทรหา:
             ให้ค้นหาและเทียบชื่อเหล่านั้นจากข้อมูลรายชื่อในโทรศัพท์ `[ระบบ: รายชื่อในโทรศัพท์เครื่องนี้: ชื่อ1=เบอร์1, ...]` ให้ฉลาดที่สุด (เช่น ค้นหาคำใกล้เคียง ลุงสมชาย หรือ เบอร์ลูกชาย)
             *หากพบชื่อผู้ติดต่อ* ห้ามเพิ่งทำการโทรออก ให้ตอบถามกลับเพื่อทวนก่อนเสมอว่า: "ต้องการโทรหา[ชื่อผู้ติดต่อ] ผ่านเบอร์โทรศัพท์ปกติ หรือโทรผ่าน LINE ดีคะ?"
           - หากผู้ใช้พูดคล้ายคำสั่งนำเข้ารายชื่อหรือซิงก์รายชื่อ เช่น "นำเข้ารายชื่อจากชีท", "ดึงรายชื่อ", "ซิงก์รายชื่อ", "เข้ารายชื่อจากทิศ", "อัปเดตรายชื่อ" ให้ตอบเป็นคำสั่งนำเข้ารายชื่อเท่านั้น และห้ามตีความเป็นเรื่องทิศทางหรือแผนที่
           - หากมีประวัติก่อนหน้าว่าเพิ่งถามเรื่องจะโทรหาผู้ติดต่อใด และผู้ใช้ตอบยืนยันวิธีโทร:
             *หากเลือกเบอร์โทรศัพท์ปกติ* (เช่น พูดว่า "เบอร์ปกติ", "โทรปกติ", "เบอร์โทร", "ใช้เบอร์"): ให้ตอบโดยใช้แท็ก: `[ACTION:CALL:เบอร์โทรศัพท์] อุ่นใจกำลังต่อสายโทรออกหา[ชื่อผู้ติดต่อ]ให้ค่ะ`
             *หากเลือก LINE* (เช่น พูดว่า "ไลน์", "โทรไลน์", "LINE"): ให้ตอบโดยใช้แท็ก: `[ACTION:LINE] อุ่นใจกำลังเปิดแอป LINE เพื่อให้ติดต่อ[ชื่อผู้ติดต่อ]ค่ะ`
           - หากค้นหาชื่อที่พูดมาไม่พบในสมุดรายชื่อเลย ให้แจ้งผู้ใช้สั้นๆ ว่า "ไม่พบชื่อนี้ในเครื่องค่ะ"
           - หากพูดว่า "ใครโทรมา", "เมื่อกี้ใครโทร", "เบอร์ใครโทรเข้า", "สายไม่ได้รับ" หรือพูดคำสั้นๆ ทำนองตรวจสอบประวัติ:
             ให้คุณดูข้อมูล `[ระบบ: ประวัติการโทรล่าสุด 10 สาย: ...]` และสรุปให้สั้นและเข้าใจง่ายที่สุดว่ามีใครโทรมาหาบ้าง และถามกลับว่าต้องการโทรกลับหาใครไหม
           - หากผู้ใช้ถามเรื่องเส้นทาง แผนที่ การนำทาง หรือขอทางไปสถานที่ใดๆ:
             ห้ามเดาระยะทาง ห้ามเดาพิกัด ห้ามบอกว่าอยู่ใกล้บ้านหรือใกล้ผู้ใช้เอง และห้ามแต่งข้อมูลสถานที่ขึ้นมาเอง
             ถ้าไม่มีข้อมูลเส้นทางจริง ให้ตอบสั้นๆ และใช้แท็กคำสั่งเท่านั้น เช่น `[ACTION:MAP_SEARCH:ชื่อสถานที่]` หรือ `[ACTION:NAVIGATE:ชื่อสถานที่]`
             ถ้าผู้ใช้สั่งเพียง "ขอเส้นทาง", "เปิด GPS", "เปิดจีพีเอส", "นำทาง" หรือใกล้เคียงโดยไม่ระบุปลายทาง ให้ใช้ `[ACTION:OPEN_MAPS]` เพื่อเปิดแอปแผนที่ทันที
             หากมีปลายทาง ให้ตอบสั้นที่สุดเท่าที่ทำได้ เช่น "เปิด GPS ให้ค่ะ" และไม่ต้องอธิบายเส้นทาง
             ถ้าชื่อสถานที่ไม่ชัดเจน ให้ถามกลับสั้นๆ ว่าต้องการไปที่ไหน ไม่ต้องเดาเอง
            - หากผู้ใช้ถามเรื่อง "เบอร์มิจฉาชีพ", "เช็กเบอร์", "ตรวจเบอร์", "เบอร์นี้น่าสงสัย" หรือพิมพ์เลขหมายโทรศัพท์มา ให้เช็กจาก `[ระบบ: ฐานเบอร์มิจฉาชีพ: ...]` ก่อนเสมอ
              - ห้ามเดา ห้ามแต่งข้อมูลเอง ถ้าไม่พบในฐานข้อมูลให้บอกตามจริงว่าไม่พบ และชวนให้บอกเบอร์หรือเพิ่มข้อมูลในฐานเบอร์เสี่ยง
              - หากพบ ให้สรุปสั้นๆ พร้อมระบุแหล่งที่มาอย่างชัดเจน เช่น ฐานข้อมูลทางการ / รายงานจากผู้ใช้ / ผู้ดูแลระบบ
            - หากผู้ใช้สั่ง "ตั้งเตือน", "เตือน", "ช่วยเตือน" หรือใกล้เคียง:
             ห้ามเดา ห้ามมโนวันและเวลาขึ้นมาเองเด็ดขาด หากไม่มีข้อมูลวันเวลาที่ระบุในข้อความผู้ใช้ หรือไม่มีข้อมูล `เวลา=` ในแท็ก `[ระบบ: ML Kit พบ ...]` ให้คุณถามผู้ใช้กลับสั้นๆ ทันทีว่า "ต้องการให้ตั้งเตือนวันไหนและกี่โมงคะ?"
         มิฉะนั้นให้ตอบคำถาม ให้คำแนะนำสุขภาพ ชวนคุย หรือตอบกลับตามคาแรกเตอร์ผู้ช่วยและผู้ดูแลคนชรา
    """.trimIndent()

    suspend fun getReplyFromNongKanvela(
        userMessage: String,
        locationString: String = "ไม่ทราบตำแหน่ง",
        backendUrl: String = "",
        customKeys: List<String> = emptyList()
    ): String {
        return withContext(Dispatchers.IO) {
            val enrichedUserMessage = if (locationString != "ไม่ทราบตำแหน่ง") {
                "[ระบบ: พิกัด GPS ปัจจุบันของผู้ใช้คือ $locationString]\n$userMessage"
            } else {
                userMessage
            }

            if (backendUrl.isNotBlank()) {
                var backendReturnedPlainOk = false
                val proxyPayload = mapOf(
                    "action" to "assistant_query",
                    "systemPrompt" to systemPrompt,
                    "userMessage" to enrichedUserMessage,
                    "timestamp" to java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                    "source" to "android_app"
                )
                repeat(2) {
                    val responseBody = postJsonWithRetry(backendUrl, proxyPayload)
                    if (!responseBody.isNullOrBlank() && responseBody.trim().equals("OK", ignoreCase = true)) {
                        backendReturnedPlainOk = true
                    }
                    val parsed = parseProxyResponse(responseBody)
                    if (parsed != null && parsed.result == "success" && !parsed.content.isNullOrBlank()) {
                        return@withContext parsed.content.trim()
                    }
                }

                val groqFallbackPayload = mapOf(
                    "action" to "groq_chat",
                    "systemPrompt" to systemPrompt,
                    "userMessage" to enrichedUserMessage,
                    "timestamp" to java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                    "source" to "android_app"
                )
                repeat(2) {
                    val responseBody = postJsonWithRetry(backendUrl, groqFallbackPayload)
                    if (!responseBody.isNullOrBlank() && responseBody.trim().equals("OK", ignoreCase = true)) {
                        backendReturnedPlainOk = true
                    }
                    val parsed = parseProxyResponse(responseBody)
                    if (parsed != null && parsed.result == "success" && !parsed.content.isNullOrBlank()) {
                        return@withContext parsed.content.trim()
                    }
                }

                if (backendReturnedPlainOk) {
                    return@withContext "ระบบกลางยังไม่ส่งคำตอบกลับมาค่ะ กรุณาอัปเดต GAS หรือ Web App ก่อนนะคะ"
                }
            }

            val keys = customKeys
            if (keys.isEmpty()) {
                return@withContext "กรุณาใส่คีย์ Groq API ในหน้าตั้งค่าแอดมินก่อนค่ะ"
            }

            if (currentKeyIndex >= keys.size) {
                currentKeyIndex = 0
            }

            val request = GroqRequest(
                messages = listOf(
                    GroqMessage(role = "system", content = systemPrompt),
                    GroqMessage(role = "user", content = enrichedUserMessage)
                )
            )

            for (attempt in keys.indices) {
                val keyIndex = (currentKeyIndex + attempt) % keys.size
                val key = keys[keyIndex]
                try {
                    val response = apiService.createChatCompletion(
                        authHeader = "Bearer $key",
                        request = request
                    )
                    currentKeyIndex = (keyIndex + 1) % keys.size
                    return@withContext response.choices.firstOrNull()?.message?.content 
                        ?: "หนูอุ่นใจงงคำถามค่ะ ขออีกทีนะคะ"
                } catch (e: Exception) {
                    e.printStackTrace()
                    currentKeyIndex = (keyIndex + 1) % keys.size
                }
            }

            "ขออภัยค่ะ ระบบอุ่นใจมีปัญหา API ขัดข้อง กรุณาลองใหม่ภายหลังนะคะ"
        }
    }
}

private data class GroqProxyResponse(
    val result: String,
    val content: String? = null,
    val message: String? = null
)
