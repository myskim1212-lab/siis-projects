$utf8NoBom = New-Object System.Text.UTF8Encoding($False)
$base = "D:\wso2\WSO2-Integration-Studio-8.5.0-win32-x86_64\workspace\SIISDbConnectorTest"
$res = "$base\src\test\resources"

# FILE 2: TC003.json
$content = @'
{
    "operations": {
        "delete_tb_user_v2": null,
        "insert_tb_user_v2": {
            "data": [
                {"USER_ID":"ID_1","USER_NAME":"Tester_1","USER_NICK":"NICK_1","USER_CODE":"CODE_1","USER_DESC":"Standard test record 1\nLine2\tTabbed","USER_AGE":20,"USER_COUNT":1000,"USER_BIGINT":922337203685477580,"USER_SCORE":88.1234,"USER_RATE":0.12345678,"USER_RATIO":1.2345,"USER_WEIGHT":75.4321,"CREATED_DATE":"2026-01-01","UPDATED_TS":"2026-01-01T00:00:00.000","UPDATED_TZ":"2026-01-01T00:00:00.000+09:00","UPDATED_LTZ":"2026-01-01T00:00:00.000+09:00","IS_ACTIVE":"Y","IS_DELETED":"N","USER_PROFILE":"QklOQVJZXzE=","META_JSON":"{\"key\":\"val1\"}","TAGS":"tag1,tag2","USER_XML":"<user><id>1</id></user>","OPTIONAL_COL":"TC003"},
                {"USER_ID":"ID_2","USER_NAME":"Tester_2","USER_NICK":"NICK_2","USER_CODE":"CODE_2","USER_DESC":"Standard test record 2\nLine2\tTabbed","USER_AGE":21,"USER_COUNT":1001,"USER_BIGINT":922337203685477581,"USER_SCORE":88.1234,"USER_RATE":0.12345678,"USER_RATIO":1.2345,"USER_WEIGHT":75.4321,"CREATED_DATE":"2026-01-01","UPDATED_TS":"2026-01-01T00:00:00.000","UPDATED_TZ":"2026-01-01T00:00:00.000+09:00","UPDATED_LTZ":"2026-01-01T00:00:00.000+09:00","IS_ACTIVE":"N","IS_DELETED":"N","USER_PROFILE":"QklOQVJZXzI=","META_JSON":"{\"key\":\"val2\"}","TAGS":"tag1,tag2","USER_XML":"<user><id>2</id></user>","OPTIONAL_COL":"TC003"},
                {"USER_ID":"ID_3","USER_NAME":"Tester_3","USER_NICK":"NICK_3","USER_CODE":"CODE_3","USER_DESC":"Standard test record 3\nLine2\tTabbed","USER_AGE":22,"USER_COUNT":1002,"USER_BIGINT":922337203685477582,"USER_SCORE":88.1234,"USER_RATE":0.12345678,"USER_RATIO":1.2345,"USER_WEIGHT":75.4321,"CREATED_DATE":"2026-01-01","UPDATED_TS":"2026-01-01T00:00:00.000","UPDATED_TZ":"2026-01-01T00:00:00.000+09:00","UPDATED_LTZ":"2026-01-01T00:00:00.000+09:00","IS_ACTIVE":"Y","IS_DELETED":"N","USER_PROFILE":"QklOQVJZXzM=","META_JSON":"{\"key\":\"val3\"}","TAGS":"tag1,tag2","USER_XML":"<user><id>3</id></user>","OPTIONAL_COL":"TC003"},
                {"USER_ID":"ID_4","USER_NAME":"Tester_4","USER_NICK":"NICK_4","USER_CODE":"CODE_4","USER_DESC":"Standard test record 4\nLine2\tTabbed","USER_AGE":23,"USER_COUNT":1003,"USER_BIGINT":922337203685477583,"USER_SCORE":88.1234,"USER_RATE":0.12345678,"USER_RATIO":1.2345,"USER_WEIGHT":75.4321,"CREATED_DATE":"2026-01-01","UPDATED_TS":"2026-01-01T00:00:00.000","UPDATED_TZ":"2026-01-01T00:00:00.000+09:00","UPDATED_LTZ":"2026-01-01T00:00:00.000+09:00","IS_ACTIVE":"N","IS_DELETED":"N","USER_PROFILE":"QklOQVJZXzQ=","META_JSON":"{\"key\":\"val4\"}","TAGS":"tag1,tag2","USER_XML":"<user><id>4</id></user>","OPTIONAL_COL":"TC003"},
                {"USER_ID":"ID_5","USER_NAME":"Tester_5","USER_NICK":"NICK_5","USER_CODE":"CODE_5","USER_DESC":"Standard test record 5\nLine2\tTabbed","USER_AGE":24,"USER_COUNT":1004,"USER_BIGINT":922337203685477584,"USER_SCORE":88.1234,"USER_RATE":0.12345678,"USER_RATIO":1.2345,"USER_WEIGHT":75.4321,"CREATED_DATE":"2026-01-01","UPDATED_TS":"2026-01-01T00:00:00.000","UPDATED_TZ":"2026-01-01T00:00:00.000+09:00","UPDATED_LTZ":"2026-01-01T00:00:00.000+09:00","IS_ACTIVE":"Y","IS_DELETED":"N","USER_PROFILE":"QklOQVJZXzU=","META_JSON":"{\"key\":\"val5\"}","TAGS":"tag1,tag2","USER_XML":"<user><id>5</id></user>","OPTIONAL_COL":"TC003"},
                {"USER_ID":"ID_6","USER_NAME":"Tester_6","USER_NICK":"NICK_6","USER_CODE":"CODE_6","USER_DESC":"Standard test record 6\nLine2\tTabbed","USER_AGE":25,"USER_COUNT":1005,"USER_BIGINT":922337203685477585,"USER_SCORE":88.1234,"USER_RATE":0.12345678,"USER_RATIO":1.2345,"USER_WEIGHT":75.4321,"CREATED_DATE":"2026-01-01","UPDATED_TS":"2026-01-01T00:00:00.000","UPDATED_TZ":"2026-01-01T00:00:00.000+09:00","UPDATED_LTZ":"2026-01-01T00:00:00.000+09:00","IS_ACTIVE":"N","IS_DELETED":"N","USER_PROFILE":"QklOQVJZXzY=","META_JSON":"{\"key\":\"val6\"}","TAGS":"tag1,tag2","USER_XML":"<user><id>6</id></user>","OPTIONAL_COL":"TC003"},
                {"USER_ID":"ID_7","USER_NAME":"Tester_7","USER_NICK":"NICK_7","USER_CODE":"CODE_7","USER_DESC":"Standard test record 7\nLine2\tTabbed","USER_AGE":26,"USER_COUNT":1006,"USER_BIGINT":922337203685477586,"USER_SCORE":88.1234,"USER_RATE":0.12345678,"USER_RATIO":1.2345,"USER_WEIGHT":75.4321,"CREATED_DATE":"2026-01-01","UPDATED_TS":"2026-01-01T00:00:00.000","UPDATED_TZ":"2026-01-01T00:00:00.000+09:00","UPDATED_LTZ":"2026-01-01T00:00:00.000+09:00","IS_ACTIVE":"Y","IS_DELETED":"N","USER_PROFILE":"QklOQVJZXzc=","META_JSON":"{\"key\":\"val7\"}","TAGS":"tag1,tag2","USER_XML":"<user><id>7</id></user>","OPTIONAL_COL":"TC003"},
                {"USER_ID":"ID_8","USER_NAME":"Tester_8","USER_NICK":"NICK_8","USER_CODE":"CODE_8","USER_DESC":"Standard test record 8\nLine2\tTabbed","USER_AGE":27,"USER_COUNT":1007,"USER_BIGINT":922337203685477587,"USER_SCORE":88.1234,"USER_RATE":0.12345678,"USER_RATIO":1.2345,"USER_WEIGHT":75.4321,"CREATED_DATE":"2026-01-01","UPDATED_TS":"2026-01-01T00:00:00.000","UPDATED_TZ":"2026-01-01T00:00:00.000+09:00","UPDATED_LTZ":"2026-01-01T00:00:00.000+09:00","IS_ACTIVE":"N","IS_DELETED":"N","USER_PROFILE":"QklOQVJZXzg=","META_JSON":"{\"key\":\"val8\"}","TAGS":"tag1,tag2","USER_XML":"<user><id>8</id></user>","OPTIONAL_COL":"TC003"},
                {"USER_ID":"ID_9","USER_NAME":"Tester_9","USER_NICK":"NICK_9","USER_CODE":"CODE_9","USER_DESC":"Standard test record 9\nLine2\tTabbed","USER_AGE":28,"USER_COUNT":1008,"USER_BIGINT":922337203685477588,"USER_SCORE":88.1234,"USER_RATE":0.12345678,"USER_RATIO":1.2345,"USER_WEIGHT":75.4321,"CREATED_DATE":"2026-01-01","UPDATED_TS":"2026-01-01T00:00:00.000","UPDATED_TZ":"2026-01-01T00:00:00.000+09:00","UPDATED_LTZ":"2026-01-01T00:00:00.000+09:00","IS_ACTIVE":"Y","IS_DELETED":"N","USER_PROFILE":"QklOQVJZXzk=","META_JSON":"{\"key\":\"val9\"}","TAGS":"tag1,tag2","USER_XML":"<user><id>9</id></user>","OPTIONAL_COL":"TC003"},
                {"USER_ID":"ID_10","USER_NAME":"Tester_10","USER_NICK":"NICK_10","USER_CODE":"CODE_10","USER_DESC":"Standard test record 10\nLine2\tTabbed","USER_AGE":29,"USER_COUNT":1009,"USER_BIGINT":922337203685477589,"USER_SCORE":88.1234,"USER_RATE":0.12345678,"USER_RATIO":1.2345,"USER_WEIGHT":75.4321,"CREATED_DATE":"2026-01-01","UPDATED_TS":"2026-01-01T00:00:00.000","UPDATED_TZ":"2026-01-01T00:00:00.000+09:00","UPDATED_LTZ":"2026-01-01T00:00:00.000+09:00","IS_ACTIVE":"N","IS_DELETED":"N","USER_PROFILE":"QklOQVJZXzEw","META_JSON":"{\"key\":\"val10\"}","TAGS":"tag1,tag2","USER_XML":"<user><id>10</id></user>","OPTIONAL_COL":"TC003"}
            ]
        }
    }
}
'@
[System.IO.File]::WriteAllText("$res\TC003.json", $content, $utf8NoBom)
Write-Host "FILE 2: TC003.json - OK"

