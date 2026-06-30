$utf8NoBom = New-Object System.Text.UTF8Encoding($False)
$base = "D:\wso2\WSO2-Integration-Studio-8.5.0-win32-x86_64\workspace\SIISDbConnectorTest"
$res = "$base\src\test\resources"

# FILE 15: TC018.json - continue on error (same as TC017 but TC018/TC018_ERROR)
$content = @'
{
    "operations": {
        "delete_tb_user_v2_m1": null,
        "delete_tb_user_v2_m2": null,
        "delete_tb_user_v2_m3": null,
        "insert_tb_user_v2_m1": {
            "data": [
                {"USER_ID":"ID_1","USER_NAME":"Stop1_1","USER_NICK":"SN1","USER_CODE":"SC1","USER_DESC":"Continue on error test - m1 record 1","USER_AGE":20,"USER_COUNT":1000,"USER_BIGINT":922337203685477580,"USER_SCORE":88.12,"USER_RATE":0.123,"USER_RATIO":1.23,"USER_WEIGHT":75.43,"CREATED_DATE":"2026-01-01","UPDATED_TS":"2026-01-01T00:00:00.000","UPDATED_TZ":"2026-01-01T00:00:00.000+09:00","UPDATED_LTZ":"2026-01-01T00:00:00.000+09:00","IS_ACTIVE":"Y","IS_DELETED":"N","USER_PROFILE":"QklOQVJZXzE=","META_JSON":"{\"k\":1}","TAGS":"stop","USER_XML":"<u><id>1</id></u>","OPTIONAL_COL":"TC018"},
                {"USER_ID":"ID_2","USER_NAME":"Stop1_2","USER_NICK":"SN2","USER_CODE":"SC2","USER_DESC":"Continue on error test - m1 record 2","USER_AGE":21,"USER_COUNT":1001,"USER_BIGINT":922337203685477581,"USER_SCORE":88.12,"USER_RATE":0.123,"USER_RATIO":1.23,"USER_WEIGHT":75.43,"CREATED_DATE":"2026-01-01","UPDATED_TS":"2026-01-01T00:00:00.000","UPDATED_TZ":"2026-01-01T00:00:00.000+09:00","UPDATED_LTZ":"2026-01-01T00:00:00.000+09:00","IS_ACTIVE":"N","IS_DELETED":"N","USER_PROFILE":"QklOQVJZXzI=","META_JSON":"{\"k\":2}","TAGS":"stop","USER_XML":"<u><id>2</id></u>","OPTIONAL_COL":"TC018"},
                {"USER_ID":"ID_3","USER_NAME":"Stop1_3","USER_NICK":"SN3","USER_CODE":"SC3","USER_DESC":"Continue on error test - m1 record 3","USER_AGE":22,"USER_COUNT":1002,"USER_BIGINT":922337203685477582,"USER_SCORE":88.12,"USER_RATE":0.123,"USER_RATIO":1.23,"USER_WEIGHT":75.43,"CREATED_DATE":"2026-01-01","UPDATED_TS":"2026-01-01T00:00:00.000","UPDATED_TZ":"2026-01-01T00:00:00.000+09:00","UPDATED_LTZ":"2026-01-01T00:00:00.000+09:00","IS_ACTIVE":"Y","IS_DELETED":"N","USER_PROFILE":"QklOQVJZXzM=","META_JSON":"{\"k\":3}","TAGS":"stop","USER_XML":"<u><id>3</id></u>","OPTIONAL_COL":"TC018"}
            ]
        },
        "insert_tb_user_v2_m2": {
            "data": [
                {
                    "USER_ID": null,
                    "USER_NAME": null,
                    "USER_NICK": "NICK_1",
                    "USER_CODE": "ERROR_CODE",
                    "USER_DESC": "TC018 error record - should fail due to NOT NULL constraint",
                    "USER_AGE": 20,
                    "USER_COUNT": 1000,
                    "USER_BIGINT": 922337203685477580,
                    "USER_SCORE": 88.12,
                    "USER_RATE": 0.123,
                    "USER_RATIO": 1.23,
                    "USER_WEIGHT": 75.43,
                    "CREATED_DATE": "2026-01-01",
                    "UPDATED_TS": "2026-01-01T00:00:00.000",
                    "UPDATED_TZ": "2026-01-01T00:00:00.000+09:00",
                    "UPDATED_LTZ": "2026-01-01T00:00:00.000+09:00",
                    "IS_ACTIVE": "Y",
                    "IS_DELETED": "N",
                    "USER_PROFILE": "ZXJyb3I=",
                    "META_JSON": "{\"error\":true}",
                    "TAGS": "error",
                    "USER_XML": "<u><error>true</error></u>",
                    "OPTIONAL_COL": "TC018_ERROR"
                }
            ]
        },
        "insert_tb_user_v2_m3": {
            "data": [
                {"USER_ID":"ID_1","USER_NAME":"Stop3_1","USER_NICK":"SN1","USER_CODE":"SC1","USER_DESC":"Continue on error test - m3 record 1","USER_AGE":20,"USER_COUNT":1000,"USER_BIGINT":922337203685477580,"USER_SCORE":88.12,"USER_RATE":0.123,"USER_RATIO":1.23,"USER_WEIGHT":75.43,"CREATED_DATE":"2026-01-01","UPDATED_TS":"2026-01-01T00:00:00.000","UPDATED_TZ":"2026-01-01T00:00:00.000+09:00","UPDATED_LTZ":"2026-01-01T00:00:00.000+09:00","IS_ACTIVE":"Y","IS_DELETED":"N","USER_PROFILE":"QklOQVJZXzE=","META_JSON":"{\"k\":1}","TAGS":"stop","USER_XML":"<u><id>1</id></u>","OPTIONAL_COL":"TC018"},
                {"USER_ID":"ID_2","USER_NAME":"Stop3_2","USER_NICK":"SN2","USER_CODE":"SC2","USER_DESC":"Continue on error test - m3 record 2","USER_AGE":21,"USER_COUNT":1001,"USER_BIGINT":922337203685477581,"USER_SCORE":88.12,"USER_RATE":0.123,"USER_RATIO":1.23,"USER_WEIGHT":75.43,"CREATED_DATE":"2026-01-01","UPDATED_TS":"2026-01-01T00:00:00.000","UPDATED_TZ":"2026-01-01T00:00:00.000+09:00","UPDATED_LTZ":"2026-01-01T00:00:00.000+09:00","IS_ACTIVE":"N","IS_DELETED":"N","USER_PROFILE":"QklOQVJZXzI=","META_JSON":"{\"k\":2}","TAGS":"stop","USER_XML":"<u><id>2</id></u>","OPTIONAL_COL":"TC018"},
                {"USER_ID":"ID_3","USER_NAME":"Stop3_3","USER_NICK":"SN3","USER_CODE":"SC3","USER_DESC":"Continue on error test - m3 record 3","USER_AGE":22,"USER_COUNT":1002,"USER_BIGINT":922337203685477582,"USER_SCORE":88.12,"USER_RATE":0.123,"USER_RATIO":1.23,"USER_WEIGHT":75.43,"CREATED_DATE":"2026-01-01","UPDATED_TS":"2026-01-01T00:00:00.000","UPDATED_TZ":"2026-01-01T00:00:00.000+09:00","UPDATED_LTZ":"2026-01-01T00:00:00.000+09:00","IS_ACTIVE":"Y","IS_DELETED":"N","USER_PROFILE":"QklOQVJZXzM=","META_JSON":"{\"k\":3}","TAGS":"stop","USER_XML":"<u><id>3</id></u>","OPTIONAL_COL":"TC018"}
            ]
        }
    }
}
'@
[System.IO.File]::WriteAllText("$res\TC018.json", $content, $utf8NoBom)
Write-Host "FILE 15: TC018.json - OK"

