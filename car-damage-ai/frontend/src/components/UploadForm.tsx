type Props = {
  disabled: boolean;
  onSubmit: (files: File[], vehicleId: string) => void;
};

export function UploadForm({ disabled, onSubmit }: Props) {
  return (
    <form
      className="card"
      onSubmit={(e) => {
        e.preventDefault();
        const form = e.currentTarget;
        const fd = new FormData(form);
        const vehicleId = String(fd.get("vehicleId") ?? "");
        const input = form.elements.namedItem("files") as HTMLInputElement;
        const files = input.files ? Array.from(input.files) : [];
        onSubmit(files, vehicleId);
      }}
    >
      <div className="cardHeader">
        <div>
          <div className="cardTitle">Upload images</div>
          <div className="cardSub">JPEG / PNG, multiple files supported</div>
        </div>
      </div>

      <div className="field">
        <label htmlFor="vehicleId">Vehicle ID (optional)</label>
        <input id="vehicleId" name="vehicleId" type="text" placeholder="VH-123" disabled={disabled} />
      </div>

      <div className="field">
        <label htmlFor="files">Images</label>
        <input id="files" name="files" type="file" accept="image/*" multiple required disabled={disabled} />
      </div>

      <div className="actions">
        <button className="btn primary" type="submit" disabled={disabled}>
          Upload &amp; analyze
        </button>
      </div>
    </form>
  );
}
