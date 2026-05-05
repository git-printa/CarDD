# Frontend (STEP 5)

Vite + React UI on port **3000**.

## Features

- Multi-image upload + optional vehicle ID
- Loading spinner while uploading + analyzing
- Results table: type, part, confidence, estimated cost, box
- Image preview with SVG bounding boxes (per uploaded file tab)

## Local dev

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:3000`.  
The dev server proxies `/api/*` to `http://localhost:8080` (see `vite.config.ts`).

## Docker (from repo root)

```bash
docker compose up -d --build frontend
```

The container serves the SPA via nginx and proxies `/api/*` to the `backend` service.