# FILE 3: TC004.json
$content = @'
{
    "operations": {
        "delete_tb_user_v2": null,
        "insert_tb_user_v2": {
            "data": [
                {
                    "USER_ID": "ID_10",
                    "USER_NAME": "Tester_10",
                    "USER_NICK": "NICK_10",
                    "USER_CODE": "CODE_10",
                    "USER_DESC": "TC004 delete-insert transaction test record",
                    "USER_AGE": 29,
                    "USER_COUNT": 1009,
                    "USER_BIGINT": 922337203685477589,
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
                    "USER_PROFILE": "QklOQVJZXzEw",
                    "META_JSON": "{\"key\":\"val10\"}",
                    "TAGS": "tag1,tag2",
                    "USER_XML": "<user><id>10</id></user>",
                    "OPTIONAL_COL": "TC004"
                }
            ]
        }
    }
}
'@
[System.IO.File]::WriteAllText("$res\TC004.json", $content, $utf8NoBom)
Write-Host "FILE 3: TC004.json - OK"

# FILE 4: TC005.json
$content = @'
{
    "operations": {
        "delete_tb_user_v2": null,
        "insert_tb_user_v2": {
            "data": [
                {
                    "USER_ID": null,
                    "USER_NAME": null,
                    "USER_NICK": "NICK_1",
                    "USER_CODE": "CODE_NULL",
                    "USER_DESC": "TC005 null value test - should fail due to NOT NULL constraint",
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
                    "USER_PROFILE": "QklOQVJZXzE=",
                    "META_JSON": "{\"key\":\"null_test\"}",
                    "TAGS": "null_test",
                    "USER_XML": "<user><id>null</id></user>",
                    "OPTIONAL_COL": "TC005"
                }
            ]
        }
    }
}
'@
[System.IO.File]::WriteAllText("$res\TC005.json", $content, $utf8NoBom)
Write-Host "FILE 4: TC005.json - OK"

