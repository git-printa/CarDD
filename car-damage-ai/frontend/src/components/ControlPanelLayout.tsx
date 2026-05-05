import { ReactNode } from "react";

export type PanelScreen = "dashboard" | "assess" | "studio" | "chat";

type Props = {
  screen: PanelScreen;
  onScreen: (s: PanelScreen) => void;
  children: ReactNode;
};

const menu: Array<{ id: PanelScreen; title: string; sub: string }> = [
  { id: "dashboard", title: "Dashboard", sub: "Overview and quick actions" },
  { id: "assess", title: "Upload & Test", sub: "Run inference and estimate cost" },
  { id: "studio", title: "Training Studio", sub: "Annotate and train models" },
  { id: "chat", title: "Chat Analytics", sub: "Ask the AI assistant" },
];

export function ControlPanelLayout({ screen, onScreen, children }: Props) {
  return (
    <div className="cpLayout">
      <aside className="cpSidebar">
        <div className="cpLogo">Car Damage Control</div>
        <div className="cpMenu">
          {menu.map((m) => (
            <button
              key={m.id}
              type="button"
              className={`cpNavBtn ${screen === m.id ? "active" : ""}`}
              onClick={() => onScreen(m.id)}
            >
              <div className="cpNavTitle">{m.title}</div>
              <div className="cpNavSub">{m.sub}</div>
            </button>
          ))}
        </div>
      </aside>
      <section className="cpMain">{children}</section>
    </div>
  );
}
