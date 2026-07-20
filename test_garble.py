garbled = 'เธ เธณเธฅเธฑเธ‡เธชเน เธ•เธ™เธ”เนŒเธšเธฒเธขเธ•เธฃเธงเธˆเธˆเธฑเธšเน€เธซเธ•เธธเธฅเน‰เธกเธ‰เธธเธ เน€เธ‰เธดเธ™เธ•เธฅเธญเธ” 24 เธŠเธฑเนˆเธงเน‚เธกเธ‡'
try:
    fixed = garbled.encode('windows-1252').decode('utf-8')
    print('windows-1252 to utf-8:', fixed)
except Exception as e:
    print('error 1252:', e)

try:
    fixed = garbled.encode('cp874').decode('utf-8')
    print('cp874 to utf-8:', fixed)
except Exception as e:
    print('error 874:', e)
