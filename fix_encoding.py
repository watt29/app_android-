import sys

def fix_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    new_lines = []
    for line in lines:
        try:
            fixed_line = line.encode('windows-1252').decode('utf-8')
            new_lines.append(fixed_line)
        except Exception:
            new_lines.append(line)
            
    with open(filepath, 'w', encoding='utf-8') as f:
        f.writelines(new_lines)

fix_file('app/src/main/java/com/example/nongkanvelaassistant/MainActivity.kt')
fix_file('app/src/main/java/com/example/nongkanvelaassistant/data/EmergencySensorService.kt')
