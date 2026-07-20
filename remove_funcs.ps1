$content = Get-Content -Raw 'app/src/main/java/com/example/nongkanvelaassistant/MainActivity.kt'
$lines = $content -split '\r?\n'
$newLines = $lines[0..563] + $lines[786..($lines.Length-1)]
Set-Content -Path 'app/src/main/java/com/example/nongkanvelaassistant/MainActivity.kt' -Value ($newLines -join "
") -Encoding UTF8
