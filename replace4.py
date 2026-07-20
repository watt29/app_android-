with open('dashboard/src/App.jsx', 'r', encoding='utf-8') as f:
    lines = f.readlines()

lines[223] = "    if (success) showToast('สั่งเร่งเสียงลำโพงดังสุดแล้ว');\n"
lines[231] = "    showToast('กำลังขอข้อมูลแบตเตอรี่...');\n"

with open('dashboard/src/App.jsx', 'w', encoding='utf-8') as f:
    f.writelines(lines)
