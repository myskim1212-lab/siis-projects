$utf8NoBom = New-Object System.Text.UTF8Encoding($False)
$base = "D:\wso2\WSO2-Integration-Studio-8.5.0-win32-x86_64\workspace\SIISDbConnectorTest"
$res = "$base\src\test\resources"

# FILE 22: TC051.json - empty CLOB (null USER_DESC)
$content = @'
{
    "operations": {
        "delete_tb_user_v2": null,
        "insert_tb_user_v2": {
            "data": [
                {
                    "USER_ID": "ID_1",
                    "USER_NAME": "EmptyClob_1",
                    "USER_NICK": "EC1",
                    "USER_CODE": "EC_CODE_1",
                    "USER_DESC": null,
                    "USER_AGE": 20,
                    "USER_COUNT": 1000,
                    "USER_BIGINT": 922337203685477580,
                    "USER_SCORE": 88.1234,
                    "USER_RATE": 0.12345678,
                    "USER_RATIO": 1.2345,
                    "USER_WEIGHT": 75.4321,
                    "CREATED_DATE": "2026-01-01",
                    "UPDATED_TS": "2026-01-01T00:00:00.000",
                    "UPDATED_TZ": "2026-01-01T00:00:00.000+09:00",
                    "UPDATED_LTZ": "2026-01-01T00:00:00.000+09:00",
                    "IS_ACTIVE": "Y",
                    "IS_DELETED": "N",
                    "USER_PROFILE": "ZW1wdHk=",
                    "META_JSON": "{\"clob\":\"empty\"}",
                    "TAGS": "empty,clob",
                    "USER_XML": "<user><clob>empty</clob></user>",
                    "OPTIONAL_COL": "TC051"
                }
            ]
        }
    }
}
'@
[System.IO.File]::WriteAllText("$res\TC051.json", $content, $utf8NoBom)
Write-Host "FILE 22: TC051.json - OK"

# FILE 23: TC052.json - large CLOB 4000 chars
# Generate 4000-char USER_DESC by repeating "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789" (36 chars) then padding
$pattern = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
$sb = New-Object System.Text.StringBuilder
while ($sb.Length -lt 4000) {
    $sb.Append($pattern) | Out-Null
}
$largeDesc = $sb.ToString().Substring(0, 4000)

$tc052 = @"
{
    "operations": {
        "delete_tb_user_v2": null,
        "insert_tb_user_v2": {
            "data": [
                {
                    "USER_ID": "ID_1",
                    "USER_NAME": "LargeClob4000_1",
                    "USER_NICK": "LC4K1",
                    "USER_CODE": "LC4K_CODE_1",
                    "USER_DESC": "$largeDesc",
                    "USER_AGE": 20,
                    "USER_COUNT": 1000,
                    "USER_BIGINT": 922337203685477580,
                    "USER_SCORE": 88.1234,
                    "USER_RATE": 0.12345678,
                    "USER_RATIO": 1.2345,
                    "USER_WEIGHT": 75.4321,
                    "CREATED_DATE": "2026-01-01",
                    "UPDATED_TS": "2026-01-01T00:00:00.000",
                    "UPDATED_TZ": "2026-01-01T00:00:00.000+09:00",
                    "UPDATED_LTZ": "2026-01-01T00:00:00.000+09:00",
                    "IS_ACTIVE": "Y",
                    "IS_DELETED": "N",
                    "USER_PROFILE": "bGFyZ2VfY2xvYg==",
                    "META_JSON": "{\"clob\":\"4000chars\"}",
                    "TAGS": "large,clob,4000",
                    "USER_XML": "<user><clob>4000chars</clob></user>",
                    "OPTIONAL_COL": "TC052"
                }
            ]
        }
    }
}
"@
[System.IO.File]::WriteAllText("$res\TC052.json", $tc052, $utf8NoBom)
Write-Host "FILE 23: TC052.json - OK (USER_DESC length: $($largeDesc.Length))"

# FILE 24: TC053.json - multilingual round-trip
$content = @'
{
    "operations": {
        "delete_tb_user_v2": null,
        "insert_tb_user_v2": {
            "data": [
                {
                    "USER_ID": "ML_99",
                    "USER_NAME": "\u0645\u062D\u0645\u062F_99\u060C \u0623\u062D\u0645\u062F",
                    "USER_NICK": "AR_99",
                    "USER_CODE": "ML_CODE_99",
                    "USER_DESC": "Multilingual Round-Trip:\nArabic: \u0645\u0631\u062D\u0628\u0627\nChinese: \u4E2D\u6587\nKorean: \uD55C\uAD6D\uC5B4\nData: 99",
                    "USER_AGE": 35,
                    "USER_COUNT": 1099,
                    "USER_BIGINT": 922337203685477580,
                    "USER_SCORE": 88.1234,
                    "USER_RATE": 0.12345678,
                    "USER_RATIO": 1.2345,
                    "USER_WEIGHT": 75.4321,
                    "CREATED_DATE": "2026-01-01",
                    "UPDATED_TS": "2026-01-01T00:00:00.000",
                    "UPDATED_TZ": "2026-01-01T00:00:00.000+09:00",
                    "UPDATED_LTZ": "2026-01-01T00:00:00.000+09:00",
                    "IS_ACTIVE": "Y",
                    "IS_DELETED": "N",
                    "USER_PROFILE": "bWxfOTk=",
                    "META_JSON": "{\"roundtrip\":true,\"id\":99}",
                    "TAGS": "multilingual,roundtrip",
                    "USER_XML": "<user><id>ML_99</id><type>multilingual</type></user>",
                    "OPTIONAL_COL": "TC053"
                }
            ]
        }
    }
}
'@
[System.IO.File]::WriteAllText("$res\TC053.json", $content, $utf8NoBom)
Write-Host "FILE 24: TC053.json - OK"

