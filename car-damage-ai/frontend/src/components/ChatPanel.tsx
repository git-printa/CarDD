import { useEffect, useState } from "react";
import { chatQuery, getChatRuntime, getChatSkills, type ChatResponse, type ChatRuntime, type ChatSkill } from "../api";

type ChatMessage = {
  role: "user" | "assistant";
  text: string;
  meta?: string;
};

const ROLE_OPTIONS = ["viewer", "analyst", "admin"] as const;

export function ChatPanel() {
  const [prompt, setPrompt] = useState("");
  const [role, setRole] = useState<(typeof ROLE_OPTIONS)[number]>("viewer");
  const [days, setDays] = useState(30);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [skills, setSkills] = useState<ChatSkill[]>([]);
  const [runtime, setRuntime] = useState<ChatRuntime | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      role: "assistant",
      text: "Ask me anything about this app (usage, troubleshooting, deployment) or analytics questions like top damage types this month.",
    },
  ]);

  useEffect(() => {
    getChatSkills()
      .then(setSkills)
      .catch(() => {
        // non-fatal for chat usage
      });
    getChatRuntime()
      .then(setRuntime)
      .catch(() => {
        // non-fatal for chat usage
      });
  }, []);

  async function send() {
    if (!prompt.trim()) return;
    setBusy(true);
    setError(null);
    const userPrompt = prompt.trim();
    setPrompt("");
    setMessages((prev) => [...prev, { role: "user", text: userPrompt }]);
    try {
      const response: ChatResponse = await chatQuery(userPrompt, role, days);
      setMessages((prev) => [
        ...prev,
        {
          role: "assistant",
          text: response.answer,
          meta: `${response.skill_used} · ${response.time_range_days}d · ${response.sources.join(", ")}`,
        },
      ]);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="grid">
      <div className="card">
        <div className="cardHeader">
          <div>
            <div className="cardTitle">Assistant Chat</div>
            <div className="cardSub">General assistant + analytics insights from your persisted data.</div>
          </div>
        </div>

        <div className="chatControls">
          <label>
            Role
            <select value={role} onChange={(e) => setRole(e.target.value as (typeof ROLE_OPTIONS)[number])}>
              {ROLE_OPTIONS.map((r) => (
                <option key={r} value={r}>
                  {r}
                </option>
              ))}
            </select>
          </label>
          <label>
            Time range (days)
            <input
              type="number"
              min={1}
              max={3650}
              value={days}
              onChange={(e) => setDays(Number.isNaN(Number(e.target.value)) ? 30 : Number(e.target.value))}
            />
          </label>
        </div>

        {runtime ? (
          <div className="chatRuntime muted">
            mode: <span className="mono">{runtime.chat_mode}</span> · model:{" "}
            <span className="mono">{runtime.ollama_model}</span> · ollama:{" "}
            <span className={runtime.ollama_available ? "okMsgInline" : "errMsgInline"}>
              {runtime.ollama_available ? "online" : "offline"}
            </span>
          </div>
        ) : null}

        <div className="chatList">
          {messages.map((message, idx) => (
            <div key={`${message.role}-${idx}`} className={`chatBubble ${message.role}`}>
              <div>{message.text}</div>
              {message.meta ? <div className="chatMeta">{message.meta}</div> : null}
            </div>
          ))}
        </div>

        <div className="chatComposer">
          <input
            type="text"
            value={prompt}
            onChange={(e) => setPrompt(e.target.value)}
            placeholder="Example: why is training slow on my machine?"
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                void send();
              }
            }}
          />
          <button type="button" disabled={busy || !prompt.trim()} onClick={() => void send()}>
            {busy ? "Thinking..." : "Send"}
          </button>
        </div>

        {error ? <div className="errMsg">{error}</div> : null}
      </div>

      <div className="card">
        <div className="cardTitle">Skill Set</div>
        <div className="cardSub">Backend tools available to chat orchestration.</div>
        <table className="table">
          <thead>
            <tr>
              <th>Skill</th>
              <th>Description</th>
              <th>Role</th>
            </tr>
          </thead>
          <tbody>
            {skills.map((s) => (
              <tr key={s.id}>
                <td className="mono">{s.id}</td>
                <td>{s.description}</td>
                <td>{s.requiredRole}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
