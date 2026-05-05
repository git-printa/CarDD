# AI Car Damage Assessment — MVP

Microservices MVP: React (Vite) frontend, Spring Boot API, Python FastAPI + YOLOv8 inference, rule-based repair estimates.

## Project layout

| Path | Role |
|------|------|
| `frontend/` | Vite + React: multi-image upload, results table, bbox overlay |
| `backend/` | Spring Boot: uploads, metadata, calls AI service, cost aggregation |
| `ai-service/` | FastAPI + Ultralytics YOLOv8n: `POST /predict` |
| `docker-compose.yml` | `ai-service` + `backend` + `frontend` (nginx on 3000) |
| `model-training/` | Scripts + guide to download base YOLO, train car-damage weights, sync into Docker ([`model-training/README.md`](model-training/README.md)) |

## Course steps (status)

1. **Structure** — repo layout
2. **AI service** — `ai-service/` FastAPI + mock / YOLO modes (you can run locally)
3. **Backend API** — `backend/` upload + metadata endpoints (done)
4. **Backend → AI integration** — `/analyze` + estimation + required response format (done)
5. **Frontend UI** — `frontend/` Vite + React (done)
6. **Dockerfiles + compose** — images for `ai-service`, `backend`, `frontend` (done)
7. **`docker compose up`** — full stack (see below)
8. E2E test + DigitalOcean notes

## Prerequisites (for later steps)

- Docker & Docker Compose
- JDK 11+ (backend)
- Node 20+ (frontend)
- Python 3.11+ (AI service, local dev)

## API Testing (Postman)

- Collection: `postman/car-damage-ai.collection.json`
- Local environment: `postman/car-damage-ai.local.environment.json`
- Keep these files versioned and update them as endpoints/response contracts evolve.

## Recent Integration Notes (May 2026)

This section summarizes the latest end-to-end changes applied in this workspace:

- **NIS/ILS currency contract**:
  - Assessment responses now include `currency: "ILS"`, `total_cost_nis`, and `estimated_cost_nis`.
  - Legacy fields (`total_cost`, `estimated_cost`) remain for backward compatibility.
- **Section accuracy improvements**:
  - Added 2-stage support (`detections` + `part_detections`) from `ai-service`.
  - Backend now matches damage boxes to part boxes using IoU and falls back to geometric part inference when needed.
- **Training endpoint hardening**:
  - Added safer error handling and persistent training-run migration (`V2__ensure_training_tables.sql`).
  - Training start/status no longer fail silently when persistence logging fails.
- **Docker training alignment**:
  - Backend training command now targets mounted `model-training` workspace via `TRAIN_WORKDIR` + `TRAIN_COMMAND`.
  - Current compose command is wired for the 6-class CArDD-style dataset training flow.
- **6-class dataset compatibility**:
  - Added alias mapping so `glass shatter`, `lamp broken`, `tire flat` can still flow into current billing categories.

For dataset/training specifics and exact path requirements, see `model-training/README.md`.


## Unified Docker Run

```bash
cd car-damage-ai
cp .env.example .env   # optional
docker compose up -d --build
```

Services:

- `ai-service`: `http://localhost:8000`
- `backend`: `http://localhost:8080`
- `frontend`: `http://localhost:3000`

Sanity smoke test:

```bash
./tests/e2e-compose-smoke.sh
```