# FILE 25: TC054.json - special chars round-trip
$content = @'
{
    "operations": {
        "delete_tb_user_v2": null,
        "insert_tb_user_v2": {
            "data": [
                {
                    "USER_ID": "HX_99",
                    "USER_NAME": "HTML_User_99 <>&\"'",
                    "USER_NICK": "HX99",
                    "USER_CODE": "HX_CODE_99",
                    "USER_DESC": "HTML/XML Round-Trip:\n<root><child attr=\"val\">text &amp; more</child></root>\nSpecial: <!-- comment --> <![CDATA[data]]>\nData: 99",
                    "USER_AGE": 30,
                    "USER_COUNT": 1099,
                    "USER_BIGINT": 922337203685477580,
                    "USER_SCORE": 88.1234,
                    "USER_RATE": 0.12345678,
                    "USER_RATIO": 1.2345,
                    "USER_WEIGHT": 75.4321,
                    "CREATED_DATE": "2026-01-01",
                    "UPDATED_TS": "2026-01-01T00:00:00.000",
                    "UPDATED_TZ": "2026-01-01T00:00:00.000+09:00",
                    "UPDATED_LTZ": "2026-01-01T00:00:00.000+09:00",
                    "IS_ACTIVE": "Y",
                    "IS_DELETED": "N",
                    "USER_PROFILE": "aHhfOTk=",
                    "META_JSON": "{\"html\":true,\"roundtrip\":99}",
                    "TAGS": "html,xml,roundtrip",
                    "USER_XML": "<user><data><![CDATA[<html>&amp;</html>]]></data></user>",
                    "OPTIONAL_COL": "TC054"
                }
            ]
        }
    }
}
'@
[System.IO.File]::WriteAllText("$res\TC054.json", $content, $utf8NoBom)
Write-Host "FILE 25: TC054.json - OK"

# FILE 26: TC055.json - negative values round-trip
$content = @'
{
    "operations": {
        "delete_tb_user_v2": null,
        "insert_tb_user_v2": {
            "data": [
                {
                    "USER_ID": "NEG_99",
                    "USER_NAME": "Negative_99",
                    "USER_NICK": "NEG99",
                    "USER_CODE": "NEG_CODE_99",
                    "USER_DESC": "Negative Values Round-Trip Test. Data: 99",
                    "USER_AGE": -1,
                    "USER_COUNT": -100,
                    "USER_BIGINT": -9223372036854775807,
                    "USER_SCORE": -88.1234,
                    "USER_RATE": -0.12345678,
                    "USER_RATIO": -1.2345,
                    "USER_WEIGHT": -75.4321,
                    "CREATED_DATE": "2026-01-01",
                    "UPDATED_TS": "2026-01-01T00:00:00.000",
                    "UPDATED_TZ": "2026-01-01T00:00:00.000+09:00",
                    "UPDATED_LTZ": "2026-01-01T00:00:00.000+09:00",
                    "IS_ACTIVE": "Y",
                    "IS_DELETED": "N",
                    "USER_PROFILE": "bmVnXzk5",
                    "META_JSON": "{\"negative\":true,\"id\":99}",
                    "TAGS": "negative,roundtrip",
                    "USER_XML": "<user><neg>-99</neg></user>",
                    "OPTIONAL_COL": "TC055"
                }
            ]
        }
    }
}
'@
[System.IO.File]::WriteAllText("$res\TC055.json", $content, $utf8NoBom)
Write-Host "FILE 26: TC055.json - OK"

