import { useEffect, useMemo, useRef, useState } from "react";
import {
  exportTrainingDataset,
  getTrainingImages,
  getTrainingLabels,
  getTrainingStatus,
  saveTrainingLabels,
  startTraining,
  type TrainingExportResult,
  type TrainingImage,
  type TrainingLabel,
  type TrainingStatus,
} from "../api";

const DAMAGE_TYPES = [
  "surface_scratch",
  "paint_scuff",
  "clearcoat_scratch",
  "dent",
  "crease",
  "panel_deformation",
  "bumper_deformation",
  "misalignment",
  "crack",
  "broken_light",
  "shattered_glass",
  "hole_puncture",
] as const;

const PARTS = [
  "front_bumper",
  "rear_bumper",
  "hood",
  "trunk",
  "roof",
  "front_left_door",
  "front_right_door",
  "rear_left_door",
  "rear_right_door",
  "front_left_fender",
  "front_right_fender",
  "rear_left_quarter",
  "rear_right_quarter",
  "left_headlight",
  "right_headlight",
  "left_taillight",
  "right_taillight",
  "grille",
  "windshield",
  "rear_glass",
  "side_mirror_left",
  "side_mirror_right",
  "wheel_arch",
] as const;

const SEVERITIES = ["minor", "moderate", "severe"] as const;

type DraftMeta = {
  damageType: string;
  part: string;
  severity: "minor" | "moderate" | "severe";
  note: string;
};

type Point = { x: number; y: number };

const defaultMeta: DraftMeta = {
  damageType: "surface_scratch",
  part: "front_bumper",
  severity: "moderate",
  note: "",
};

function classFromDamageType(damageType: string): "scratch" | "dent" | "crack" {
  if (damageType.includes("scratch") || damageType.includes("scuff")) return "scratch";
  if (
    damageType === "dent" ||
    damageType === "crease" ||
    damageType === "panel_deformation" ||
    damageType === "bumper_deformation" ||
    damageType === "misalignment"
  ) {
    return "dent";
  }
  return "crack";
}

function rectFromPoints(a: Point, b: Point) {
  const x = Math.min(a.x, b.x);
  const y = Math.min(a.y, b.y);
  const w = Math.abs(a.x - b.x);
  const h = Math.abs(a.y - b.y);
  return { x, y, w, h };
}

