import { useState } from "react";
import { analyzeAssessment, uploadAssessment, type AnalyzeResponse, type AssessmentItem, type UploadResponse } from "./api";
import { ControlPanelLayout, type PanelScreen } from "./components/ControlPanelLayout";
import { ChatPanel } from "./components/ChatPanel";
import { Dashboard } from "./components/Dashboard";
import { ImagePreview } from "./components/ImagePreview";
import { ResultsTable } from "./components/ResultsTable";
import { TrainingStudio } from "./components/TrainingStudio";
import { UploadForm } from "./components/UploadForm";
import "./App.css";

export function App() {
  const [screen, setScreen] = useState<PanelScreen>("dashboard");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [upload, setUpload] = useState<UploadResponse | null>(null);
  const [analyze, setAnalyze] = useState<AnalyzeResponse | null>(null);
  const [filesByImageId, setFilesByImageId] = useState<Map<string, File>>(new Map());

  const items: AssessmentItem[] = upload?.items ?? [];

  const canPreview = Boolean(
    upload && analyze && upload.items.length > 0 && filesByImageId.size > 0,
  );

  return (
    <div className="page">
      <ControlPanelLayout screen={screen} onScreen={setScreen}>
        <header className="topbar">
          <div>
            <div className="brand">Car Damage AI</div>
            <div className="tagline">Control panel for assessment, training, and deployment</div>
          </div>
          <div className="topbarRight">
            {busy ? <span className="spinner" aria-label="Loading" /> : null}
          </div>
        </header>

        {screen === "dashboard" ? <Dashboard /> : null}

        {screen === "assess" ? <main className="grid">
        <UploadForm
          disabled={busy}
          onSubmit={async (files, vehicleId) => {
            setError(null);
            setAnalyze(null);
            setUpload(null);
            setFilesByImageId(new Map());
            if (files.length === 0) {
              setError("Please choose at least one image.");
              return;
            }
            setBusy(true);
            try {
              const map = new Map<string, File>();
              const up = await uploadAssessment(files, vehicleId);
              for (const it of up.items) {
                const match = files.find((f) => f.name === it.originalFilename);
                if (match) {
                  map.set(it.imageId, match);
                }
              }
              setFilesByImageId(map);
              setUpload(up);
              const an = await analyzeAssessment(up.batchId);
              setAnalyze(an);
            } catch (e) {
              setError(e instanceof Error ? e.message : String(e));
            } finally {
              setBusy(false);
            }
          }}
        />

        {error ? (
          <div className="card error">
            <div className="cardTitle">Something went wrong</div>
            <pre className="errBody">{error}</pre>
          </div>
        ) : null}

        {analyze ? (
          <ResultsTable
            key={upload?.batchId ?? "results"}
            damages={analyze.damages}
            totalCost={analyze.total_cost_nis ?? analyze.total_cost}
            currency={analyze.currency}
            aiStack={analyze.ai_stack}
          />
        ) : null}

        {canPreview && analyze && upload ? (
          <ImagePreview
            key={upload.batchId}
            batchId={upload.batchId}
            items={items}
            filesByImageId={filesByImageId}
            damages={analyze.damages}
          />
        ) : null}
        </main> : null}

        {screen === "studio" ? <TrainingStudio /> : null}
        {screen === "chat" ? <ChatPanel /> : null}

        <footer className="footer">
          <span className="muted">
            API: <span className="mono">/api/v1/assessments/upload</span> +{" "}
            <span className="mono">/api/v1/assessments/&lt;batchId&gt;/analyze</span>
          </span>
        </footer>
      </ControlPanelLayout>
    </div>
  );
}
