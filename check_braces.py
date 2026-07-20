lines = open('dashboard/src/App.jsx', encoding='utf-8').read().split('\n')
depth = 0
for i, line in enumerate(lines):
    depth += line.count('{') - line.count('}')
    if 'function App' in line:
        print(f"Line {i+1}: {line} (Depth: {depth})")
    if depth < 0:
        print(f"Negative depth at line {i+1}: {line} (Depth: {depth})")
        break
    if 'return (' in line and i > 400:
        print(f"Line {i+1}: {line} (Depth: {depth})")