# FILE 5: TC006.json
$content = @'
{
    "operations": {
        "delete_tb_user_v2": null,
        "insert_tb_user_v2": {
            "data": [
                {
                    "USER_ID": "ID_1",
                    "USER_NAME": "LargeClob_1",
                    "USER_NICK": "NICK_1",
                    "USER_CODE": "LARGE_CODE_1",
                    "USER_DESC": "LARGE_CLOB_START: Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum. REPEAT_BLOCK_1: The quick brown fox jumps over the lazy dog. Pack my box with five dozen liquor jugs. How valiantly did he jump over the golden fence! REPEAT_BLOCK_2: 가나다라마바사아자차카타파하 한글 유니코드 테스트 블록입니다. 특수문자: !@#$%^&*()_+-=[]{}|;:,.<>? REPEAT_BLOCK_3: The quick brown fox jumps over the lazy dog. Pack my box with five dozen liquor jugs. How valiantly did he jump over the golden fence! REPEAT_BLOCK_4: Lorem ipsum dolor sit amet, consectetur adipiscing elit. REPEAT_BLOCK_5: Sphinx of black quartz, judge my vow. Mr. Jock, TV quiz Ph.D., bags few lynx. REPEAT_END. DATA_ID:1",
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
                    "USER_PROFILE": "AAAAB3NzaC1yc2EAAAABIwAAAQEAklOQVJZXzE=",
                    "META_JSON": "{\"type\":\"large_clob\",\"size\":5000}",
                    "TAGS": "large,clob,test",
                    "USER_XML": "<user><id>1</id><type>large_clob</type></user>",
                    "OPTIONAL_COL": "TC006"
                }
            ]
        }
    }
}
'@
[System.IO.File]::WriteAllText("$res\TC006.json", $content, $utf8NoBom)
Write-Host "FILE 5: TC006.json - OK"

