import re

with open('dashboard/src/App.jsx', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Remove the first function App() { ... } block we accidentally added
# Since it was added at line 42, let's just find and replace it
content = re.sub(r'function App\(\) \{\s*const \[devices, setDevices\].*?const \[viewMode, setViewMode\] = useState\(\'detail\'\); // \'detail\' or \'global_map\'', '', content, flags=re.DOTALL)

# 2. Extract the orphaned block of states and methods
# They start from // Device specific state to const handleToggleFlashlight = async (isOn) => { ... }
orphaned_match = re.search(r'(// Device specific state.*?const handleToggleFlashlight = async \(isOn\) => \{.*?\n  \};\n)', content, flags=re.DOTALL)
if orphaned_match:
    orphaned_code = orphaned_match.group(1)
    # Remove it from its current location
    content = content.replace(orphaned_code, '')
    
    # Inject it right after the real unction App() { and its initial states
    # Find unction App() { and the next few lines up to const [filterType, setFilterType] = useState('all');
    inject_point = re.search(r'(function App\(\) \{.*?const \[filterType, setFilterType\] = useState\(\'all\'\);.*?\n)', content, flags=re.DOTALL)
    if inject_point:
        inject_code = inject_point.group(1)
        # Put the orphaned code right after the inject point
        content = content.replace(inject_code, inject_code + '\n' + orphaned_code + '\n')
    else:
        print("Could not find inject point!")
else:
    print("Could not find orphaned code!")

# Remove the rogue } at line 1386. Oh wait, App.jsx might have an extra } at the end.
# We can just remove the very last } before export default App; if there's an extra one.
# Let's count { and } inside the file? A bit risky. Let's see if there's a stray }; around line 220 that shouldn't be there.
# Look at line 221:   }; which was right after const [filterType, ...]. That's an error from previous replace!
content = re.sub(r'const \[filterType, setFilterType\] = useState\(\'all\'\); // \'all\', \'sos\', \'low_battery\', \'online\'\s*\n\s*\};\s*\n', r"const [filterType, setFilterType] = useState('all'); // 'all', 'sos', 'low_battery', 'online'\n", content, flags=re.DOTALL)

with open('dashboard/src/App.jsx', 'w', encoding='utf-8') as f:
    f.write(content)
print("Done fixing App.jsx")
