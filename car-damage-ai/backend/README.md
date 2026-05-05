# Backend API (STEP 3)

Spring Boot service on port **8080**.

Requires **Java 11+**.

## Implemented in this step

- `GET /health` — simple liveness endpoint
- `POST /api/v1/assessments/upload` — accepts multipart `files` (multi-image) and optional `vehicleId`
- `GET /api/v1/assessments/{batchId}` — returns stored upload metadata for a batch
- `POST /api/v1/assessments/{batchId}/analyze` — calls AI service + estimates repair costs
- Local file storage at `UPLOAD_DIR/<batchId>/...`
- In-memory metadata store (MVP-safe; replace with DB in production)
- Global JSON error handling

## Request/response quick example

Upload:

```bash
curl -s -X POST http://localhost:8080/api/v1/assessments/upload \
  -F "vehicleId=VH-123" \
  -F "files=@/path/car1.jpg" \
  -F "files=@/path/car2.jpg" | jq .
```

Sample response:

```json
{
  "batchId": "...",
  "vehicleId": "VH-123",
  "totalFiles": 2,
  "createdAt": "...",
  "items": [
    {
      "imageId": "...",
      "originalFilename": "car1.jpg",
      "storedFilename": "....jpg",
      "contentType": "image/jpeg",
      "sizeBytes": 12345,
      "width": 0,
      "height": 0,
      "status": "UPLOADED",
      "uploadedAt": "..."
    }
  ]
}
```

## Local run

```bash
cd backend
./mvnw spring-boot:run
```

If Maven wrapper is not present, use local Maven:

```bash
mvn spring-boot:run
```

## Tests

```bash
cd backend
mvn test
```

## Environment

Copy `backend/.env.example` and set values via shell / compose:

- `SERVER_PORT` (default `8080`)
- `UPLOAD_DIR` (default `/tmp/car-damage-ai/uploads`)
- `AI_SERVICE_URL` (used in STEP 4 integration)


## Docker

From repo root:

```bash
docker compose up -d --build backend ai-service
```

Backend health:

```bash
curl -s http://localhost:8080/health
```

## Compose E2E smoke test

```bash
./tests/e2e-compose-smoke.sh
```


## Analyze response shape

```json
{
  "damages": [
    {
      "type": "scratch",
      "confidence": 0.87,
      "box": [63, 51, 244, 32],
      "part": "bumper",
      "estimated_cost": 160,
      "estimated_cost_nis": 160,
      "image_id": "..."
    }
  ],
  "total_cost": 160,
  "total_cost_nis": 160,
  "currency": "ILS"
}
```

Rule-based estimator maps `(damage type + inferred part)` to a fixed cost table in Israeli shekel (ILS/NIS).
