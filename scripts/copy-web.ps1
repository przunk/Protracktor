$sourceDir = "D:\Projects\Protracktor\dist"
$destPath = "\\192.168.1.141\Udostepnione\protracktor-web.tar.gz"

$latestFile = Get-ChildItem -Path $sourceDir -Filter "*.tar.gz" | Sort-Object LastWriteTime -Descending | Select-Object -First 1

if ($latestFile) {
    Copy-Item -Path $latestFile.FullName -Destination $destPath -Force
    Write-Host "Skopiowano najnowszy plik: $($latestFile.Name) -> $destPath"
} else {
    Write-Error "Brak plików .tar.gz w katalogu źródłowym."
    exit 1
}