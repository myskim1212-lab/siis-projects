"""WSO2 Management Tool — 진입점 (pywebview)."""
from pathlib import Path

import webview

import db.database as db
from bridge.api import Api

WEB_DIR = Path(__file__).parent / 'web'


def main():
    db.init_db()
    api = Api()
    webview.create_window(
        'WSO2 Management Tool',
        str(WEB_DIR / 'index.html'),
        js_api=api,
        width=1280,
        height=800,
        min_size=(1000, 640),
    )
    webview.start()


if __name__ == '__main__':
    main()
