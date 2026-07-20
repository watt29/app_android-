lines = open('dashboard/src/App.jsx', encoding='utf-8').read().split('\n')
new_lines = []
for i, line in enumerate(lines):
    if i+1 == 322 and 'const selectedDevice =' in line:
        continue
    new_lines.append(line)
with open('dashboard/src/App.jsx', 'w', encoding='utf-8') as f:
    f.write('\n'.join(new_lines))
