# YOLO / OpenCV need a few system libs even with headless wheels.
FROM python:3.11-slim-bookworm

RUN apt-get update && apt-get install -y --no-install-recommends \
    libgl1 \
    libglib2.0-0 \
    libgomp1 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

COPY requirements-docker.txt .

# CPU-only PyTorch (avoids multi-GB CUDA metapackages on amd64/aarch64).
RUN pip install --upgrade pip \
 && pip install --no-cache-dir \
      torch torchvision \
      --index-url https://download.pytorch.org/whl/cpu \
 && pip install --no-cache-dir -r requirements-docker.txt \
 && pip install --no-cache-dir --no-deps ultralytics==8.3.49

COPY app ./app

# Optional locally trained weights (see docker-weights/README.md + model-training/scripts/04-sync-weights-for-docker-build.sh)
COPY docker-weights/ /docker-weights/

EXPOSE 8000

ENV HOST=0.0.0.0 \
    PORT=8000 \
    INFERENCE_MODE=mock

CMD ["sh", "-c", "uvicorn app.main:app --host ${HOST} --port ${PORT}"]
