s = "เธ เธฒเธฃ"
try:
    # If s was decoded from utf-8 bytes using cp874 (with replace)
    b = s.encode('cp874')
    print("Reversed:", b.decode('utf-8'))
except Exception as e:
    print("Error:", e)
