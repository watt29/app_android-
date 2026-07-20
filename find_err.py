lines = open('dashboard/src/App.jsx', encoding='utf-8').read().split('\n')
for i, line in enumerate(lines):
    if 'selectedDevice' in line and not 'selectedDeviceId' in line:
        print(f"Line {i+1}: {line}")
