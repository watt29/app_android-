lines = open('dashboard/src/App.jsx', encoding='utf-8').read().split('\n')
# Remove line 215 which is   };
# But let's be safe and just remove line 214-216 if it matches   };
new_lines = []
for i, line in enumerate(lines):
    if i == 214 and line.strip() == '};':
        continue
    new_lines.append(line)

with open('dashboard/src/App.jsx', 'w', encoding='utf-8') as f:
    f.write('\n'.join(new_lines))
