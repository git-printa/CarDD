# AI Service (Step 2)

FastAPI + **Ultralytics YOLOv8** on port **8000**.

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/health` | Liveness + `inference_mode` |
| POST | `/predict` | Multipart `file` (image) → detections |

## Response shape (`POST /predict`)

```json
{
  "detections": [
    { "label": "scratch", "confidence": 0.87, "box": [x, y, w, h] }
  ],
  "image_width": 1920,
  "image_height": 1080,
  "inference_mode": "mock"
}
```

Boxes are **pixel coordinates** in the original image space (`x`, `y` top-left, `w`, `h` size).

## Modes (`.env`)

- **`INFERENCE_MODE=mock`** (default): deterministic synthetic `scratch` / `dent` / `crack` boxes so the full stack works **without** a custom-trained model.
- **`INFERENCE_MODE=yolo`**: runs real YOLO. Set **`MODEL_PATH`** to your trained `.pt` (three classes named scratch/dent/crack) for production semantics. Using stock `yolov8n.pt` will return **COCO** labels (useful only to verify wiring).

**Train your own car-damage weights and bundle them for Docker:** see the repo folder [`model-training/`](../model-training/README.md) (scripts `01`–`04`, synthetic demo dataset, and `docker-weights/` copy path `/docker-weights/car_damage_best.pt`).

## Tests

```bash
cd ai-service
source .venv/bin/activate
pip install -r requirements-dev.txt
pytest -v
```

`tests/conftest.py` sets `INFERENCE_MODE=mock` before importing the app so CI never downloads weights.

## Local run

```bash
cd ai-service
python3 -m venv .venv
source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt
cp .env.example .env        # optional edits
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

Test:

```bash
curl -s http://127.0.0.1:8000/health
curl -s -F "file=@/path/to/photo.jpg" http://127.0.0.1:8000/predict | jq .
```

## Docker (from repo root `car-damage-ai/`)

```bash
cd car-damage-ai
cp .env.example .env   # optional
docker compose build ai-service
docker compose up -d ai-service
```

Smoke test:

```bash
curl -s http://127.0.0.1:8000/health
```

Override inference mode for one run:

```bash
INFERENCE_MODE=yolo docker compose up -d ai-service
```

`INFERENCE_MODE=yolo` downloads weights on first request / first load into the container filesystem (ephemeral unless you add a volume for caches).

### Docker image notes

The `Dockerfile` installs **CPU-only** PyTorch from the official CPU wheel index, then `ultralytics` with `--no-deps` so the image stays suitable for **CPU droplets** and typical dev laptops. Dependency list for the container is in `requirements-docker.txt`. Local `pip install -r requirements.txt` may still resolve a GPU/CUDA stack depending on your platform.

## Postman

- Import `postman/car-damage-ai.collection.json`
- Import `postman/car-damage-ai.local.environment.json`
- Set `sampleImagePath` and `sampleTextPath` in the environment before running `POST /predict` requests

## Notes

- Max upload size enforced in app: **15MB**.
- CORS: set `CORS_ORIGINS` to comma-separated origins in production (default `*`).


Unified stack smoke test from repo root:

```bash
./tests/e2e-compose-smoke.sh
```