# FILE 16: TC045.json - multilingual 3 records
$content = @'
{
    "operations": {
        "delete_tb_user_v2": null,
        "insert_tb_user_v2": {
            "data": [
                {"USER_ID":"ML_1","USER_NAME":"\u0645\u062D\u0645\u062F_1\u060C \u0623\u062D\u0645\u062F","USER_NICK":"AR_1","USER_CODE":"ML_CODE_1","USER_DESC":"Arabic: \u0645\u0631\u062D\u0628\u0627 \u0627\u0644\u0639\u0627\u0644\u0645\nChinese: \u4E16\u754C\u4E2D\u6587\nKorean: \uC548\uB155\uD558\uC138\uC694\nJapanese: \u3053\u3093\u306B\u3061\u306F","USER_AGE":25,"USER_COUNT":1000,"USER_BIGINT":922337203685477580,"USER_SCORE":88.12,"USER_RATE":0.123,"USER_RATIO":1.23,"USER_WEIGHT":75.43,"CREATED_DATE":"2026-01-01","UPDATED_TS":"2026-01-01T00:00:00.000","UPDATED_TZ":"2026-01-01T00:00:00.000+09:00","UPDATED_LTZ":"2026-01-01T00:00:00.000+09:00","IS_ACTIVE":"Y","IS_DELETED":"N","USER_PROFILE":"bWxfdGVzdA==","META_JSON":"{\"lang\":\"ar\"}","TAGS":"multilingual,arabic","USER_XML":"<user><lang>ar</lang></user>","OPTIONAL_COL":"TC045"},
                {"USER_ID":"ML_2","USER_NAME":"\u4E2D\u6587\u7528\u6237_2","USER_NICK":"ZH_2","USER_CODE":"ML_CODE_2","USER_DESC":"Chinese: \u4E2D\u6587\u6D4B\u8BD5\nKorean: \uD55C\uAD6D\uC5B4 \uD14C\uC2A4\uD2B8\nJapanese: \u65E5\u672C\u8A9E\u30C6\u30B9\u30C8","USER_AGE":30,"USER_COUNT":2000,"USER_BIGINT":922337203685477581,"USER_SCORE":77.77,"USER_RATE":0.456,"USER_RATIO":2.34,"USER_WEIGHT":60.0,"CREATED_DATE":"2026-01-01","UPDATED_TS":"2026-01-01T00:00:00.000","UPDATED_TZ":"2026-01-01T00:00:00.000+09:00","UPDATED_LTZ":"2026-01-01T00:00:00.000+09:00","IS_ACTIVE":"N","IS_DELETED":"N","USER_PROFILE":"bWxfdGVzdA==","META_JSON":"{\"lang\":\"zh\"}","TAGS":"multilingual,chinese","USER_XML":"<user><lang>zh</lang></user>","OPTIONAL_COL":"TC045"},
                {"USER_ID":"ML_3","USER_NAME":"\uD55C\uAD6D\uC5B4_\uC0AC\uC6A9\uC790_3","USER_NICK":"KO_3","USER_CODE":"ML_CODE_3","USER_DESC":"Korean test record 3\nHangul: \uAC00\uB098\uB2E4\uB77C\uB9C8\uBC14\uC0AC\n\uC544\uC790\uCC28\uCE74\uD0C0\uD30C\uD558","USER_AGE":35,"USER_COUNT":3000,"USER_BIGINT":922337203685477582,"USER_SCORE":99.0,"USER_RATE":0.789,"USER_RATIO":3.45,"USER_WEIGHT":80.5,"CREATED_DATE":"2026-01-01","UPDATED_TS":"2026-01-01T00:00:00.000","UPDATED_TZ":"2026-01-01T00:00:00.000+09:00","UPDATED_LTZ":"2026-01-01T00:00:00.000+09:00","IS_ACTIVE":"Y","IS_DELETED":"N","USER_PROFILE":"bWxfdGVzdA==","META_JSON":"{\"lang\":\"ko\"}","TAGS":"multilingual,korean","USER_XML":"<user><lang>ko</lang></user>","OPTIONAL_COL":"TC045"}
            ]
        }
    }
}
'@
[System.IO.File]::WriteAllText("$res\TC045.json", $content, $utf8NoBom)
Write-Host "FILE 16: TC045.json - OK"

