$content = Get-Content -Raw 'app/src/main/java/com/example/nongkanvelaassistant/data/EmergencySensorService.kt'
$content = $content.TrimEnd()
if ($content.EndsWith('}')) {
    $content = $content.Substring(0, $content.Length - 1)
}
$newFuncs = Get-Content -Raw 'C:\Users\Lenovo\.gemini\antigravity\brain\5da18c15-838c-45d8-80bb-1d95e9352d14\scratch\temp_service.txt'
$final = $content + "
" + $newFuncs + "
}"
Set-Content -Path 'app/src/main/java/com/example/nongkanvelaassistant/data/EmergencySensorService.kt' -Value $final -Encoding UTF8
