import type { AiStack, Damage } from "../api";

type Props = {
  damages: Damage[];
  totalCost: number;
  currency?: string;
  aiStack?: AiStack;
};

export function ResultsTable({ damages, totalCost, currency, aiStack }: Props) {
  const resolvedCurrency = currency ?? "ILS";
  const moneyPrefix = resolvedCurrency === "ILS" ? "₪" : "";
  return (
    <div className="card">
      {aiStack ? (
        <div className="inferenceBanner" role="status">
          <div className="inferenceBannerTitle">
            AI: <span className="mono">{aiStack.inference_mode}</span>
            {aiStack.mock_layout ? (
              <>
                {" "}
                · layout <span className="mono">{aiStack.mock_layout}</span>
              </>
            ) : null}
            {aiStack.model_path ? (
              <>
                {" "}
                · <span className="mono">{aiStack.model_path}</span>
              </>
            ) : null}
          </div>
          <p className="inferenceBannerNote">{aiStack.note}</p>
        </div>
      ) : null}
      <div className="cardHeader">
        <div>
          <div className="cardTitle">Results</div>
          <div className="cardSub">Per-damage estimates from rule engine</div>
        </div>
        <div className="pill">
          Total: <strong>{moneyPrefix}{totalCost} {resolvedCurrency}</strong>
        </div>
      </div>

      <div className="tableWrap">
        <table className="table">
          <thead>
            <tr>
              <th>Image</th>
              <th>Type</th>
              <th>Part</th>
              <th>Confidence</th>
              <th>Est. cost</th>
              <th>Box (x,y,w,h)</th>
            </tr>
          </thead>
          <tbody>
            {damages.map((d, idx) => (
              <tr key={`${d.image_id}-${idx}`}>
                <td className="mono">{d.image_id.slice(0, 8)}…</td>
                <td>{d.type}</td>
                <td>{d.part ?? "—"}</td>
                <td>{(d.confidence * 100).toFixed(1)}%</td>
                <td>{moneyPrefix}{d.estimated_cost_nis ?? d.estimated_cost}</td>
                <td className="mono">{d.box?.join(", ") ?? "—"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
