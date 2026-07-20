with open('dashboard/src/App.jsx', 'rb') as f:
    b = f.read()
import re
# find SOS
idx = b.find(b'SOS')
print(b[idx-50:idx+50])
