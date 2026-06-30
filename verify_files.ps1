$base = "D:\wso2\WSO2-Integration-Studio-8.5.0-win32-x86_64\workspace\SIISDbConnectorTest"
$files = @(
    "src\main\java\siis\jdbc\api\test\data\TestDataLoader.java",
    "src\test\resources\TC003.json",
    "src\test\resources\TC004.json",
    "src\test\resources\TC005.json",
    "src\test\resources\TC006.json",
    "src\test\resources\TC007.json",
    "src\test\resources\TC008.json",
    "src\test\resources\TC010.json",
    "src\test\resources\TC011.json",
    "src\test\resources\TC013.json",
    "src\test\resources\TC014.json",
    "src\test\resources\TC015.json",
    "src\test\resources\TC016.json",
    "src\test\resources\TC017.json",
    "src\test\resources\TC018.json",
    "src\test\resources\TC045.json",
    "src\test\resources\TC046.json",
    "src\test\resources\TC047.json",
    "src\test\resources\TC048.json",
    "src\test\resources\TC049.json",
    "src\test\resources\TC050.json",
    "src\test\resources\TC051.json",
    "src\test\resources\TC052.json",
    "src\test\resources\TC053.json",
    "src\test\resources\TC054.json",
    "src\test\resources\TC055.json",
    "src\test\resources\TC056.json",
    "src\test\resources\TC057.json",
    "src\test\resources\TC058.json",
    "src\test\resources\mssql\TC051.json",
    "src\test\resources\mssql\TC052.json",
    "src\test\resources\mssql\TC053.json",
    "src\test\resources\mssql\TC054.json",
    "src\test\resources\mssql\TC055.json",
    "src\test\resources\mssql\TC056.json"
)
$ok = 0
$fail = 0
foreach ($f in $files) {
    $path = Join-Path $base $f
    if (Test-Path $path) {
        $size = (Get-Item $path).Length
        Write-Host "OK  [$size bytes]  $f"
        $ok++
    } else {
        Write-Host "MISSING: $f"
        $fail++
    }
}
Write-Host ""
Write-Host "Summary: $ok OK, $fail MISSING"
