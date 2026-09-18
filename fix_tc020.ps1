$path = "D:\wso2\WSO2-Integration-Studio-8.5.0-win32-x86_64\workspace\SIISDbConnectorTest\src\test\java\siis\jdbc\api\test\functional\FunctionalTestForMssql.java"
$enc = [System.Text.Encoding]::UTF8
$lines = [System.IO.File]::ReadAllLines($path, $enc)
$line = $lines[549]
$idx = $line.IndexOf('assertThat')
$comment = '        // 빈 배열 전송 시 성공 응답이어야 하며, affected rows = 0 이어야 함'
$assertion = '        ' + $line.Substring($idx)
$list = New-Object 'System.Collections.Generic.List[string]'
for ($i = 0; $i -lt $lines.Length; $i++) {
    if ($i -eq 549) {
        $list.Add($comment)
        $list.Add($assertion)
    } else {
        $list.Add($lines[$i])
    }
}
[System.IO.File]::WriteAllLines($path, $list, $enc)
Write-Host ('Done. Lines: ' + $list.Count)