# FILE 17: TC046.json - emoji 2 records
$content = @'
{
    "operations": {
        "delete_tb_user_v2": null,
        "insert_tb_user_v2": {
            "data": [
                {"USER_ID":"EM_1","USER_NAME":"\uD83D\uDE00_Emoji_1","USER_NICK":"EM1","USER_CODE":"\uD83C\uDF1F_CODE_1","USER_DESC":"Emoji test:\n\uD83D\uDE00\uD83D\uDE01\uD83D\uDE02\uD83D\uDE03\uD83D\uDE04\n\uD83C\uDF89\uD83C\uDF8A\uD83C\uDF8B\nAnimals: \uD83D\uDC36\uD83D\uDC31\uD83D\uDC2D\nNature: \uD83C\uDF33\uD83C\uDF34\uD83C\uDF35","USER_AGE":25,"USER_COUNT":1000,"USER_BIGINT":922337203685477580,"USER_SCORE":88.12,"USER_RATE":0.123,"USER_RATIO":1.23,"USER_WEIGHT":75.43,"CREATED_DATE":"2026-01-01","UPDATED_TS":"2026-01-01T00:00:00.000","UPDATED_TZ":"2026-01-01T00:00:00.000+09:00","UPDATED_LTZ":"2026-01-01T00:00:00.000+09:00","IS_ACTIVE":"Y","IS_DELETED":"N","USER_PROFILE":"ZW1vamk=","META_JSON":"{\"emoji\":true}","TAGS":"emoji,test","USER_XML":"<user><emoji>\uD83D\uDE00</emoji></user>","OPTIONAL_COL":"TC046"},
                {"USER_ID":"EM_2","USER_NAME":"\uD83C\uDFC6_Winner_2","USER_NICK":"EM2","USER_CODE":"\uD83D\uDD11_CODE_2","USER_DESC":"More emoji:\n\uD83D\uDE80\uD83D\uDEF8\uD83D\uDEF9\uD83D\uDEA3\nFlags: \uD83C\uDDF0\uD83C\uDDF7\uD83C\uDDFA\uD83C\uDDF8\uD83C\uDDEF\uD83C\uDDF5\nTech: \uD83D\uDCBB\uD83D\uDCF1\uD83D\uDDA5\uFE0F","USER_AGE":30,"USER_COUNT":2000,"USER_BIGINT":922337203685477581,"USER_SCORE":77.77,"USER_RATE":0.456,"USER_RATIO":2.34,"USER_WEIGHT":60.0,"CREATED_DATE":"2026-01-01","UPDATED_TS":"2026-01-01T00:00:00.000","UPDATED_TZ":"2026-01-01T00:00:00.000+09:00","UPDATED_LTZ":"2026-01-01T00:00:00.000+09:00","IS_ACTIVE":"N","IS_DELETED":"N","USER_PROFILE":"ZW1vamk=","META_JSON":"{\"emoji\":true,\"type\":\"flags\"}","TAGS":"emoji,flags","USER_XML":"<user><emoji>\uD83C\uDFC6</emoji></user>","OPTIONAL_COL":"TC046"}
            ]
        }
    }
}
'@
[System.IO.File]::WriteAllText("$res\TC046.json", $content, $utf8NoBom)
Write-Host "FILE 17: TC046.json - OK"

