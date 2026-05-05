import hashlib
import io
import logging
import os
import shutil
import threading
import traceback
from contextlib import asynccontextmanager
from datetime import datetime, timezone
from typing import Any, Optional

from dotenv import load_dotenv
from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from PIL import Image
from pydantic import BaseModel

load_dotenv()

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
)
log = logging.getLogger("ai-service")

_model: Any = None
_inference_mode: str = "mock"
_train_lock = threading.Lock()
_train_status: dict[str, Any] = {
    "running": False,
    "started_at": None,
    "finished_at": None,
    "exit_code": None,
    "command": None,
    "data_yaml": None,
    "best_pt": None,
    "last_error": None,
    "run_id": None,
    "log_tail": [],
}


def _env_mode() -> str:
    return os.getenv("INFERENCE_MODE", "mock").strip().lower()


def _model_path() -> str:
    return os.getenv("MODEL_PATH", "yolov8n.pt").strip()


def _load_yolo(path_override: Optional[str] = None):
    global _model
    from ultralytics import YOLO

    path = (path_override or _model_path()).strip()
    log.info("Loading YOLO weights from %s", path)
    _model = YOLO(path)
    log.info("YOLO ready; class names: %s", getattr(_model, "names", None))


def _fallback_model_path() -> str:
    candidates = [
        "/docker-weights/car_damage_best.pt",
        "/docker-weights/cardd_best.pt",
        "/weights/yolo26x.pt",
        "yolov8n.pt",
    ]
    for p in candidates:
        if os.path.isfile(p):
            return p
    return ""


def _append_train_log(line: str) -> None:
    with _train_lock:
        tail = _train_status.get("log_tail", [])
        tail.append(str(line))
        if len(tail) > 300:
            tail = tail[-300:]
        _train_status["log_tail"] = tail


def _set_train_status(**kwargs: Any) -> None:
    with _train_lock:
        _train_status.update(kwargs)


def _snapshot_train_status() -> dict[str, Any]:
    with _train_lock:
        out = dict(_train_status)
        out["log_tail"] = list(_train_status.get("log_tail", []))
        return out


class TrainStartRequest(BaseModel):
    data_yaml: str
    model_path: str = "/workspace/model-training/cache/base-weights/yolov8m.pt"
    epochs: int = 30
    imgsz: int = 640
    project: str = "/workspace/model-training/runs"
    name: str = "cardd_6class"
    patience: int = 15
    deploy_path: str = "/docker-weights/car_damage_best.pt"


@asynccontextmanager
async def lifespan(app: FastAPI):
    global _inference_mode
    _inference_mode = _env_mode()
    if _inference_mode == "yolo":
        try:
            _load_yolo()
        except Exception:
            configured = _model_path()
            fallback = _fallback_model_path()
            if fallback and fallback != configured:
                log.exception("Configured MODEL_PATH failed (%s); trying fallback %s", configured, fallback)
                os.environ["MODEL_PATH"] = fallback
                _load_yolo(fallback)
            elif fallback:
                log.exception("Configured MODEL_PATH failed (%s); retrying fallback", configured)
                _load_yolo(fallback)
            else:
                log.exception("Failed to load YOLO model and no fallback found; switching to mock mode")
                _inference_mode = "mock"
    else:
        log.info("INFERENCE_MODE=mock — returning synthetic damage boxes")
    yield
    log.info("Shutdown complete")


