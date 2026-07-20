$content = Get-Content -Raw 'app/src/main/java/com/example/nongkanvelaassistant/data/EmergencySensorService.kt'
$lines = $content -split '\r?\n'
$newLines = $lines[0..673]
Set-Content -Path 'app/src/main/java/com/example/nongkanvelaassistant/data/EmergencySensorService.kt' -Value ($newLines -join "
") -Encoding UTF8