# FILE 6: TC007.json - special chars and emoji
$content = @'
{
    "operations": {
        "delete_tb_user_v2": null,
        "insert_tb_user_v2": {
            "data": [
                {
                    "USER_ID": "ID_1",
                    "USER_NAME": "Special\u2605_User_1",
                    "USER_NICK": "NICK_1",
                    "USER_CODE": "\uD83D\uDD11_\uD55C_1_\u2211_\u03C0_\u03A9_\u2605_\u33D8",
                    "USER_DESC": "--- Special Chars Report ---\nNewline(LF) and Tab(TAB)\tCheck\nEmoji: \uD83D\uDE80, \uD83D\uDCA1, \uD83D\uDEE0\uFE0F, \uD83D\uDCC9\nMath/Tech: \u221A, \u221E, \u00B1, \u2260, \u2286, \u2297\nBrackets: \u300C\u300D, \u300E\u300F, \u3008\u3009, \u201C\u201D, \u2018\u2019\nData: 1",
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
                    "USER_PROFILE": "QklOQVJZXzE=",
                    "META_JSON": "{\"desc\": \"Special \u2605 text\", \"val\": 0}",
                    "TAGS": "java,\u2211,\u03A9,test",
                    "USER_XML": "<user><msg>Hello \uD83D\uDE80</msg><id>1</id></user>",
                    "OPTIONAL_COL": "TC007"
                }
            ]
        }
    }
}
'@
[System.IO.File]::WriteAllText("$res\TC007.json", $content, $utf8NoBom)
Write-Host "FILE 6: TC007.json - OK"