app = FastAPI(title="Car Damage AI", version="0.1.0", lifespan=lifespan)
app.add_middleware(
    CORSMiddleware,
    allow_origins=os.getenv("CORS_ORIGINS", "*").split(","),
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


def _image_from_upload(data: bytes) -> Image.Image:
    try:
        img = Image.open(io.BytesIO(data)).convert("RGB")
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Invalid image: {e}") from e
    return img


def _stable_seed(data: bytes) -> int:
    h = hashlib.sha256(data).hexdigest()
    return int(h[:8], 16)


def _permute_three(seed: int) -> list[int]:
    """Deterministic shuffle of indices 0,1,2."""
    order = [0, 1, 2]
    s = seed & 0xFFFFFFFF
    for i in range(2, 0, -1):
        j = s % (i + 1)
        s //= i + 1
        order[i], order[j] = order[j], order[i]
    return order


def _mock_layout() -> str:
    return os.getenv("MOCK_LAYOUT", "default").strip().lower()


def mock_front_collision(width: int, height: int) -> list[dict[str, Any]]:
    """
    Plausible front-impact layout: hood/shell, headlight zone, bumper/grille.
    For demo only — not derived from image pixels.
    """
    specs: list[tuple[float, float, float, float, str, float]] = [
        (0.28, 0.12, 0.48, 0.20, "scratch", 0.81),
        (0.06, 0.44, 0.20, 0.16, "crack", 0.84),
        (0.38, 0.68, 0.26, 0.12, "dent", 0.86),
    ]
    out: list[dict[str, Any]] = []
    for xn, yn, wn, hn, label, conf in specs:
        x = max(0, int(xn * width))
        y = max(0, int(yn * height))
        w = max(8, int(wn * width))
        h = max(8, int(hn * height))
        if x + w > width:
            w = width - x
        if y + h > height:
            h = height - y
        out.append({"label": label, "confidence": conf, "box": [x, y, w, h]})
    return out


def mock_part_detections(width: int, height: int) -> list[dict[str, Any]]:
    """Deterministic body-part regions used for 2-stage section matching."""
    specs: list[tuple[float, float, float, float, str, float]] = [
        (0.20, 0.10, 0.60, 0.25, "hood", 0.92),
        (0.07, 0.40, 0.20, 0.20, "headlight", 0.88),
        (0.73, 0.40, 0.20, 0.20, "headlight", 0.88),
        (0.10, 0.64, 0.80, 0.24, "bumper", 0.94),
    ]
    out: list[dict[str, Any]] = []
    for xn, yn, wn, hn, label, conf in specs:
        x = max(0, int(xn * width))
        y = max(0, int(yn * height))
        w = max(8, int(wn * width))
        h = max(8, int(hn * height))
        if x + w > width:
            w = width - x
        if y + h > height:
            h = height - y
        out.append({"label": label, "confidence": conf, "box": [x, y, w, h]})
    return out


def mock_detections(width: int, height: int, seed: int) -> list[dict[str, Any]]:
    """Deterministic fake boxes for UI / pipeline testing without custom weights."""
    if _mock_layout() == "front_collision":
        return mock_front_collision(width, height)

    labels = ["scratch", "dent", "crack"]
    out: list[dict[str, Any]] = []
    # three template regions: top-left, center-right, bottom band
    specs = [
        (0.08, 0.12, 0.35, 0.08, 0, 0.82),
        (0.55, 0.35, 0.22, 0.18, 1, 0.76),
        (0.2, 0.72, 0.6, 0.12, 2, 0.71),
    ]
    count = 1 + (seed % 3)
    order = _permute_three(seed ^ 0x9E3779B9)
    size_jitter = 0.82 + 0.36 * (((seed >> 8) & 0xFFF) / 4095.0)
    for n in range(count):
        idx = order[n]
        xn, yn, wn, hn, li, conf_base = specs[idx]
        jitter = (seed % 17) / 1000.0 + n * 0.003
        x = int((xn + jitter) * width)
        y = int((yn + jitter * 0.5) * height)
        w = max(8, int(wn * width * size_jitter))
        h = max(8, int(hn * height * size_jitter))
        conf = min(0.99, conf_base + ((seed >> (n * 3)) % 7) / 100.0)
        out.append(
            {
                "label": labels[li % len(labels)],
                "confidence": round(conf, 4),
                "box": [x, y, w, h],
            }
        )
    return out


def extract_part_detections(detections: list[dict[str, Any]]) -> list[dict[str, Any]]:
    known_parts = {"door", "bumper", "hood", "headlight", "fender"}
    out: list[dict[str, Any]] = []
    for d in detections:
        label = str(d.get("label", "")).strip().lower()
        if not label:
            continue
        if label in known_parts:
            out.append(d)
            continue
        tokens = [t for t in label.replace("-", "_").split("_") if t]
        if any(t in known_parts for t in tokens):
            out.append(d)
    out.sort(key=lambda it: float(it.get("confidence", 0.0)), reverse=True)
    return out[:25]


def yolo_detections(img: Image.Image) -> list[dict[str, Any]]:
    if _model is None:
        raise HTTPException(status_code=503, detail="Model not loaded")
    import numpy as np

    arr = np.array(img)
    results = _model.predict(arr, verbose=False)
    if not results:
        return []
    r0 = results[0]
    boxes = getattr(r0, "boxes", None)
    if boxes is None or len(boxes) == 0:
        return []
    raw_names = _model.names
    out: list[dict[str, Any]] = []
    xyxy = boxes.xyxy.cpu().numpy()
    confs = boxes.conf.cpu().numpy()
    clss = boxes.cls.cpu().numpy().astype(int)
    for i in range(len(xyxy)):
        x1, y1, x2, y2 = xyxy[i].tolist()
        x, y = int(x1), int(y1)
        w, h = int(x2 - x1), int(y2 - y1)
        cls_id = int(clss[i])
        if isinstance(raw_names, dict):
            label = str(raw_names.get(cls_id, cls_id))
        elif isinstance(raw_names, list) and 0 <= cls_id < len(raw_names):
            label = str(raw_names[cls_id])
        else:
            label = str(cls_id)
        conf = float(confs[i])
        out.append(
            {
                "label": label,
                "confidence": round(conf, 4),
                "box": [x, y, w, h],
            }
        )
    # cap for API sanity
    out.sort(key=lambda d: d["confidence"], reverse=True)
    return out[:25]


def _run_training(req: TrainStartRequest, run_id: str) -> None:
    _set_train_status(
        running=True,
        started_at=datetime.now(timezone.utc).isoformat(),
        finished_at=None,
        exit_code=None,
        command=f"yolo-train model={req.model_path} data={req.data_yaml} epochs={req.epochs} imgsz={req.imgsz}",
        data_yaml=req.data_yaml,
        best_pt=None,
        last_error=None,
        run_id=run_id,
        log_tail=[],
    )
    _append_train_log(f"[train] run_id={run_id}")
    _append_train_log(f"[train] data={req.data_yaml}")
    _append_train_log(f"[train] model={req.model_path}")
    try:
        from ultralytics import YOLO

        model = YOLO(req.model_path)
        model.train(
            data=req.data_yaml,
            epochs=max(1, int(req.epochs)),
            imgsz=max(64, int(req.imgsz)),
            project=req.project,
            name=req.name,
            patience=max(1, int(req.patience)),
            exist_ok=True,
        )
        best = os.path.join(req.project, req.name, "weights", "best.pt")
        if not os.path.isfile(best):
            raise RuntimeError(f"Missing best.pt at {best}")
        deploy_parent = os.path.dirname(req.deploy_path)
        if deploy_parent:
            os.makedirs(deploy_parent, exist_ok=True)
        shutil.copy2(best, req.deploy_path)
        os.environ["MODEL_PATH"] = req.deploy_path
        if _inference_mode == "yolo":
            _load_yolo()
        _append_train_log(f"[train] best_pt={best}")
        _append_train_log(f"[train] deployed_to={req.deploy_path}")
        _set_train_status(
            running=False,
            finished_at=datetime.now(timezone.utc).isoformat(),
            exit_code=0,
            best_pt=req.deploy_path,
        )
    except Exception as e:
        _append_train_log(f"[train] error={e}")
        _append_train_log(traceback.format_exc())
        _set_train_status(
            running=False,
            finished_at=datetime.now(timezone.utc).isoformat(),
            exit_code=1,
            last_error=str(e),
        )


@app.get("/health")
def health():
    body: dict[str, Any] = {
        "status": "ok",
        "inference_mode": _inference_mode,
    }
    if _inference_mode == "yolo":
        body["model_path"] = _model_path()
    else:
        body["mock_layout"] = _mock_layout()
    return body


@app.post("/train/start")
def train_start(req: TrainStartRequest):
    status = _snapshot_train_status()
    if status.get("running"):
        return {
            "running": True,
            "message": "Training already running",
            "run_id": status.get("run_id"),
        }
    if not os.path.isfile(req.data_yaml):
        raise HTTPException(status_code=400, detail=f"data_yaml not found: {req.data_yaml}")
    if not os.path.isfile(req.model_path):
        raise HTTPException(status_code=400, detail=f"model_path not found: {req.model_path}")
    run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    t = threading.Thread(target=_run_training, args=(req, run_id), daemon=True)
    t.start()
    return {"running": True, "message": "Training started", "run_id": run_id}


@app.get("/train/status")
def train_status():
    return _snapshot_train_status()


class ReloadModelRequest(BaseModel):
    model_path: Optional[str] = None


@app.post("/reload-model")
def reload_model(req: ReloadModelRequest):
    path = req.model_path
    if path:
        os.environ["MODEL_PATH"] = path
    try:
        _load_yolo()
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to reload model: {e}") from e
    return {"status": "ok", "model_path": _model_path(), "inference_mode": _inference_mode}


@app.post("/predict")
async def predict(file: UploadFile = File(...)):
    if not file.content_type or not file.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="Expected an image file")
    data = await file.read()
    if not data:
        raise HTTPException(status_code=400, detail="Empty file")
    if len(data) > 15 * 1024 * 1024:
        raise HTTPException(status_code=400, detail="Image too large (max 15MB)")

    try:
        img = _image_from_upload(data)
    except HTTPException:
        raise
    except Exception as e:
        log.exception("Image decode error")
        raise HTTPException(status_code=400, detail=str(e)) from e

    w, h = img.size

    try:
        part_detections: list[dict[str, Any]]
        if _inference_mode == "yolo":
            detections = yolo_detections(img)
            part_detections = extract_part_detections(detections)
        else:
            seed = _stable_seed(data)
            detections = mock_detections(w, h, seed)
            part_detections = mock_part_detections(w, h)
    except HTTPException:
        raise
    except Exception as e:
        log.exception("Inference failed")
        raise HTTPException(status_code=500, detail=f"Inference failed: {e}") from e

    log.info(
        "predict mode=%s size=%sx%s detections=%d",
        _inference_mode,
        w,
        h,
        len(detections),
    )
    payload: dict[str, Any] = {
        "detections": detections,
        "part_detections": part_detections,
        "image_width": w,
        "image_height": h,
        "inference_mode": _inference_mode,
    }
    if _inference_mode == "yolo":
        payload["model_path"] = _model_path()
    else:
        payload["mock_layout"] = _mock_layout()
    return payload
