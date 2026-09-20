# 오프라인 설치용 패키지 (vendor/wheels)

`requirements.txt`에 필요한 모든 패키지(의존 패키지 포함)를 미리 다운로드해둔 디렉터리입니다.
외부망이 안 되는 PC에서도 인터넷 없이 설치할 수 있습니다.

- 대상: Windows x64, **Python 3.11 및 3.13** (cp311 / cp313, win_amd64)
- 확인 완료: 이 디렉터리만으로 `--no-index`(PyPI 접속 없이) 설치 성공 및 `import` 정상 동작 확인함.

## 설치 방법

폐쇄망 PC에 이 프로젝트 폴더(`vendor/wheels` 포함)를 복사한 뒤:

```bash
python -m pip install --no-index --find-links=vendor/wheels -r requirements.txt
```

(`run.bat`이 내부적으로 이 명령을 실행합니다.)

## 주의

- **컴파일된 패키지(`cryptography`, `bcrypt`, `pynacl`, `cffi`, `pythonnet`,
  `charset_normalizer`)는 Python 버전/아키텍처가 안 맞으면 설치가 실패합니다.**
  이 중 `cryptography`/`bcrypt`/`pynacl`/`pythonnet`은 "stable ABI(abi3)"로 빌드되어 있어
  한 버전만 받아둬도 그 이후 버전에서 대부분 그대로 동작하지만, **`cffi`와
  `charset_normalizer`는 Python 마이너 버전마다 정확히 맞는 wheel이 따로 필요**합니다
  (실제로 Python 3.13 PC에서 `cffi` 불일치로 설치가 실패한 사례가 있어 3.11/3.13 두
  버전을 모두 받아뒀습니다). 그 외 버전(3.9/3.10/3.12 등)이나 32bit Python이면 여전히
  실패할 수 있습니다 — 그럴 경우 아래 재생성 명령으로 해당 버전용 wheel을 추가하면 됩니다.
- `pywebview` 실행에는 Windows의 **Microsoft Edge WebView2 Runtime**도 필요합니다
  (대부분 Windows 10/11에 기본 설치되어 있음). 이건 pip 패키지가 아니라 별도의 OS
  구성요소라 여기 포함되어 있지 않습니다.

## 재생성 / 다른 Python 버전 추가 (인터넷 되는 환경에서)

전체를 현재 실행 중인 Python 버전 기준으로 새로 받으려면:

```bash
python -m pip download -r requirements.txt -d vendor/wheels
python -m pip download setuptools wheel -d vendor/wheels
```

**폐쇄망 PC가 다른 Python 버전(예: 3.12)을 쓴다면**, 그 버전 wheel만 추가로 받아서 같은
`vendor/wheels` 폴더에 넣으면 됩니다 (설치 중인 인터프리터에 맞는 wheel을 pip이 자동으로
골라 쓰므로 여러 버전을 한 폴더에 같이 둬도 문제없음):

```bash
python -m pip download bcrypt cryptography cffi pynacl pythonnet charset_normalizer ^
    -d vendor/wheels --python-version 3.12 --implementation cp --abi cp312 --platform win_amd64 --only-binary=:all:
```

(`--python-version`/`--abi` 숫자만 대상 버전으로 바꾸면 됨. `proxy_tools`는 순수 Python
sdist라 버전 무관하게 이미 받아둔 것 하나로 모든 버전에서 동작함.)