export function TrainingStudio() {
  const [images, setImages] = useState<TrainingImage[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [labels, setLabels] = useState<TrainingLabel[]>([]);
  const [meta, setMeta] = useState<DraftMeta>(defaultMeta);
  const [busy, setBusy] = useState(false);
  const [note, setNote] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [exportResult, setExportResult] = useState<TrainingExportResult | null>(null);
  const [trainingStatus, setTrainingStatus] = useState<TrainingStatus | null>(null);
  const [selectedLabelIdx, setSelectedLabelIdx] = useState<number | null>(null);
  const [drawStart, setDrawStart] = useState<Point | null>(null);
  const [drawNow, setDrawNow] = useState<Point | null>(null);

  const imgRef = useRef<HTMLImageElement | null>(null);

  const selected = useMemo(
    () => images.find((i) => i.image_id === selectedId) ?? null,
    [images, selectedId],
  );

  useEffect(() => {
    void refreshImages();
  }, []);

  async function refreshImages() {
    setErr(null);
    try {
      const data = await getTrainingImages();
      setImages(data);
      setSelectedId((prev) => prev ?? data[0]?.image_id ?? null);
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    }
  }

  useEffect(() => {
    if (!selectedId) {
      setLabels([]);
      setSelectedLabelIdx(null);
      return;
    }
    let alive = true;
    setBusy(true);
    setErr(null);
    getTrainingLabels(selectedId)
      .then((res) => {
        if (!alive) return;
        setLabels(res);
        setSelectedLabelIdx(null);
      })
      .catch((e) => {
        if (!alive) return;
        setErr(e instanceof Error ? e.message : String(e));
        setLabels([]);
      })
      .finally(() => {
        if (alive) setBusy(false);
      });
    return () => {
      alive = false;
    };
  }, [selectedId]);

  function displayedToNatural(pt: Point): Point {
    const img = imgRef.current;
    if (!img) return pt;
    const rect = img.getBoundingClientRect();
    if (rect.width <= 0 || rect.height <= 0) return pt;
    const sx = img.naturalWidth / rect.width;
    const sy = img.naturalHeight / rect.height;
    return { x: Math.round(pt.x * sx), y: Math.round(pt.y * sy) };
  }

  function naturalToDisplayed(pt: Point): Point {
    const img = imgRef.current;
    if (!img) return pt;
    const rect = img.getBoundingClientRect();
    if (img.naturalWidth <= 0 || img.naturalHeight <= 0) return pt;
    const sx = rect.width / img.naturalWidth;
    const sy = rect.height / img.naturalHeight;
    return { x: pt.x * sx, y: pt.y * sy };
  }

  function pointerRelative(ev: React.PointerEvent<HTMLDivElement>): Point | null {
    const img = imgRef.current;
    if (!img) return null;
    const rect = img.getBoundingClientRect();
    const x = ev.clientX - rect.left;
    const y = ev.clientY - rect.top;
    if (x < 0 || y < 0 || x > rect.width || y > rect.height) return null;
    return { x, y };
  }

  function onCanvasPointerDown(ev: React.PointerEvent<HTMLDivElement>) {
    if (ev.button !== 0) return;
    const p = pointerRelative(ev);
    if (!p) return;
    setDrawStart(p);
    setDrawNow(p);
    setSelectedLabelIdx(null);
  }

  function onCanvasPointerMove(ev: React.PointerEvent<HTMLDivElement>) {
    if (!drawStart) return;
    const p = pointerRelative(ev);
    if (!p) return;
    setDrawNow(p);
  }

  function onCanvasPointerUp() {
    if (!drawStart || !drawNow) {
      setDrawStart(null);
      setDrawNow(null);
      return;
    }
    const disp = rectFromPoints(drawStart, drawNow);
    if (disp.w < 6 || disp.h < 6) {
      setDrawStart(null);
      setDrawNow(null);
      return;
    }
    const topLeft = displayedToNatural({ x: disp.x, y: disp.y });
    const bottomRight = displayedToNatural({ x: disp.x + disp.w, y: disp.y + disp.h });
    const x = Math.max(0, Math.min(topLeft.x, bottomRight.x));
    const y = Math.max(0, Math.min(topLeft.y, bottomRight.y));
    const w = Math.max(1, Math.abs(bottomRight.x - topLeft.x));
    const h = Math.max(1, Math.abs(bottomRight.y - topLeft.y));

    const modelClass = classFromDamageType(meta.damageType);
    setLabels((prev) => [
      ...prev,
      {
        type: modelClass,
        damageType: meta.damageType,
        part: meta.part,
        severity: meta.severity,
        note: meta.note,
        x,
        y,
        w,
        h,
      },
    ]);
    setDrawStart(null);
    setDrawNow(null);
  }

  async function saveCurrent() {
    if (!selectedId) return;
    setBusy(true);
    setErr(null);
    setNote(null);
    try {
      const saved = await saveTrainingLabels(selectedId, labels);
      setLabels(saved);
      setNote(`Saved ${saved.length} labels for image ${selectedId.slice(0, 8)}…`);
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  }


  async function triggerTraining() {
    if (!exportResult?.data_yaml) {
      setErr("Export dataset first to generate data.yaml");
      return;
    }
    setBusy(true);
    setErr(null);
    setNote(null);
    try {
      const start = await startTraining(exportResult.data_yaml);
      setNote(start.message);
      const status = await getTrainingStatus();
      setTrainingStatus(status);
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  }

  async function exportYolo() {
    setBusy(true);
    setErr(null);
    setNote(null);
    try {
      const res = await exportTrainingDataset();
      setExportResult(res);
      setNote(`Exported ${res.labeled_images} labeled images to ${res.dataset_path}`);
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  }


  useEffect(() => {
    let timer: number | undefined;
    const tick = async () => {
      try {
        const status = await getTrainingStatus();
        setTrainingStatus(status);
      } catch {
        // keep UI usable even if backend training endpoint is unavailable
      }
      timer = window.setTimeout(tick, 2500);
    };
    void tick();
    return () => {
      if (timer) window.clearTimeout(timer);
    };
  }, []);

  function removeLabel(idx: number) {
    setLabels((prev) => prev.filter((_, i) => i !== idx));
    if (selectedLabelIdx === idx) setSelectedLabelIdx(null);
  }

  const drawingPreview =
    drawStart && drawNow ? rectFromPoints(drawStart, drawNow) : null;

  return (
    <div className="card">
      <div className="cardHeader">
        <div>
          <div className="cardTitle">Training Studio</div>
          <div className="cardSub">Draw boxes on image, label damage type + part + severity, then export YOLO.</div>
        </div>
      </div>

      <div className="taxonomyNote">
        <strong>Taxonomy:</strong> 12 damage subtypes + 23 vehicle parts.
        YOLO export is mapped to 3 model classes (<span className="mono">scratch/dent/crack</span>) for current pricing model.
      </div>

      <div className="trainingGrid">
        <div className="trainingList">
          <div className="muted">Uploaded images ({images.length})</div>
          <div className="trainingItems">
            {images.map((img) => (
              <button
                key={img.image_id}
                type="button"
                className={`trainingItem ${img.image_id === selectedId ? "active" : ""}`}
                onClick={() => setSelectedId(img.image_id)}
              >
                <div className="mono">{img.image_id.slice(0, 8)}…</div>
                <div>{img.original_filename}</div>
                <div className="muted">{img.width} x {img.height}</div>
              </button>
            ))}
          </div>
        </div>

        <div>
          {selected ? (
            <>
              <div
                className="annotatorSurface"
                onPointerDown={onCanvasPointerDown}
                onPointerMove={onCanvasPointerMove}
                onPointerUp={onCanvasPointerUp}
              >
                <img
                  ref={imgRef}
                  className="trainingImage"
                  src={`/api/v1/training/images/${encodeURIComponent(selected.image_id)}/content`}
                  alt={selected.original_filename}
                  draggable={false}
                />

                {labels.map((l, idx) => {
                  const p1 = naturalToDisplayed({ x: l.x, y: l.y });
                  const p2 = naturalToDisplayed({ x: l.x + l.w, y: l.y + l.h });
                  const r = rectFromPoints(p1, p2);
                  return (
                    <button
                      key={`${l.type}-${idx}`}
                      type="button"
                      className={`labelRect ${selectedLabelIdx === idx ? "active" : ""}`}
                      style={{ left: r.x, top: r.y, width: r.w, height: r.h }}
                      onClick={(e) => {
                        e.stopPropagation();
                        setSelectedLabelIdx(idx);
                      }}
                      title={`${l.damageType} • ${l.part} • ${l.severity}`}
                    >
                      <span>{l.damageType}</span>
                    </button>
                  );
                })}

                {drawingPreview ? (
                  <div
                    className="labelRect draft"
                    style={{
                      left: drawingPreview.x,
                      top: drawingPreview.y,
                      width: drawingPreview.w,
                      height: drawingPreview.h,
                    }}
                  />
                ) : null}
              </div>

              <div className="trainingFormRow trainingFormRowWide">
                <label>
                  Damage type
                  <select
                    value={meta.damageType}
                    onChange={(e) => setMeta((m) => ({ ...m, damageType: e.target.value }))}
                  >
                    {DAMAGE_TYPES.map((d) => (
                      <option key={d} value={d}>{d}</option>
                    ))}
                  </select>
                </label>

                <label>
                  Part
                  <select value={meta.part} onChange={(e) => setMeta((m) => ({ ...m, part: e.target.value }))}>
                    {PARTS.map((p) => (
                      <option key={p} value={p}>{p}</option>
                    ))}
                  </select>
                </label>

                <label>
                  Severity
                  <select
                    value={meta.severity}
                    onChange={(e) => setMeta((m) => ({ ...m, severity: e.target.value as DraftMeta["severity"] }))}
                  >
                    {SEVERITIES.map((s) => (
                      <option key={s} value={s}>{s}</option>
                    ))}
                  </select>
                </label>

                <label>
                  Note
                  <input
                    type="text"
                    value={meta.note}
                    onChange={(e) => setMeta((m) => ({ ...m, note: e.target.value }))}
                    placeholder="optional inspector note"
                  />
                </label>
              </div>

              <table className="table">
                <thead>
                  <tr>
                    <th>Class</th>
                    <th>Damage</th>
                    <th>Part</th>
                    <th>Severity</th>
                    <th>Box (x,y,w,h)</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {labels.map((l, idx) => (
                    <tr key={`${l.type}-${idx}`} className={selectedLabelIdx === idx ? "activeRow" : ""}>
                      <td>{l.type}</td>
                      <td>{l.damageType}</td>
                      <td>{l.part}</td>
                      <td>{l.severity}</td>
                      <td className="mono">{l.x}, {l.y}, {l.w}, {l.h}</td>
                      <td>
                        <button type="button" onClick={() => removeLabel(idx)}>Remove</button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>

              <div className="trainingActions">
                <button type="button" disabled={busy || !labels.length} onClick={saveCurrent}>
                  Save Labels
                </button>
                <button type="button" disabled={busy} onClick={exportYolo}>
                  Export YOLO Dataset
                </button>
                <button type="button" disabled={busy || !exportResult?.data_yaml} onClick={triggerTraining}>
                  Train Model
                </button>
              </div>

              {exportResult ? (
                <div className="muted mono">
                  classes: {exportResult.class_names.join(", ")}<br />
                  data.yaml: {exportResult.data_yaml}
                </div>
              ) : null}

              {trainingStatus ? (
                <div className="trainingStatus">
                  <div><strong>Training:</strong> {trainingStatus.running ? "running" : "idle"}</div>
                  {trainingStatus.exit_code !== undefined ? <div>exit_code: {trainingStatus.exit_code}</div> : null}
                  {trainingStatus.best_pt ? <div>best_pt: <span className="mono">{trainingStatus.best_pt}</span></div> : null}
                  {trainingStatus.last_error ? <div className="errMsg">{trainingStatus.last_error}</div> : null}
                  <pre className="trainingLog">{(trainingStatus.log_tail ?? []).slice(-20).join("\n")}</pre>
                </div>
              ) : null}
            </>
          ) : (
            <div className="muted">Upload images first, then label here.</div>
          )}
        </div>
      </div>

      {note ? <div className="okMsg">{note}</div> : null}
      {err ? <div className="errMsg">{err}</div> : null}
    </div>
  );
}
