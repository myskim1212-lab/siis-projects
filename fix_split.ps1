$filePath = "D:\wso2\WSO2-Integration-Studio-8.5.0-win32-x86_64\workspace\SIISDbConnectorTest\src\test\java\siis\jdbc\api\test\functional\FunctionalTestForMssql.java"

# Read all bytes and decode as UTF-8
$encoding = New-Object System.Text.UTF8Encoding($false)
$bytes = [System.IO.File]::ReadAllBytes($filePath)
$content = $encoding.GetString($bytes)

# Detect line ending style
$crlf = $content.Contains("`r`n")
$sep = if ($crlf) { "`r`n" } else { "`n" }

# Split into lines
$lines = $content -split "`r?`n"

$fixedLines = [System.Collections.Generic.List[string]]::new()
$fixedCount = 0

foreach ($line in $lines) {
    # Target line: has // comment text then whitespace then the assertion
    if ($line -match '^(\s*//.*?)\s{2,}(assertThat\(response\.isSuccess\(\)\)\.isTrue\(\);.*)$') {
        $commentPart = $Matches[1]
        $assertPart  = "        " + $Matches[2].TrimStart()
        $fixedLines.Add($commentPart)
        $fixedLines.Add($assertPart)
        $fixedCount++
        Write-Host "Found bad line. Splitting into:"
        Write-Host "  LINE 1: $commentPart"
        Write-Host "  LINE 2: $assertPart"
    } else {
        $fixedLines.Add($line)
    }
}

if ($fixedCount -eq 0) {
    Write-Host "ERROR: Target line not found. No changes made." -ForegroundColor Red
    exit 1
}

$newContent = $fixedLines -join $sep

$outBytes = $encoding.GetBytes($newContent)
[System.IO.File]::WriteAllBytes($filePath, $outBytes)

Write-Host "Done. Fixed $fixedCount line(s) and saved file as UTF-8." -ForegroundColor Green
