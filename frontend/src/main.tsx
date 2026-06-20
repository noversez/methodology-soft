import React, { useEffect, useMemo, useState, useTransition } from "react";
import { createRoot } from "react-dom/client";
import {
  Activity,
  Beaker,
  BookOpen,
  Camera,
  CheckCircle2,
  ClipboardList,
  FileText,
  GitBranch,
  Loader2,
  Moon,
  Plus,
  RadioTower,
  Shield,
  Sun,
  X,
} from "lucide-react";
import "./styles.css";

const API = "http://127.0.0.1:8080/api";

type Role = "DETECTIVE" | "ASSISTANT" | "INSPECTOR" | "AGENT" | "LAB_ANALYST" | "ADMIN";
type User = { id: string; login: string; role: Role; displayName: string };
type CaseFile = { id: string; registrationNumber: string; title: string; openedAt: string; priority: string; status: string; description: string };
type Evidence = { id: string; caseId: string; registrationNumber: string; name: string; type: string; importance: string; description: string; discoveryDateTime: string; latitude?: number; longitude?: number; locationTitle?: string; status: string };
type TaskItem = { id: string; caseId: string; title: string; description: string; assigneeId: string; priority: string; deadline: string; status: string; resultText?: string; resultEvidenceId?: string };
type LabRequest = { id: string; evidenceId: string; profile: string; questions: string; desiredDueDate: string; status: string; requesterId: string; labAssigneeId?: string; resultText?: string };
type Graph = { nodes: { type: string; id: string; label: string; status: string }[]; edges: { id: string; source: { type: string; id: string }; target: { type: string; id: string }; semanticType: string; confidence: string }[]; filtered: boolean; warning?: string };
type ReportPreview = { content: string; hasOpenLabRequests: boolean; warning?: string };
type ReportFile = { id: string; registrationNumber: string; status: string };
type FormKind = "case" | "evidence" | "agentResult";
type FieldErrors = Record<string, string>;
type ApiErrorBody = { code?: string; message?: string; details?: Record<string, unknown> };
type CaseDraft = { title: string; openedAt: string; priority: string; description: string };
type EvidenceDraft = { name: string; type: string; importance: string; description: string; discoveryDateTime: string; locationTitle: string };
type AgentResultDraft = { taskId: string; note: string; evidenceName: string; evidenceType: string; locationTitle: string };

const emptyCaseDraft = (): CaseDraft => ({
  title: "",
  openedAt: new Date().toISOString().slice(0, 16),
  priority: "MEDIUM",
  description: "",
});

const emptyEvidenceDraft = (): EvidenceDraft => ({
  name: "",
  type: "",
  importance: "MEDIUM",
  description: "",
  discoveryDateTime: new Date().toISOString().slice(0, 16),
  locationTitle: "",
});
const emptyAgentResultDraft = (): AgentResultDraft => ({ taskId: "", note: "", evidenceName: "", evidenceType: "цифровая", locationTitle: "" });

const NETWORK_MESSAGE = "Нестабильность сети, проверьте подключение к интернету";
const DRAFTS_KEY = "dims-form-drafts-v1";

class ApiRequestError extends Error {
  constructor(message: string, readonly code = "REQUEST_ERROR", readonly details: Record<string, unknown> = {}) {
    super(message);
  }
}

function loadDrafts(): { caseDraft: CaseDraft; evidenceDraft: EvidenceDraft; agentResultDraft: AgentResultDraft } {
  try {
    const saved = JSON.parse(localStorage.getItem(DRAFTS_KEY) ?? "null");
    if (saved?.version === 1) return { caseDraft: saved.caseDraft, evidenceDraft: saved.evidenceDraft, agentResultDraft: saved.agentResultDraft ?? emptyAgentResultDraft() };
  } catch {
    localStorage.removeItem(DRAFTS_KEY);
  }
  return { caseDraft: emptyCaseDraft(), evidenceDraft: emptyEvidenceDraft(), agentResultDraft: emptyAgentResultDraft() };
}

function required(value: string, label: string) {
  return value.trim() ? "" : `Заполните поле «${label}»`;
}