# FILE 18: TC047.json - HTML/XML special chars
$content = @'
{
    "operations": {
        "delete_tb_user_v2": null,
        "insert_tb_user_v2": {
            "data": [
                {"USER_ID":"HX_1","USER_NAME":"HTML_User_1 <>&\"'","USER_NICK":"HX1","USER_CODE":"HX_CODE_1","USER_DESC":"HTML entities: &lt;tag&gt; &amp; &quot;quoted&quot; &apos;apos&apos;\nXML: <root><child attr=\"val\">text &amp; more</child></root>\nSpecial: <!-- comment --> <![CDATA[data]]>","USER_AGE":25,"USER_COUNT":1000,"USER_BIGINT":922337203685477580,"USER_SCORE":88.12,"USER_RATE":0.123,"USER_RATIO":1.23,"USER_WEIGHT":75.43,"CREATED_DATE":"2026-01-01","UPDATED_TS":"2026-01-01T00:00:00.000","UPDATED_TZ":"2026-01-01T00:00:00.000+09:00","UPDATED_LTZ":"2026-01-01T00:00:00.000+09:00","IS_ACTIVE":"Y","IS_DELETED":"N","USER_PROFILE":"aHRtbA==","META_JSON":"{\"html\":true,\"tag\":\"<b>bold</b>\"}","TAGS":"html,xml,special","USER_XML":"<user><data><![CDATA[<html>&amp;</html>]]></data></user>","OPTIONAL_COL":"TC047"},
                {"USER_ID":"HX_2","USER_NAME":"XML_User_2 </xss>","USER_NICK":"HX2","USER_CODE":"HX_CODE_2","USER_DESC":"More XML special chars:\n<?xml version='1.0'?>\n<root xmlns:ns=\"http://example.com\">\n  <ns:child>value &amp; more</ns:child>\n</root>","USER_AGE":30,"USER_COUNT":2000,"USER_BIGINT":922337203685477581,"USER_SCORE":77.77,"USER_RATE":0.456,"USER_RATIO":2.34,"USER_WEIGHT":60.0,"CREATED_DATE":"2026-01-01","UPDATED_TS":"2026-01-01T00:00:00.000","UPDATED_TZ":"2026-01-01T00:00:00.000+09:00","UPDATED_LTZ":"2026-01-01T00:00:00.000+09:00","IS_ACTIVE":"N","IS_DELETED":"N","USER_PROFILE":"eG1s","META_JSON":"{\"xml\":true}","TAGS":"xml,namespace","USER_XML":"<user><xss>&lt;/xss&gt;</xss></user>","OPTIONAL_COL":"TC047"}
            ]
        }
    }
}
'@
[System.IO.File]::WriteAllText("$res\TC047.json", $content, $utf8NoBom)
Write-Host "FILE 18: TC047.json - OK"

