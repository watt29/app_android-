s = "เธ เธฒเธฃ"
try:
    print("Latin1 -> UTF-8:", s.encode('latin1').decode('utf-8'))
except Exception as e:
    print("Latin1 err:", e)

# What if it's CP1252 but with 'replace' or 'ignore'? 
# Or what if powershell interpreted utf8 bytes as TIS-620?