async function request<T>(path: string, userId?: string, init: RequestInit = {}): Promise<T> {
  const method = init.method?.toUpperCase() ?? "GET";
  const attempts = method === "GET" ? 2 : 1;
  for (let attempt = 0; attempt < attempts; attempt += 1) {
    try {
      const response = await fetch(`${API}${path}`, {
        ...init,
        signal: AbortSignal.timeout(method === "GET" ? 2350 : 5000),
        headers: {
          "Content-Type": "application/json",
          ...(userId ? { "X-User-Id": userId } : {}),
          ...(init.headers ?? {}),
        },
      });
      if (!response.ok) {
        if (method === "GET" && response.status >= 500 && attempt + 1 < attempts) {
          await new Promise((resolve) => setTimeout(resolve, 300));
          continue;
        }
        const error: ApiErrorBody = await response.json().catch(() => ({ message: "Ошибка запроса" }));
        throw new ApiRequestError(error.message ?? "Ошибка запроса", error.code, error.details);
      }
      return response.json();
    } catch (error) {
      if (error instanceof ApiRequestError) throw error;
      if (attempt + 1 === attempts) throw new ApiRequestError(NETWORK_MESSAGE, "NETWORK_ERROR");
      await new Promise((resolve) => setTimeout(resolve, 300));
    }
  }
  throw new ApiRequestError(NETWORK_MESSAGE, "NETWORK_ERROR");
}