# FILE 19: TC048.json - SQL keywords
$content = @'
{
    "operations": {
        "delete_tb_user_v2": null,
        "insert_tb_user_v2": {
            "data": [
                {"USER_ID":"SQL_1","USER_NAME":"SELECT * FROM users WHERE 1=1","USER_NICK":"SQL1","USER_CODE":"DROP TABLE users; --","USER_DESC":"SQL Injection attempt data (stored as literal string):\nSELECT * FROM users WHERE 1=1; DROP TABLE users; --\nUNION SELECT username, password FROM admin\nOR '1'='1'","USER_AGE":25,"USER_COUNT":1000,"USER_BIGINT":922337203685477580,"USER_SCORE":88.12,"USER_RATE":0.123,"USER_RATIO":1.23,"USER_WEIGHT":75.43,"CREATED_DATE":"2026-01-01","UPDATED_TS":"2026-01-01T00:00:00.000","UPDATED_TZ":"2026-01-01T00:00:00.000+09:00","UPDATED_LTZ":"2026-01-01T00:00:00.000+09:00","IS_ACTIVE":"Y","IS_DELETED":"N","USER_PROFILE":"c3Fs","META_JSON":"{\"sql\":\"SELECT 1\"}","TAGS":"sql,keyword,injection","USER_XML":"<user><sql>SELECT 1</sql></user>","OPTIONAL_COL":"TC048"},
                {"USER_ID":"SQL_2","USER_NAME":"INSERT INTO admin VALUES('hack')","USER_NICK":"SQL2","USER_CODE":"UPDATE users SET admin=1","USER_DESC":"More SQL keywords as data:\nINSERT INTO admin VALUES('hack');\nDELETE FROM logs WHERE 1=1;\nCREATE TABLE evil (id INT);\nALTER TABLE users ADD COLUMN hacked BOOLEAN;","USER_AGE":30,"USER_COUNT":2000,"USER_BIGINT":922337203685477581,"USER_SCORE":77.77,"USER_RATE":0.456,"USER_RATIO":2.34,"USER_WEIGHT":60.0,"CREATED_DATE":"2026-01-01","UPDATED_TS":"2026-01-01T00:00:00.000","UPDATED_TZ":"2026-01-01T00:00:00.000+09:00","UPDATED_LTZ":"2026-01-01T00:00:00.000+09:00","IS_ACTIVE":"N","IS_DELETED":"N","USER_PROFILE":"c3Fs","META_JSON":"{\"sql\":\"DELETE FROM\"}","TAGS":"sql,delete,create","USER_XML":"<user><sql>INSERT INTO</sql></user>","OPTIONAL_COL":"TC048"}
            ]
        }
    }
}
'@
[System.IO.File]::WriteAllText("$res\TC048.json", $content, $utf8NoBom)
Write-Host "FILE 19: TC048.json - OK"

