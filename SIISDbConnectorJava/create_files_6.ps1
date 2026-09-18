$utf8NoBom = New-Object System.Text.UTF8Encoding($False)
$base = "D:\wso2\WSO2-Integration-Studio-8.5.0-win32-x86_64\workspace\SIISDbConnectorTest"
$mssql = "$base\src\test\resources\mssql"

# Generate 4000-char string for large CLOB
$pattern = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
$sb = New-Object System.Text.StringBuilder
while ($sb.Length -lt 4000) {
    $sb.Append($pattern) | Out-Null
}
$largeDesc = $sb.ToString().Substring(0, 4000)

# mssql/TC051.json - MSSQL multilingual round-trip
$content = @'
{
    "operations": {
        "delete_tb_user_v2": null,
        "insert_tb_user_v2": {
            "data": [
                {
                    "USER_ID": "ML_99",
                    "USER_NAME": "_99_Mohamed_Ahmed",
                    "USER_NICK": "ML99",
                    "USER_CODE": "ML_CODE_99",
                    "USER_DESC": "MSSQL Multilingual Round-Trip Test. Data: 99",
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
                    "META_JSON": "{\"mssql\":true,\"id\":99}",
                    "TAGS": "mssql,multilingual",
                    "USER_XML": "<user><id>ML_99</id></user>",
                    "OPTIONAL_COL": "TC051_MSSQL"
                }
            ]
        }
    }
}
'@
[System.IO.File]::WriteAllText("$mssql\TC051.json", $content, $utf8NoBom)
Write-Host "mssql/TC051.json - OK"

# mssql/TC052.json - MSSQL HTML/XML round-trip (similar to TC054.json)
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
                    "USER_DESC": "MSSQL HTML/XML Round-Trip:\n<root><child attr=\"val\">text &amp; more</child></root>\nSpecial: <!-- comment --> <![CDATA[data]]>\nData: 99",
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
                    "META_JSON": "{\"html\":true,\"mssql\":true,\"roundtrip\":99}",
                    "TAGS": "html,xml,mssql,roundtrip",
                    "USER_XML": "<user><data><![CDATA[<html>&amp;</html>]]></data></user>",
                    "OPTIONAL_COL": "TC052_MSSQL"
                }
            ]
        }
    }
}
'@
[System.IO.File]::WriteAllText("$mssql\TC052.json", $content, $utf8NoBom)
Write-Host "mssql/TC052.json - OK"

# mssql/TC053.json - MSSQL negative values round-trip (similar to TC055.json)
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
                    "USER_DESC": "MSSQL Negative Values Round-Trip Test. Data: 99",
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
                    "META_JSON": "{\"negative\":true,\"mssql\":true,\"id\":99}",
                    "TAGS": "negative,mssql,roundtrip",
                    "USER_XML": "<user><neg>-99</neg></user>",
                    "OPTIONAL_COL": "TC053_MSSQL"
                }
            ]
        }
    }
}
'@
[System.IO.File]::WriteAllText("$mssql\TC053.json", $content, $utf8NoBom)
Write-Host "mssql/TC053.json - OK"

# mssql/TC054.json - MSSQL emoji round-trip (ASCII only for MSSQL VARCHAR)
$content = @'
{
    "operations": {
        "delete_tb_user_v2": null,
        "insert_tb_user_v2": {
            "data": [
                {
                    "USER_ID": "EM_99",
                    "USER_NAME": "Emoji_ASCII_99",
                    "USER_NICK": "EM99",
                    "USER_CODE": "EM_CODE_99",
                    "USER_DESC": "MSSQL Emoji Round-Trip (NVARCHAR CLOB):\nData: 99",
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
                    "META_JSON": "{\"mssql\":true,\"emoji\":true}",
                    "TAGS": "mssql,emoji",
                    "USER_XML": "<user><id>EM_99</id></user>",
                    "OPTIONAL_COL": "TC054_MSSQL"
                }
            ]
        }
    }
}
'@
[System.IO.File]::WriteAllText("$mssql\TC054.json", $content, $utf8NoBom)
Write-Host "mssql/TC054.json - OK"

# mssql/TC055.json - MSSQL large CLOB round-trip (4000 chars)
$tc055mssql = @"
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
                    "META_JSON": "{\"clob\":\"4000chars\",\"mssql\":true}",
                    "TAGS": "mssql,large,clob",
                    "USER_XML": "<user><clob>4000chars</clob></user>",
                    "OPTIONAL_COL": "TC055_MSSQL"
                }
            ]
        }
    }
}
"@
[System.IO.File]::WriteAllText("$mssql\TC055.json", $tc055mssql, $utf8NoBom)
Write-Host "mssql/TC055.json - OK"

# mssql/TC056.json - MSSQL null CLOB round-trip
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
                    "META_JSON": "{\"clob\":\"empty\",\"mssql\":true}",
                    "TAGS": "empty,clob,mssql",
                    "USER_XML": "<user><clob>empty</clob></user>",
                    "OPTIONAL_COL": "TC056_MSSQL"
                }
            ]
        }
    }
}
'@
[System.IO.File]::WriteAllText("$mssql\TC056.json", $content, $utf8NoBom)
Write-Host "mssql/TC056.json - OK"

Write-Host "--- mssql files complete ---"
