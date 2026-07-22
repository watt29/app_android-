package com.example.nongkanvelaassistant.data

object ThaiSpeechText {
    fun expandRanks(text: String): String {
        var value = text
        val ranks = listOf(
            "พ.ต.อ." to "พันตำรวจเอก", "พ.ต.ท." to "พันตำรวจโท", "พ.ต.ต." to "พันตำรวจตรี",
            "ร.ต.อ." to "ร้อยตำรวจเอก", "ร.ต.ท." to "ร้อยตำรวจโท", "ร.ต.ต." to "ร้อยตำรวจตรี",
            "ส.ต.อ." to "สิบตำรวจเอก", "ส.ต.ท." to "สิบตำรวจโท", "ส.ต.ต." to "สิบตำรวจตรี",
            "จ.ส.ต." to "จ่าสิบตำรวจ", "ด.ต." to "ดาบตำรวจ",
            "พล.ต.อ." to "พลตำรวจเอก", "พล.ต.ท." to "พลตำรวจโท", "พล.ต.ต." to "พลตำรวจตรี",
            "พล.อ." to "พลเอก", "พล.ท." to "พลโท", "พล.ต." to "พลตรี",
            "พ.อ." to "พันเอก", "พ.ท." to "พันโท", "พ.ต." to "พันตรี",
            "ร.อ." to "ร้อยเอก", "ร.ท." to "ร้อยโท", "ร.ต." to "ร้อยตรี",
            "น.อ." to "นาวาเอก", "น.ท." to "นาวาโท", "น.ต." to "นาวาตรี"
        )
        ranks.forEach { (short, full) -> value = value.replace(short, full) }
        return value
    }
}
