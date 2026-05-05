"""
Force mock inference before app import so tests never download YOLO weights.
load_dotenv() in app.main does not override existing os.environ keys.
"""

import os

os.environ.setdefault("INFERENCE_MODE", "mock")
os.environ.setdefault("MODEL_PATH", "yolov8n.pt")
os.environ.setdefault("MOCK_LAYOUT", "default")

import pytest
from starlette.testclient import TestClient

from app.main import app


@pytest.fixture()
def client() -> TestClient:
    with TestClient(app) as c:
        yield c


@pytest.fixture()
def rgb_jpeg_bytes() -> bytes:
    from io import BytesIO

    from PIL import Image

    buf = BytesIO()
    Image.new("RGB", (320, 240), color=(90, 120, 60)).save(buf, format="JPEG", quality=85)
    return buf.getvalue()
