def test_health_ok(client):
    r = client.get("/health")
    assert r.status_code == 200
    body = r.json()
    assert body["status"] == "ok"
    assert body["inference_mode"] == "mock"
    assert "mock_layout" in body
    assert body.get("model_path") is None


def test_predict_jpeg_returns_detections(client, rgb_jpeg_bytes):
    r = client.post(
        "/predict",
        files={"file": ("car.jpg", rgb_jpeg_bytes, "image/jpeg")},
    )
    assert r.status_code == 200
    data = r.json()
    assert data["inference_mode"] == "mock"
    assert "mock_layout" in data
    assert data["image_width"] == 320
    assert data["image_height"] == 240
    dets = data["detections"]
    assert 1 <= len(dets) <= 3
    for d in dets:
        assert d["label"] in ("scratch", "dent", "crack")
        assert 0 < d["confidence"] <= 1.0
        box = d["box"]
        assert len(box) == 4
        x, y, w, h = box
        assert w > 0 and h > 0
        assert x >= 0 and y >= 0
        assert x + w <= 320 + 1  # rounding slack
        assert y + h <= 240 + 1
    part_dets = data["part_detections"]
    assert len(part_dets) >= 1
    for p in part_dets:
        assert p["label"] in ("door", "bumper", "hood", "headlight", "fender")
        assert 0 < p["confidence"] <= 1.0
        assert len(p["box"]) == 4


def test_predict_rejects_non_image_content_type(client):
    r = client.post(
        "/predict",
        files={"file": ("x.txt", b"not an image", "text/plain")},
    )
    assert r.status_code == 400
    assert "image" in r.json()["detail"].lower()


def test_predict_rejects_empty_file(client):
    r = client.post(
        "/predict",
        files={"file": ("empty.jpg", b"", "image/jpeg")},
    )
    assert r.status_code == 400


def test_predict_rejects_invalid_image_bytes(client):
    r = client.post(
        "/predict",
        files={"file": ("bad.jpg", b"\xff\xd8notreally", "image/jpeg")},
    )
    assert r.status_code == 400
    assert "invalid" in r.json()["detail"].lower() or "image" in r.json()["detail"].lower()


def test_predict_rejects_oversized_payload(client):
    # App limit: 15MB; send 15MB + 1 of JPEG header garbage (still fails decode or size first)
    huge = b"\xff" * (15 * 1024 * 1024 + 1)
    r = client.post(
        "/predict",
        files={"file": ("big.jpg", huge, "image/jpeg")},
    )
    assert r.status_code == 400
    assert "large" in r.json()["detail"].lower() or "too" in r.json()["detail"].lower()
