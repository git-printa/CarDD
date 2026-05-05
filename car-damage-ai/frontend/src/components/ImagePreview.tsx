import { useEffect, useRef, useState } from "react";
import type { AssessmentItem, Damage } from "../api";

type Props = {
  batchId: string;
  items: AssessmentItem[];
  filesByImageId: Map<string, File>;
  damages: Damage[];
};

type Layout = {
  overlayW: number;
  overlayH: number;
  natW: number;
  natH: number;
};

export function ImagePreview({ batchId, items, filesByImageId, damages }: Props) {
  const [activeId, setActiveId] = useState<string | null>(() => items[0]?.imageId ?? null);
  const [objectUrl, setObjectUrl] = useState<string | null>(null);

  const activeItem = items.find((i) => i.imageId === activeId) ?? items[0];
  const file = activeItem ? filesByImageId.get(activeItem.imageId) : undefined;

  useEffect(() => {
    setActiveId(items[0]?.imageId ?? null);
  }, [batchId]);

  useEffect(() => {
    if (!file) {
      setObjectUrl(null);
      return;
    }
    const url = URL.createObjectURL(file);
    setObjectUrl(url);
    return () => {
      URL.revokeObjectURL(url);
    };
  }, [file]);

  if (!activeItem || !file || !objectUrl) {
    return (
      <div className="card">
        <div className="cardHeader">
          <div className="cardTitle">Preview</div>
        </div>
        <div className="muted">Upload images to preview overlays.</div>
      </div>
    );
  }

  const localDamages = damages.filter((d) => d.image_id === activeItem.imageId);

  return (
    <div className="card">
      <div className="cardHeader">
        <div>
          <div className="cardTitle">Preview</div>
          <div className="cardSub">{activeItem.originalFilename}</div>
        </div>
      </div>

      <div className="tabs">
        {items.map((it) => (
          <button
            key={it.imageId}
            type="button"
            className={`tab ${it.imageId === activeItem.imageId ? "active" : ""}`}
            onClick={() => setActiveId(it.imageId)}
          >
            {it.originalFilename}
          </button>
        ))}
      </div>

      <PreviewCanvas key={objectUrl} imageUrl={objectUrl} damages={localDamages} />
    </div>
  );
}

function PreviewCanvas({ imageUrl, damages }: { imageUrl: string; damages: Damage[] }) {
  const imgRef = useRef<HTMLImageElement | null>(null);
  const [layout, setLayout] = useState<Layout | null>(null);

  useEffect(() => {
    setLayout(null);
  }, [imageUrl]);

  const recompute = () => {
    const im = imgRef.current;
    if (!im) {
      return;
    }
    setLayout({
      overlayW: im.clientWidth,
      overlayH: im.clientHeight,
      natW: Math.max(1, im.naturalWidth),
      natH: Math.max(1, im.naturalHeight),
    });
  };

  useEffect(() => {
    const onResize = () => recompute();
    window.addEventListener("resize", onResize);
    return () => window.removeEventListener("resize", onResize);
  }, [imageUrl]);

  const labelSize = layout ? Math.max(12, Math.round(layout.natH * 0.03)) : 14;

  return (
    <div className="previewStage">
      <img
        ref={imgRef}
        className="previewImg"
        src={imageUrl}
        alt="preview"
        onLoad={recompute}
      />
      {layout ? (
        <svg
          className="previewSvg"
          width={layout.overlayW}
          height={layout.overlayH}
          viewBox={`0 0 ${layout.natW} ${layout.natH}`}
          preserveAspectRatio="none"
        >
          {damages.map((d, idx) => {
            const b = d.box ?? [];
            if (b.length !== 4) {
              return null;
            }
            const [x, y, w, h] = b;
            return (
              <g key={`${d.type}-${idx}`}>
                <rect x={x} y={y} width={w} height={h} className="bbox" />
                <text x={x + 6} y={y + labelSize + 4} className="bboxLabel" style={{ fontSize: labelSize }}>
                  {d.type} (₪{d.estimated_cost_nis ?? d.estimated_cost})
                </text>
              </g>
            );
          })}
        </svg>
      ) : null}
    </div>
  );
}