# FILE 7: TC008.json - minimum values
$content = @'
{
    "operations": {
        "delete_tb_user_v2": null,
        "insert_tb_user_v2": {
            "data": [
                {
                    "USER_ID": "ID_1",
                    "USER_NAME": "Min_1",
                    "USER_NICK": "N",
                    "USER_CODE": "A",
                    "USER_DESC": "x",
                    "USER_AGE": 0,
                    "USER_COUNT": 0,
                    "USER_BIGINT": 0,
                    "USER_SCORE": 0.0,
                    "USER_RATE": 0.0,
                    "USER_RATIO": 0.0,
                    "USER_WEIGHT": 0.0,
                    "CREATED_DATE": "2000-01-01",
                    "UPDATED_TS": "2000-01-01T00:00:00.000",
                    "UPDATED_TZ": "2000-01-01T00:00:00.000+00:00",
                    "UPDATED_LTZ": "2000-01-01T00:00:00.000+00:00",
                    "IS_ACTIVE": "Y",
                    "IS_DELETED": "N",
                    "USER_PROFILE": "YQ==",
                    "META_JSON": "{\"min\":true}",
                    "TAGS": "min",
                    "USER_XML": "<u><id>1</id></u>",
                    "OPTIONAL_COL": "TC008"
                }
            ]
        }
    }
}
'@
[System.IO.File]::WriteAllText("$res\TC008.json", $content, $utf8NoBom)
Write-Host "FILE 7: TC008.json - OK"

# FILE 8: TC010.json - zero values
$content = @'
{
    "operations": {
        "delete_tb_user_v2": null,
        "insert_tb_user_v2": {
            "data": [
                {
                    "USER_ID": "ID_1",
                    "USER_NAME": "Zero_1",
                    "USER_NICK": "ZERO",
                    "USER_CODE": "ZERO_CODE",
                    "USER_DESC": "Zero value test record",
                    "USER_AGE": 0,
                    "USER_COUNT": 0,
                    "USER_BIGINT": 0,
                    "USER_SCORE": 0.0,
                    "USER_RATE": 0.0,
                    "USER_RATIO": 0.0,
                    "USER_WEIGHT": 0.0,
                    "CREATED_DATE": "2026-01-01",
                    "UPDATED_TS": "2026-01-01T00:00:00.000",
                    "UPDATED_TZ": "2026-01-01T00:00:00.000+09:00",
                    "UPDATED_LTZ": "2026-01-01T00:00:00.000+09:00",
                    "IS_ACTIVE": "Y",
                    "IS_DELETED": "N",
                    "USER_PROFILE": "MA==",
                    "META_JSON": "{\"zero\":0}",
                    "TAGS": "zero",
                    "USER_XML": "<user><zero>0</zero></user>",
                    "OPTIONAL_COL": "TC010"
                }
            ]
        }
    }
}
'@
[System.IO.File]::WriteAllText("$res\TC010.json", $content, $utf8NoBom)
Write-Host "FILE 8: TC010.json - OK"

