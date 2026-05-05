# Bundled YOLO weights (optional)

This directory is copied into the image at **`/docker-weights/`**.

- **`README.md`** is tracked in git.
- **`car_damage_best.pt`** is produced locally by training, then synced here (gitignored).

## Add weights before building the image

From the repo root `car-damage-ai/`:

```bash
./model-training/scripts/04-sync-weights-for-docker-build.sh
```

Then in `.env`:

```env
INFERENCE_MODE=yolo
MODEL_PATH=/docker-weights/car_damage_best.pt
```

Rebuild: `docker compose build ai-service`

If `car_damage_best.pt` is missing, keep `INFERENCE_MODE=mock` or point `MODEL_PATH` to a downloaded base `.pt` (COCO — not car-specific).
