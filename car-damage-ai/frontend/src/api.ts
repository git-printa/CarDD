const API_PREFIX = "/api/v1";

async function parseError(res: Response, fallback: string): Promise<never> {
  const text = await res.text();
  if (res.status === 502 || res.status === 503 || res.status === 504) {
    throw new Error(
      `Backend is temporarily unavailable (${res.status}). Wait a few seconds and retry.`
    );
  }
  if (text && text.trim().startsWith("<")) {
    throw new Error(`${fallback} (${res.status}).`);
  }
  throw new Error(text || `${fallback} (${res.status})`);
}

export type AssessmentItem = {
  imageId: string;
  originalFilename: string;
  storedFilename: string;
  contentType: string;
  sizeBytes: number;
  width: number;
  height: number;
  status: string;
  uploadedAt: string;
};

export type UploadResponse = {
  batchId: string;
  vehicleId: string;
  totalFiles: number;
  createdAt: string;
  items: AssessmentItem[];
};

export type Damage = {
  type: string;
  confidence: number;
  box: number[];
  part?: string;
  estimated_cost: number;
  estimated_cost_nis?: number;
  image_id: string;
};

export type AiStack = {
  inference_mode: string;
  model_path?: string | null;
  mock_layout?: string | null;
  note: string;
};

export type AnalyzeResponse = {
  damages: Damage[];
  total_cost: number;
  total_cost_nis?: number;
  currency?: string;
  ai_stack?: AiStack;
};

export type TrainingImage = {
  image_id: string;
  batch_id: string;
  original_filename: string;
  width: number;
  height: number;
};

export type TrainingLabel = {
  type: "scratch" | "dent" | "crack";
  damageType: string;
  part: string;
  severity: "minor" | "moderate" | "severe";
  note?: string;
  x: number;
  y: number;
  w: number;
  h: number;
};

export type TrainingExportResult = {
  dataset_path: string;
  data_yaml: string;
  labeled_images: number;
  class_names: string[];
};

export type TrainingStartResult = {
  running: boolean;
  message: string;
  run_id?: string;
};

export type TrainingStatus = {
  running: boolean;
  started_at?: string;
  finished_at?: string;
  exit_code?: number;
  command?: string;
  data_yaml?: string;
  best_pt?: string;
  last_error?: string;
  run_id?: string;
  log_tail: string[];
};

export type AnalyticsBreakdownItem = {
  name: string;
  count: number;
  total_cost: number;
  total_cost_nis?: number;
};

export type AnalyticsSummary = {
  time_range_days: number;
  batches: number;
  images: number;
  detections: number;
  total_cost: number;
  total_cost_nis?: number;
  currency?: string;
  top_damage_types: AnalyticsBreakdownItem[];
  cost_by_part: AnalyticsBreakdownItem[];
};

export type ChatSkill = {
  id: string;
  description: string;
  requiredRole: string;
};

export type ChatRuntime = {
  chat_mode: string;
  ollama_url: string;
  ollama_model: string;
  ollama_available: boolean;
};

export type ChatResponse = {
  answer: string;
  query_type: string;
  skill_used: string;
  time_range_days: number;
  sources: string[];
  data: Record<string, unknown>;
};

export async function uploadAssessment(
  files: File[],
  vehicleId: string | undefined,
): Promise<UploadResponse> {
  const fd = new FormData();
  if (vehicleId && vehicleId.trim()) {
    fd.append("vehicleId", vehicleId.trim());
  }
  for (const f of files) {
    fd.append("files", f);
  }

  const res = await fetch(`${API_PREFIX}/assessments/upload`, {
    method: "POST",
    body: fd,
  });
  if (!res.ok) {
    await parseError(res, "Upload failed");
  }
  return (await res.json()) as UploadResponse;
}

export async function analyzeAssessment(batchId: string): Promise<AnalyzeResponse> {
  const res = await fetch(`${API_PREFIX}/assessments/${encodeURIComponent(batchId)}/analyze`, {
    method: "POST",
  });
  if (!res.ok) {
    await parseError(res, "Analyze failed");
  }
  return (await res.json()) as AnalyzeResponse;
}

export async function getTrainingImages(): Promise<TrainingImage[]> {
  const res = await fetch(`${API_PREFIX}/training/images`);
  if (!res.ok) {
    await parseError(res, "Training images failed");
  }
  return (await res.json()) as TrainingImage[];
}

export async function getTrainingLabels(imageId: string): Promise<TrainingLabel[]> {
  const res = await fetch(`${API_PREFIX}/training/images/${encodeURIComponent(imageId)}/labels`);
  if (!res.ok) {
    await parseError(res, "Load labels failed");
  }
  const body = (await res.json()) as { labels: TrainingLabel[] };
  return body.labels ?? [];
}

export async function saveTrainingLabels(imageId: string, labels: TrainingLabel[]): Promise<TrainingLabel[]> {
  const res = await fetch(`${API_PREFIX}/training/images/${encodeURIComponent(imageId)}/labels`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ labels }),
  });
  if (!res.ok) {
    await parseError(res, "Save labels failed");
  }
  const body = (await res.json()) as { labels: TrainingLabel[] };
  return body.labels ?? [];
}

export async function exportTrainingDataset(): Promise<TrainingExportResult> {
  const res = await fetch(`${API_PREFIX}/training/export-yolo`, { method: "POST" });
  if (!res.ok) {
    await parseError(res, "Export failed");
  }
  return (await res.json()) as TrainingExportResult;
}

export async function startTraining(dataYaml: string): Promise<TrainingStartResult> {
  const res = await fetch(`${API_PREFIX}/training/start`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ data_yaml: dataYaml }),
  });
  if (!res.ok) {
    await parseError(res, "Start training failed");
  }
  return (await res.json()) as TrainingStartResult;
}

export async function getTrainingStatus(): Promise<TrainingStatus> {
  const res = await fetch(`${API_PREFIX}/training/status`);
  if (!res.ok) {
    await parseError(res, "Training status failed");
  }
  return (await res.json()) as TrainingStatus;
}

export async function getAnalyticsSummary(days = 30): Promise<AnalyticsSummary> {
  const res = await fetch(`${API_PREFIX}/analytics/summary?days=${encodeURIComponent(String(days))}`);
  if (!res.ok) {
    await parseError(res, "Analytics summary failed");
  }
  return (await res.json()) as AnalyticsSummary;
}

export async function getChatSkills(): Promise<ChatSkill[]> {
  const res = await fetch(`${API_PREFIX}/chat/skills`);
  if (!res.ok) {
    await parseError(res, "Chat skills failed");
  }
  return (await res.json()) as ChatSkill[];
}

export async function getChatRuntime(): Promise<ChatRuntime> {
  const res = await fetch(`${API_PREFIX}/chat/runtime`);
  if (!res.ok) {
    await parseError(res, "Chat runtime failed");
  }
  return (await res.json()) as ChatRuntime;
}

export async function chatQuery(prompt: string, role: string, timeRangeDays: number): Promise<ChatResponse> {
  const res = await fetch(`${API_PREFIX}/chat/query`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Role": role,
    },
    body: JSON.stringify({
      prompt,
      time_range_days: timeRangeDays,
    }),
  });
  if (!res.ok) {
    await parseError(res, "Chat query failed");
  }
  return (await res.json()) as ChatResponse;
}