# FILE 9: TC011.json - unicode extreme
$content = @'
{
    "operations": {
        "delete_tb_user_v2": null,
        "insert_tb_user_v2": {
            "data": [
                {
                    "USER_ID": "ID_1",
                    "USER_NAME": "\u4E2D\u6587_\uD55C\uAD6D\uC5B4_\u65E5\u672C\u8A9E_\u0410\u0440\u0430\u0431\u0441\u043A\u0438\u0439_1",
                    "USER_NICK": "\uAC00\uB098\uB2E4_1",
                    "USER_CODE": "\uD83C\uDF0D\uD83C\uDF1F\uD83C\uDFC6_1",
                    "USER_DESC": "Unicode Extreme Test:\nCJK: \u4E2D\u6587 \u65E5\u672C\u8A9E \uD55C\uAD6D\uC5B4\nArabic: \u0645\u0631\u062D\u0628\u0627\nRussian: \u041F\u0440\u0438\u0432\u0435\u0442\nEmoji: \uD83D\uDE00\uD83D\uDE01\uD83D\uDE02\nMath: \u03B1\u03B2\u03B3\u03B4\u03B5\nSpecial: \u00E9\u00E0\u00FC\u00F1\u00E7",
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
                    "USER_PROFILE": "dW5pY29kZQ==",
                    "META_JSON": "{\"unicode\":true,\"lang\":\"multi\"}",
                    "TAGS": "unicode,multi,extreme",
                    "USER_XML": "<user><id>1</id><lang>multi</lang></user>",
                    "OPTIONAL_COL": "TC011"
                }
            ]
        }
    }
}
'@
[System.IO.File]::WriteAllText("$res\TC011.json", $content, $utf8NoBom)
Write-Host "FILE 9: TC011.json - OK"

# FILE 10: TC013.json - underflow
$content = @'
{
    "operations": {
        "delete_tb_user_v2": null,
        "insert_tb_user_v2": {
            "data": [
                {
                    "USER_ID": "ID_1",
                    "USER_NAME": "Underflow_1",
                    "USER_NICK": "UNDER",
                    "USER_CODE": "UNDER_CODE",
                    "USER_DESC": "Underflow test - extreme negative values",
                    "USER_AGE": -9999999,
                    "USER_COUNT": -9999999,
                    "USER_BIGINT": -9223372036854775808,
                    "USER_SCORE": -9.99E+125,
                    "USER_RATE": -3.4028235E+38,
                    "USER_RATIO": -3.4028235E+38,
                    "USER_WEIGHT": -1.7976931348623157E+308,
                    "CREATED_DATE": "0001-01-01",
                    "UPDATED_TS": "0001-01-01T00:00:00.000",
                    "UPDATED_TZ": "0001-01-01T00:00:00.000+00:00",
                    "UPDATED_LTZ": "0001-01-01T00:00:00.000+00:00",
                    "IS_ACTIVE": "Y",
                    "IS_DELETED": "N",
                    "USER_PROFILE": "dW5kZXJmbG93",
                    "META_JSON": "{\"underflow\":true}",
                    "TAGS": "underflow,extreme",
                    "USER_XML": "<user><id>under</id></user>",
                    "OPTIONAL_COL": "TC013"
                }
            ]
        }
    }
}
'@
[System.IO.File]::WriteAllText("$res\TC013.json", $content, $utf8NoBom)
Write-Host "FILE 10: TC013.json - OK"

# FILE 11: TC014.json - unicode response structure
$content = @'
{
    "operations": {
        "delete_tb_user_v2": null,
        "insert_tb_user_v2": {
            "data": [
                {
                    "USER_ID": "ID_1",
                    "USER_NAME": "\u4E2D\u6587_\uD55C\uAD6D\uC5B4_\u65E5\u672C\u8A9E_1",
                    "USER_NICK": "\uAC00\uB098\uB2E4_1",
                    "USER_CODE": "\uD83C\uDF0D\uD83C\uDF1F_1",
                    "USER_DESC": "TC014 Response Structure Test:\nUnicode: \u4E2D\u6587 \uD55C\uAD6D\uC5B4\nEmoji: \uD83D\uDE00\uD83D\uDE01",
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
                    "USER_PROFILE": "dW5pY29kZQ==",
                    "META_JSON": "{\"test\":\"response_structure\"}",
                    "TAGS": "unicode,structure",
                    "USER_XML": "<user><id>1</id></user>",
                    "OPTIONAL_COL": "TC014"
                }
            ]
        }
    }
}
'@
[System.IO.File]::WriteAllText("$res\TC014.json", $content, $utf8NoBom)
Write-Host "FILE 11: TC014.json - OK"

Write-Host "--- Files 2-11 complete ---"
