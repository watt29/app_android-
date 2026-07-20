try:
    text = "เธ เธฒเธฃเน เธˆเน‰เธ‡เน€เธ•เธทเธญเธ™เธ‰เธธเธ เน€เธ‰เธดเธ™"
    b = text.encode('cp874')
    print("Decoded:", b.decode('utf-8'))
except Exception as e:
    print("Error:", e)