function App() {
  const [users, setUsers] = useState<User[]>([]);
  const [userId, setUserId] = useState("");
  const [cases, setCases] = useState<CaseFile[]>([]);
  const [selectedCaseId, setSelectedCaseId] = useState("");
  const [evidence, setEvidence] = useState<Evidence[]>([]);
  const [tasks, setTasks] = useState<TaskItem[]>([]);
  const [myTasks, setMyTasks] = useState<TaskItem[]>([]);
  const [labs, setLabs] = useState<LabRequest[]>([]);
  const [labQueue, setLabQueue] = useState<LabRequest[]>([]);
  const [graph, setGraph] = useState<Graph | null>(null);
  const [preview, setPreview] = useState<ReportPreview | null>(null);
  const [approvedReport, setApprovedReport] = useState<ReportFile | null>(null);
  const [message, setMessage] = useState("");
  const [theme, setTheme] = useState<"light" | "dark">("light");
  const [activeForm, setActiveForm] = useState<FormKind | null>(null);
  const [caseDraft, setCaseDraft] = useState<CaseDraft>(() => loadDrafts().caseDraft);
  const [evidenceDraft, setEvidenceDraft] = useState<EvidenceDraft>(() => loadDrafts().evidenceDraft);
  const [agentResultDraft, setAgentResultDraft] = useState<AgentResultDraft>(() => loadDrafts().agentResultDraft);
  const [resultPhoto, setResultPhoto] = useState<File | null>(null);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [isOnline, setIsOnline] = useState(() => navigator.onLine);
  const [isPending, startTransition] = useTransition();

  const currentUser = users.find((user) => user.id === userId);
  const selectedCase = cases.find((item) => item.id === selectedCaseId);
  const firstEvidence = evidence[0];
  const visibleTasks = currentUser?.role === "AGENT" || currentUser?.role === "ASSISTANT" ? myTasks : tasks;
  const visibleLabs = currentUser?.role === "LAB_ANALYST" ? labQueue : labs;

  async function loadBase() {
    const [loadedUsers, loadedCases] = await Promise.all([
      request<User[]>("/users"),
      request<CaseFile[]>("/cases"),
    ]);
    setUsers(loadedUsers);
    setUserId((prev) => prev || loadedUsers[0]?.id || "");
    setCases(loadedCases);
    setSelectedCaseId((prev) => prev || loadedCases[0]?.id || "");
  }

  async function loadCaseDetails(caseId: string) {
    const [loadedEvidence, loadedTasks, loadedGraph] = await Promise.all([
      request<Evidence[]>(`/cases/${caseId}/evidence`),
      request<TaskItem[]>(`/cases/${caseId}/tasks`),
      request<Graph>(`/cases/${caseId}/graph`),
    ]);
    setEvidence(loadedEvidence);
    setTasks(loadedTasks);
    setGraph(loadedGraph);
    if (loadedEvidence[0]) {
      setLabs(await request<LabRequest[]>(`/evidence/${loadedEvidence[0].id}/lab-requests`));
    } else {
      setLabs([]);
    }
  }

  useEffect(() => {
    loadBase().catch((error) => setMessage(error.message));
  }, []);

  useEffect(() => {
    if (selectedCaseId) {
      setApprovedReport(null);
      setPreview(null);
      loadCaseDetails(selectedCaseId).catch((error) => setMessage(error.message));
    }
  }, [selectedCaseId]);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
  }, [theme]);

  useEffect(() => {
    if (!userId || !currentUser) return;
    if (currentUser.role === "AGENT" || currentUser.role === "ASSISTANT") {
      request<TaskItem[]>("/tasks/my", userId).then(setMyTasks).catch((error) => setMessage(error.message));
    } else {
      setMyTasks([]);
    }
    if (currentUser.role === "LAB_ANALYST") {
      request<LabRequest[]>("/lab-requests", userId).then(setLabQueue).catch((error) => setMessage(error.message));
    } else {
      setLabQueue([]);
    }
  }, [userId, currentUser?.role]);

  useEffect(() => {
    localStorage.setItem(DRAFTS_KEY, JSON.stringify({ version: 1, caseDraft, evidenceDraft, agentResultDraft }));
  }, [caseDraft, evidenceDraft, agentResultDraft]);

  useEffect(() => {
    const updateNetworkState = () => setIsOnline(navigator.onLine);
    window.addEventListener("online", updateNetworkState);
    window.addEventListener("offline", updateNetworkState);
    return () => {
      window.removeEventListener("online", updateNetworkState);
      window.removeEventListener("offline", updateNetworkState);
    };
  }, []);

  const agent = users.find((user) => user.role === "AGENT");

  async function createCase(draft: CaseDraft) {
    if (!userId) return;
    const created = await request<CaseFile>("/cases", userId, {
      method: "POST",
      body: JSON.stringify({
        ...draft,
        title: draft.title.trim(),
        description: draft.description.trim(),
        openedAt: new Date(draft.openedAt).toISOString(),
      }),
    });
    setCases((items) => [created, ...items]);
    setSelectedCaseId(created.id);
    setCaseDraft(emptyCaseDraft());
    setActiveForm(null);
    setMessage(`Создано дело ${created.registrationNumber}`);
  }

  async function createEvidence(draft: EvidenceDraft) {
    if (!userId || !selectedCaseId) return;
    const created = await request<Evidence>(`/cases/${selectedCaseId}/evidence`, userId, {
      method: "POST",
      body: JSON.stringify({
        ...draft,
        name: draft.name.trim(),
        type: draft.type.trim(),
        description: draft.description.trim(),
        discoveryDateTime: new Date(draft.discoveryDateTime).toISOString(),
        locationTitle: draft.locationTitle.trim() || undefined,
      }),
    });
    setEvidence((items) => [created, ...items]);
    setEvidenceDraft(emptyEvidenceDraft());
    setActiveForm(null);
    setMessage(`Добавлена улика ${created.registrationNumber}`);
    await loadCaseDetails(selectedCaseId);
  }

  function openForm(kind: FormKind) {
    setFieldErrors({});
    setActiveForm(kind);
  }

  function closeForm() {
    setFieldErrors({});
    setActiveForm(null);
  }

  function openAgentResult(task: TaskItem) {
    setFieldErrors({});
    setAgentResultDraft((draft) => ({ ...draft, taskId: task.id, evidenceName: draft.taskId === task.id ? draft.evidenceName : task.title }));
    setResultPhoto(null);
    setActiveForm("agentResult");
  }

  function handleFormError(error: unknown) {
    if (error instanceof ApiRequestError) {
      const errors = Object.fromEntries(Object.entries(error.details).filter(([, value]) => typeof value === "string")) as FieldErrors;
      setFieldErrors(errors);
      setMessage(error.message);
      return;
    }
    setMessage("Ошибка запроса");
  }

  function submitCase(event: React.FormEvent) {
    event.preventDefault();
    const errors = {
      title: required(caseDraft.title, "Название"),
      openedAt: required(caseDraft.openedAt, "Дата открытия"),
      description: required(caseDraft.description, "Описание"),
    };
    const visibleErrors = Object.fromEntries(Object.entries(errors).filter(([, value]) => value));
    setFieldErrors(visibleErrors);
    if (Object.keys(visibleErrors).length) return;
    startTransition(() => createCase(caseDraft).catch(handleFormError));
  }

  function submitEvidence(event: React.FormEvent) {
    event.preventDefault();
    const errors = {
      name: required(evidenceDraft.name, "Название"),
      type: required(evidenceDraft.type, "Тип"),
      description: required(evidenceDraft.description, "Описание"),
      discoveryDateTime: required(evidenceDraft.discoveryDateTime, "Дата обнаружения"),
    };
    const visibleErrors = Object.fromEntries(Object.entries(errors).filter(([, value]) => value));
    setFieldErrors(visibleErrors);
    if (Object.keys(visibleErrors).length) return;
    startTransition(() => createEvidence(evidenceDraft).catch(handleFormError));
  }

  function submitAgentResult(event: React.FormEvent) {
    event.preventDefault();
    const errors = {
      note: required(agentResultDraft.note, "Заметка"),
      evidenceName: required(agentResultDraft.evidenceName, "Название улики"),
      evidenceType: required(agentResultDraft.evidenceType, "Тип улики"),
    };
    const visibleErrors = Object.fromEntries(Object.entries(errors).filter(([, value]) => value));
    setFieldErrors(visibleErrors);
    if (Object.keys(visibleErrors).length || !userId) return;
    startTransition(async () => {
      try {
        if (resultPhoto) {
          await request(`/tasks/${agentResultDraft.taskId}/attachments`, userId, {
            method: "POST",
            body: JSON.stringify({ fileName: resultPhoto.name, mimeType: resultPhoto.type || "application/octet-stream", sizeBytes: resultPhoto.size }),
          });
        }
        await request<TaskItem>(`/tasks/${agentResultDraft.taskId}/status`, userId, {
          method: "PATCH",
          body: JSON.stringify({ status: "DONE", resultText: agentResultDraft.note.trim() }),
        });
        await request<Evidence>(`/tasks/${agentResultDraft.taskId}/result/evidence`, userId, {
          method: "POST",
          body: JSON.stringify({ name: agentResultDraft.evidenceName.trim(), type: agentResultDraft.evidenceType.trim(), importance: "MEDIUM", locationTitle: agentResultDraft.locationTitle.trim() || null }),
        });
        setMyTasks((items) => items.map((item) => item.id === agentResultDraft.taskId ? { ...item, status: "DONE", resultText: agentResultDraft.note } : item));
        setAgentResultDraft(emptyAgentResultDraft());
        setResultPhoto(null);
        setActiveForm(null);
        setMessage("Результат задачи сохранен и зарегистрирован как улика");
        if (selectedCaseId) await loadCaseDetails(selectedCaseId);
      } catch (error) {
        handleFormError(error);
      }
    });
  }

  async function createTask() {
    if (!userId || !selectedCaseId || !agent) return;
    const created = await request<TaskItem>(`/cases/${selectedCaseId}/tasks`, userId, {
      method: "POST",
      body: JSON.stringify({
        title: "Опросить свидетеля на вокзале",
        description: "Уточнить, видел ли свидетель подозреваемого вечером.",
        assigneeId: agent.id,
        priority: "HIGH",
        deadline: new Date(Date.now() + 86400000).toISOString(),
      }),
    });
    setTasks((items) => [created, ...items]);
    setMessage(`Назначена задача ${created.title}`);
  }

  async function createLabRequest() {
    if (!userId || !firstEvidence) return;
    const created = await request<LabRequest>(`/evidence/${firstEvidence.id}/lab-requests`, userId, {
      method: "POST",
      body: JSON.stringify({
        profile: "Биологический анализ",
        questions: "Определить происхождение следов и возможную связь с подозреваемым.",
        desiredDueDate: new Date(Date.now() + 172800000).toISOString(),
      }),
    });
    setLabs((items) => [created, ...items]);
    setMessage("Лабораторный запрос направлен");
  }

  async function createGraphEdge() {
    if (!userId || !selectedCase || !firstEvidence) return;
    await request(`/cases/${selectedCase.id}/graph/edges`, userId, {
      method: "POST",
      body: JSON.stringify({
        source: { type: "EVIDENCE", id: firstEvidence.id },
        target: { type: "CASE", id: selectedCase.id },
        semanticType: "улика усиливает основную гипотезу",
        confidence: "HIGH",
        hypothesisTitle: "Личная связь с мотивом",
        hypothesisText: "Найденный объект может указывать на личный мотив и связь подозреваемого с местом происшествия.",
      }),
    });
    setMessage("Гипотеза и связь добавлены в граф");
    await loadCaseDetails(selectedCase.id);
  }

  async function generateReport(force = false) {
    if (!userId || !selectedCaseId) return;
    if (!force) {
      const data = await request<ReportPreview>(`/cases/${selectedCaseId}/reports/preview`, userId, { method: "POST" });
      setPreview(data);
      setMessage(data.warning ?? "Предпросмотр отчета готов");
      return;
    }
    const report = await request<ReportFile>(`/cases/${selectedCaseId}/reports?force=true`, userId, { method: "POST" });
    setApprovedReport(report);
    setMessage(`Отчет ${report.registrationNumber} утвержден и сохранен`);
  }

  async function downloadReport() {
    if (!approvedReport) return;
    const response = await fetch(`${API}/reports/${approvedReport.id}/download`);
    if (!response.ok) throw new Error("Не удалось скачать отчет");
    const url = URL.createObjectURL(await response.blob());
    const link = document.createElement("a");
    link.href = url;
    link.download = `${approvedReport.registrationNumber}.txt`;
    link.click();
    URL.revokeObjectURL(url);
  }

  const actions = useMemo(
    () => [
      { label: "Создать дело", icon: BookOpen, run: () => Promise.resolve(openForm("case")) },
      { label: "Добавить улику", icon: Plus, run: () => Promise.resolve(openForm("evidence")) },
      { label: "Назначить задачу", icon: ClipboardList, run: createTask },
      { label: "Запрос в лабораторию", icon: Beaker, run: createLabRequest },
      { label: "Создать связь", icon: GitBranch, run: createGraphEdge },
      { label: "Предпросмотр отчета", icon: FileText, run: () => generateReport(false) },
    ],
    [userId, selectedCaseId, firstEvidence?.id, agent?.id],
  );

  return (
    <main className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <Shield size={28} />
          <div>
            <strong>DIMS</strong>
            <span>Casebook Holmes</span>
          </div>
        </div>
        <label className="field">
          Роль
          <select value={userId} onChange={(event) => setUserId(event.target.value)}>
            {users.map((user) => (
              <option key={user.id} value={user.id}>
                {user.displayName} · {user.role}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          Дело
          <select value={selectedCaseId} onChange={(event) => setSelectedCaseId(event.target.value)}>
            {cases.map((item) => (
              <option key={item.id} value={item.id}>
                {item.registrationNumber}
              </option>
            ))}
          </select>
        </label>
        <button className="theme-toggle" onClick={() => setTheme(theme === "light" ? "dark" : "light")}>
          {theme === "light" ? <Moon size={18} /> : <Sun size={18} />}
          {theme === "light" ? "Темная тема" : "Светлая тема"}
        </button>
      </aside>

      <section className="workspace">
        <header className="topbar">
          <div>
            <h1>{selectedCase?.title ?? "DIMS"}</h1>
            <p>{selectedCase?.description ?? "Единая среда расследования"}</p>
          </div>
          <div className="status">
            <RadioTower size={18} />
            {isPending ? "Синхронизация" : "REST/JSON online"}
          </div>
        </header>

        <section className="actions">
          {actions.map((action) => (
            <button
              key={action.label}
              onClick={() => startTransition(() => action.run().catch((error) => setMessage(error.message)))}
              disabled={isPending}
            >
              <action.icon size={18} />
              {action.label}
            </button>
          ))}
          <button className="primary" onClick={() => startTransition(() => generateReport(true).catch((error) => setMessage(error.message)))}>
            {isPending ? <Loader2 className="spin" size={18} /> : <CheckCircle2 size={18} />}
            Утвердить отчет
          </button>
        </section>

        {message && <div className="notice">{message}</div>}
        {!isOnline ? <div className="warning" role="alert">{NETWORK_MESSAGE}</div> : null}

        {activeForm === "case" ? (
          <FormDialog title="Новое дело" onClose={closeForm}>
            <form className="entity-form" onSubmit={submitCase} noValidate>
              <FormField label="Название" error={fieldErrors.title}>
                <input value={caseDraft.title} onChange={(event) => setCaseDraft((draft) => ({ ...draft, title: event.target.value }))} autoFocus />
              </FormField>
              <FormField label="Дата открытия" error={fieldErrors.openedAt}>
                <input type="datetime-local" value={caseDraft.openedAt} onChange={(event) => setCaseDraft((draft) => ({ ...draft, openedAt: event.target.value }))} />
              </FormField>
              <FormField label="Приоритет">
                <select value={caseDraft.priority} onChange={(event) => setCaseDraft((draft) => ({ ...draft, priority: event.target.value }))}>
                  <option value="LOW">Низкий</option><option value="MEDIUM">Средний</option><option value="HIGH">Высокий</option>
                </select>
              </FormField>
              <FormField label="Описание" error={fieldErrors.description} wide>
                <textarea value={caseDraft.description} onChange={(event) => setCaseDraft((draft) => ({ ...draft, description: event.target.value }))} rows={4} />
              </FormField>
              <FormActions pending={isPending} onCancel={closeForm} />
            </form>
          </FormDialog>
        ) : null}

        {activeForm === "evidence" ? (
          <FormDialog title="Новая улика" onClose={closeForm}>
            <form className="entity-form" onSubmit={submitEvidence} noValidate>
              <FormField label="Название" error={fieldErrors.name}>
                <input value={evidenceDraft.name} onChange={(event) => setEvidenceDraft((draft) => ({ ...draft, name: event.target.value }))} autoFocus />
              </FormField>
              <FormField label="Тип" error={fieldErrors.type}>
                <input value={evidenceDraft.type} onChange={(event) => setEvidenceDraft((draft) => ({ ...draft, type: event.target.value }))} placeholder="Например, цифровая" />
              </FormField>
              <FormField label="Важность">
                <select value={evidenceDraft.importance} onChange={(event) => setEvidenceDraft((draft) => ({ ...draft, importance: event.target.value }))}>
                  <option value="LOW">Низкая</option><option value="MEDIUM">Средняя</option><option value="HIGH">Высокая</option>
                </select>
              </FormField>
              <FormField label="Дата обнаружения" error={fieldErrors.discoveryDateTime}>
                <input type="datetime-local" value={evidenceDraft.discoveryDateTime} onChange={(event) => setEvidenceDraft((draft) => ({ ...draft, discoveryDateTime: event.target.value }))} />
              </FormField>
              <FormField label="Место обнаружения" wide>
                <input value={evidenceDraft.locationTitle} onChange={(event) => setEvidenceDraft((draft) => ({ ...draft, locationTitle: event.target.value }))} />
              </FormField>
              <FormField label="Описание" error={fieldErrors.description} wide>
                <textarea value={evidenceDraft.description} onChange={(event) => setEvidenceDraft((draft) => ({ ...draft, description: event.target.value }))} rows={4} />
              </FormField>
              <FormActions pending={isPending} onCancel={closeForm} />
            </form>
          </FormDialog>
        ) : null}

        {activeForm === "agentResult" ? (
          <FormDialog title="Результат полевой задачи" onClose={closeForm}>
            <form className="entity-form" onSubmit={submitAgentResult} noValidate>
              <FormField label="Фото" wide>
                <input type="file" accept="image/*" capture="environment" onChange={(event) => setResultPhoto(event.target.files?.[0] ?? null)} />
              </FormField>
              <FormField label="Заметка" error={fieldErrors.note} wide>
                <textarea value={agentResultDraft.note} onChange={(event) => setAgentResultDraft((draft) => ({ ...draft, note: event.target.value }))} rows={5} autoFocus />
              </FormField>
              <FormField label="Название улики" error={fieldErrors.evidenceName}>
                <input value={agentResultDraft.evidenceName} onChange={(event) => setAgentResultDraft((draft) => ({ ...draft, evidenceName: event.target.value }))} />
              </FormField>
              <FormField label="Тип улики" error={fieldErrors.evidenceType}>
                <input value={agentResultDraft.evidenceType} onChange={(event) => setAgentResultDraft((draft) => ({ ...draft, evidenceType: event.target.value }))} />
              </FormField>
              <FormField label="Место" wide>
                <input value={agentResultDraft.locationTitle} onChange={(event) => setAgentResultDraft((draft) => ({ ...draft, locationTitle: event.target.value }))} />
              </FormField>
              <FormActions pending={isPending} onCancel={closeForm} />
            </form>
          </FormDialog>
        ) : null}

        <section className="grid">
          <Panel title="Дела" count={cases.length}>
            {cases.map((item) => (
              <button className="row" key={item.id} onClick={() => setSelectedCaseId(item.id)}>
                <strong>{item.registrationNumber}</strong>
                <span>{item.status} · {item.priority}</span>
              </button>
            ))}
          </Panel>
          <Panel title="Улики" count={evidence.length}>
            {evidence.map((item) => (
              <article className="row" key={item.id}>
                <strong>{item.name}</strong>
                <span>{item.type} · {item.status}</span>
              </article>
            ))}
          </Panel>
          <Panel title={currentUser?.role === "AGENT" || currentUser?.role === "ASSISTANT" ? "Мои задачи" : "Задачи"} count={visibleTasks.length}>
            {visibleTasks.map((item) => (
              <article className="row" key={item.id}>
                <strong>{item.title}</strong>
                <span>{item.status} · {new Date(item.deadline).toLocaleDateString("ru-RU")}</span>
                {(currentUser?.role === "AGENT" || currentUser?.role === "ASSISTANT") && item.status !== "DONE" ? (
                  <button className="result-action" onClick={() => openAgentResult(item)}><Camera size={16} /> Добавить результат</button>
                ) : null}
              </article>
            ))}
          </Panel>
          <Panel title={currentUser?.role === "LAB_ANALYST" ? "Моя очередь" : "Лаборатория"} count={visibleLabs.length}>
            {visibleLabs.map((item) => (
              <article className="row" key={item.id}>
                <strong>{item.profile}</strong>
                <span>{item.status}</span>
              </article>
            ))}
          </Panel>
        </section>

        <section className="analysis">
          <div className="graph-panel">
            <div className="panel-heading">
              <h2>Граф связей</h2>
              <span>{graph?.nodes.length ?? 0} узлов · {graph?.edges.length ?? 0} связей</span>
            </div>
            {graph?.warning && <div className="warning">{graph.warning}</div>}
            <div className="graph-canvas">
              {graph?.nodes.map((node, index) => (
                <div className={`node node-${index % 5}`} key={`${node.type}-${node.id}`}>
                  <strong>{node.label}</strong>
                  <span>{node.type}</span>
                </div>
              ))}
            </div>
          </div>
          <div className="report-panel">
            <div className="panel-heading">
              <h2>Отчет</h2>
              <Activity size={18} />
            </div>
            <pre>{preview?.content ?? "Сформируйте предпросмотр, чтобы проверить обязательные поля, экспертизы и гипотезы."}</pre>
            {approvedReport ? (
              <button className="download-button" onClick={() => startTransition(() => downloadReport().catch((error) => setMessage(error.message)))} disabled={isPending}>
                <FileText size={18} /> Скачать {approvedReport.registrationNumber}
              </button>
            ) : null}
          </div>
        </section>
      </section>
    </main>
  );
}

function Panel({ title, count, children }: { title: string; count: number; children: React.ReactNode }) {
  return (
    <section className="panel">
      <div className="panel-heading">
        <h2>{title}</h2>
        <span>{count}</span>
      </div>
      <div className="panel-body">{children}</div>
    </section>
  );
}

function FormDialog({ title, onClose, children }: { title: string; onClose: () => void; children: React.ReactNode }) {
  return (
    <div className="dialog-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <section className="dialog" role="dialog" aria-modal="true" aria-labelledby="form-dialog-title">
        <div className="dialog-heading">
          <h2 id="form-dialog-title">{title}</h2>
          <button type="button" className="icon-button" onClick={onClose} aria-label="Закрыть форму"><X size={20} /></button>
        </div>
        {children}
      </section>
    </div>
  );
}

function FormField({ label, error, wide = false, children }: { label: string; error?: string; wide?: boolean; children: React.ReactNode }) {
  return (
    <label className={`form-field${wide ? " form-field-wide" : ""}`}>
      <span>{label}</span>
      {children}
      {error ? <small className="field-error">{error}</small> : null}
    </label>
  );
}

function FormActions({ pending, onCancel }: { pending: boolean; onCancel: () => void }) {
  return (
    <div className="form-actions">
      <button type="button" onClick={onCancel} disabled={pending}>Отмена</button>
      <button type="submit" className="primary" disabled={pending}>{pending ? <Loader2 className="spin" size={18} /> : null}Сохранить</button>
    </div>
  );
}

createRoot(document.getElementById("root")!).render(<App />);