# FILE 27: TC056.json - emoji round-trip
$content = @'
{
    "operations": {
        "delete_tb_user_v2": null,
        "insert_tb_user_v2": {
            "data": [
                {
                    "USER_ID": "EM_99",
                    "USER_NAME": "\uD83D\uDE00_Emoji_99",
                    "USER_NICK": "EM99",
                    "USER_CODE": "\uD83C\uDF1F_CODE_99",
                    "USER_DESC": "Emoji Round-Trip:\n\uD83D\uDE00\uD83D\uDE01\uD83D\uDE02\uD83D\uDE03\uD83D\uDE04\n\uD83C\uDF89\uD83C\uDF8A\uD83C\uDF8B\nData: 99",
                    "USER_AGE": 25,
                    "USER_COUNT": 1099,
                    "USER_BIGINT": 922337203685477580,
                    "USER_SCORE": 88.1234,
                    "USER_RATE": 0.12345678,
                    "USER_RATIO": 1.2345,
                    "USER_WEIGHT": 75.4321,
                    "CREATED_DATE": "2026-01-01",
                    "UPDATED_TS": "2026-01-01T00:00:00.000",
                    "UPDATED_TZ": "2026-01-01T00:00:00.000+09:00",
                    "UPDATED_LTZ": "2026-01-01T00:00:00.000+09:00",
                    "IS_ACTIVE": "Y",
                    "IS_DELETED": "N",
                    "USER_PROFILE": "ZW1fOTk=",
                    "META_JSON": "{\"emoji\":true,\"id\":99}",
                    "TAGS": "emoji,roundtrip",
                    "USER_XML": "<user><emoji>\uD83D\uDE00</emoji></user>",
                    "OPTIONAL_COL": "TC056"
                }
            ]
        }
    }
}
'@
[System.IO.File]::WriteAllText("$res\TC056.json", $content, $utf8NoBom)
Write-Host "FILE 27: TC056.json - OK"

# FILE 28: TC057.json - large CLOB round-trip 4000 chars (same pattern)
$tc057 = @"
{
    "operations": {
        "delete_tb_user_v2": null,
        "insert_tb_user_v2": {
            "data": [
                {
                    "USER_ID": "ID_99",
                    "USER_NAME": "LargeClob4000_99",
                    "USER_NICK": "LC4K99",
                    "USER_CODE": "LC4K_CODE_99",
                    "USER_DESC": "$largeDesc",
                    "USER_AGE": 20,
                    "USER_COUNT": 1099,
                    "USER_BIGINT": 922337203685477580,
                    "USER_SCORE": 88.1234,
                    "USER_RATE": 0.12345678,
                    "USER_RATIO": 1.2345,
                    "USER_WEIGHT": 75.4321,
                    "CREATED_DATE": "2026-01-01",
                    "UPDATED_TS": "2026-01-01T00:00:00.000",
                    "UPDATED_TZ": "2026-01-01T00:00:00.000+09:00",
                    "UPDATED_LTZ": "2026-01-01T00:00:00.000+09:00",
                    "IS_ACTIVE": "Y",
                    "IS_DELETED": "N",
                    "USER_PROFILE": "bGFyZ2VfY2xvYg==",
                    "META_JSON": "{\"clob\":\"4000chars\",\"roundtrip\":true}",
                    "TAGS": "large,clob,roundtrip",
                    "USER_XML": "<user><clob>4000chars</clob></user>",
                    "OPTIONAL_COL": "TC057"
                }
            ]
        }
    }
}
"@
[System.IO.File]::WriteAllText("$res\TC057.json", $tc057, $utf8NoBom)
Write-Host "FILE 28: TC057.json - OK"

# FILE 29: TC058.json - empty CLOB round-trip (same as TC051 but TC058)
$content = @'
{
    "operations": {
        "delete_tb_user_v2": null,
        "insert_tb_user_v2": {
            "data": [
                {
                    "USER_ID": "ID_1",
                    "USER_NAME": "EmptyClob_1",
                    "USER_NICK": "EC1",
                    "USER_CODE": "EC_CODE_1",
                    "USER_DESC": null,
                    "USER_AGE": 20,
                    "USER_COUNT": 1000,
                    "USER_BIGINT": 922337203685477580,
                    "USER_SCORE": 88.1234,
                    "USER_RATE": 0.12345678,
                    "USER_RATIO": 1.2345,
                    "USER_WEIGHT": 75.4321,
                    "CREATED_DATE": "2026-01-01",
                    "UPDATED_TS": "2026-01-01T00:00:00.000",
                    "UPDATED_TZ": "2026-01-01T00:00:00.000+09:00",
                    "UPDATED_LTZ": "2026-01-01T00:00:00.000+09:00",
                    "IS_ACTIVE": "Y",
                    "IS_DELETED": "N",
                    "USER_PROFILE": "ZW1wdHk=",
                    "META_JSON": "{\"clob\":\"empty\",\"roundtrip\":true}",
                    "TAGS": "empty,clob,roundtrip",
                    "USER_XML": "<user><clob>empty</clob></user>",
                    "OPTIONAL_COL": "TC058"
                }
            ]
        }
    }
}
'@
[System.IO.File]::WriteAllText("$res\TC058.json", $content, $utf8NoBom)
Write-Host "FILE 29: TC058.json - OK"

Write-Host "--- Files 22-29 complete ---"
