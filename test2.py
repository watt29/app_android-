s = "เธ เธฒเธฃ"
try:
    print("CP874 -> UTF-8:", s.encode('cp874').decode('utf-8'))
except Exception as e:
    print("CP874 err:", e)
try:
    print("CP1252 -> UTF-8:", s.encode('cp1252').decode('utf-8'))
except Exception as e:
    print("CP1252 err:", e)
