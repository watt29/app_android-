with open('dashboard/src/App.jsx', 'r', encoding='utf-8') as f:
    lines = f.readlines()

def replace_line(line_num, old_str, new_str):
    idx = line_num - 1
    lines[idx] = new_str + '\n'

replace_line(242, '', '    const number = prompt("กรุณาระบุเบอร์โทรศัพท์ที่ต้องการให้มือถือโทรออก:", "1669");')
replace_line(243, '', '')
replace_line(248, '', '      if (success) showToast(สั่งโทรออกเบอร์  แล้ว);')
replace_line(249, '', '')
replace_line(258, '', '    const number = prompt("กรุณาระบุเบอร์โทรศัพท์ที่ต้องการให้มือถือส่ง SMS ขอความช่วยเหลือ:", "1669");')
replace_line(259, '', '')
replace_line(260, '', '')
replace_line(264, '', '      if (success) showToast(สั่งส่ง SMS ไปยัง  แล้ว);')
replace_line(265, '', '')
replace_line(276, '', "    showToast('ปิดรับการแจ้งเตือน SOS สำเร็จ');")
replace_line(277, '', '')
replace_line(481, '', '              🚨 การแจ้งเตือนฉุกเฉิน (SOS Alerts)')
replace_line(495, '', '                    <div className="text-red-200 text-xs">ต้องการความช่วยเหลือด่วน!</div>')
replace_line(496, '', '')
replace_line(509, '', '                      ดูข้อมูล')
replace_line(521, '', '                      ปิดรับแจ้ง')
replace_line(583, '', '                placeholder="ค้นหาชื่อ หรือ ID..." ')
replace_line(609, '', '                ทั้งหมด')
replace_line(633, '', '                แบตต่ำ')
replace_line(645, '', '                ออนไลน์')
replace_line(655, '', '              <h3 className="text-xs font-bold text-slate-400 uppercase tracking-wider">รายชื่อ ({filteredDevices.length})</h3>')
replace_line(665, '', "                {viewMode === 'global_map' ? 'ดูข้อมูลเดี่ยว' : '🗺️ แผนที่รวม'}")
replace_line(666, '', '')
replace_line(727, '', "                      {device.status === 'online' ? '🟢 ออนไลน์' : '⚪ ออฟไลน์'} • ID: {device.id.substring(0,4)}")
replace_line(728, '', '')
replace_line(749, '', '                  ไม่พบข้อมูล')
replace_line(865, '', '                          {device.batteryLevel !== undefined && <span className="text-xs text-slate-500 block">🔋 แบตเตอรี่: {device.batteryLevel}%</span>}')
replace_line(901, '', '                <h4 className="font-bold text-slate-800 text-sm mb-2">สรุปสถานะ (Global)</h4>')
replace_line(907, '', "                  <div className=\"flex items-center gap-2\"><span className=\"w-3 h-3 rounded-full bg-emerald-500 inline-block\"></span> ออนไลน์: {devices.filter(d => d.status === 'online' && !d.sosActive && d.batteryLevel >= 20).length}</div>")
replace_line(909, '', "                  <div className=\"flex items-center gap-2\"><span className=\"w-3 h-3 rounded-full bg-yellow-500 inline-block\"></span> แบตต่ำ/ออฟไลน์: {devices.filter(d => (d.status === 'offline' || d.batteryLevel < 20) && !d.sosActive).length}</div>")
replace_line(911, '', '                  <div className="flex items-center gap-2"><span className="w-3 h-3 rounded-full bg-blue-500 inline-block"></span> อัปเดตพิกัดแล้ว: {devices.filter(d => d.location).length}/{devices.length}</div>')
replace_line(925, '', '              <div className="text-lg">เลือกรายชื่อทางซ้ายเพื่อดูข้อมูล</div>')
replace_line(926, '', '')
replace_line(941, '', '                    ข้อมูลของ: <span className="text-indigo-400">{selectedDevice?.name || \'Device\'}</span>')
replace_line(955, '', '                  <p className="text-slate-400 text-sm">อัปเดตข้อมูลแบบเรียลไทม์จากแอปพลิเคชัน</p>')
replace_line(983, '', '                        <h2 className="text-lg font-bold text-slate-100">ตำแหน่งปัจจุบัน (Live Location)</h2>')
replace_line(989, '', '                        <span>🔄</span> อัปเดตพิกัด')
replace_line(1041, '', '                                    <strong>พิกัดล่าสุด</strong><br />')
replace_line(1065, '', '                          <span>กำลังรอข้อมูลตำแหน่งพิกัด...</span>')
replace_line(1097, '', '                        <h2 className="text-md font-extrabold text-red-400">อยู่ระหว่างแจ้งเหตุ SOS ด่วนที่สุด!</h2>')
replace_line(1111, '', '                          📞 สั่งให้โทรกลับหาแอดมินด่วน')
replace_line(1112, '', '')

# clean empty lines resulting from multi-line removal
lines = [l for l in lines if l is not None]

with open('dashboard/src/App.jsx', 'w', encoding='utf-8') as f:
    f.writelines(lines)

print("Line by line replacement done!")
