import { useEffect, useState } from "react";
import { getAnalyticsSummary, type AnalyticsSummary } from "../api";

export function Dashboard() {
  const [summary, setSummary] = useState<AnalyticsSummary | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getAnalyticsSummary(30)
      .then(setSummary)
      .catch((e) => setError(e instanceof Error ? e.message : String(e)));
  }, []);

  const files = summary?.images ?? 0;
  const detections = summary?.detections ?? 0;
  const total = summary?.total_cost_nis ?? summary?.total_cost ?? 0;
  const currency = summary?.currency ?? "ILS";
  const moneyPrefix = currency === "ILS" ? "₪" : "";

  return (
    <div className="grid">
      <div className="kpiGrid">
        <div className="kpiCard">
          <div className="kpiLabel">Last Upload</div>
          <div className="kpiValue">{files}</div>
          <div className="muted">images</div>
        </div>
        <div className="kpiCard">
          <div className="kpiLabel">Last Detections</div>
          <div className="kpiValue">{detections}</div>
          <div className="muted">damage regions</div>
        </div>
        <div className="kpiCard">
          <div className="kpiLabel">Estimated Total</div>
          <div className="kpiValue">{moneyPrefix}{total} {currency}</div>
          <div className="muted">rule engine output</div>
        </div>
      </div>

      <div className="card">
        <div className="cardTitle">Welcome</div>
        <div className="cardSub">
          Use <strong>Upload & Test</strong> for live inference, then <strong>Training Studio</strong> to annotate data and train your next model.
        </div>
        {summary ? (
          <div className="cardSub">
            Top damage types:{" "}
            {summary.top_damage_types.length
              ? summary.top_damage_types.map((d) => `${d.name} (${d.count})`).join(", ")
              : "No detections yet"}
          </div>
        ) : null}
        {error ? <div className="errMsg">{error}</div> : null}
      </div>
    </div>
  );
}
