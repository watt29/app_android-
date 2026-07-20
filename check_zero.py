lines = open('dashboard/src/App.jsx', encoding='utf-8').read().split('\n')
depth = 0
for i, line in enumerate(lines):
    depth += line.count('{') - line.count('}')
    if i > 70 and depth == 0:
        print(f"Depth hits 0 at line {i+1}: {line}")
        break