# FILE 20: TC049.json - numeric precision
$content = @'
{
    "operations": {
        "delete_tb_user_v2": null,
        "insert_tb_user_v2": {
            "data": [
                {
                    "USER_ID": "NUM_1",
                    "USER_NAME": "Numeric_Precision_1",
                    "USER_NICK": "NUM1",
                    "USER_CODE": "NUM_CODE_1",
                    "USER_DESC": "Numeric precision test record",
                    "USER_AGE": 99,
                    "USER_COUNT": 999999,
                    "USER_BIGINT": 9223372036854775807,
                    "USER_SCORE": 3.141592653589793,
                    "USER_RATE": 1.4142136,
                    "USER_RATIO": 2.7182817,
                    "USER_WEIGHT": 1.7320508075688772,
                    "CREATED_DATE": "2026-01-01",
                    "UPDATED_TS": "2026-01-01T00:00:00.000",
                    "UPDATED_TZ": "2026-01-01T00:00:00.000+09:00",
                    "UPDATED_LTZ": "2026-01-01T00:00:00.000+09:00",
                    "IS_ACTIVE": "Y",
                    "IS_DELETED": "N",
                    "USER_PROFILE": "bnVtZXJpYw==",
                    "META_JSON": "{\"pi\":3.14159,\"e\":2.71828}",
                    "TAGS": "numeric,precision",
                    "USER_XML": "<user><pi>3.14159</pi></user>",
                    "OPTIONAL_COL": "TC049"
                }
            ]
        }
    }
}
'@
[System.IO.File]::WriteAllText("$res\TC049.json", $content, $utf8NoBom)
Write-Host "FILE 20: TC049.json - OK"

# FILE 21: TC050.json - negative values
$content = @'
{
    "operations": {
        "delete_tb_user_v2": null,
        "insert_tb_user_v2": {
            "data": [
                {
                    "USER_ID": "NEG_1",
                    "USER_NAME": "Negative_1",
                    "USER_NICK": "NEG1",
                    "USER_CODE": "NEG_CODE",
                    "USER_DESC": "Negative values test record",
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
                    "USER_PROFILE": "bmVn",
                    "META_JSON": "{\"negative\":true}",
                    "TAGS": "negative,values",
                    "USER_XML": "<user><neg>-1</neg></user>",
                    "OPTIONAL_COL": "TC050"
                }
            ]
        }
    }
}
'@
[System.IO.File]::WriteAllText("$res\TC050.json", $content, $utf8NoBom)
Write-Host "FILE 21: TC050.json - OK"

Write-Host "--- Files 15-21 complete ---"
