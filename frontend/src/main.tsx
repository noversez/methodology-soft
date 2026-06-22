import React, { useEffect, useMemo, useRef, useState, useTransition } from "react";
import { createRoot } from "react-dom/client";
import {
  Activity,
  ArrowRight,
  Beaker,
  BookOpen,
  Camera,
  CheckCircle2,
  ClipboardList,
  FileText,
  Loader2,
  ListFilter,
  Maximize2,
  Minimize2,
  Moon,
  Pencil,
  Plus,
  RadioTower,
  RotateCcw,
  Search,
  Shield,
  Sun,
  X,
} from "lucide-react";
import "./styles.css";

const API = "http://127.0.0.1:8080/api";

type Role = "DETECTIVE" | "ASSISTANT" | "INSPECTOR" | "AGENT" | "LAB_ANALYST" | "ADMIN";
type User = { id: string; login: string; role: Role; displayName: string };
type AuthResponse = { user: User; token?: string };
type CaseFile = { id: string; registrationNumber: string; title: string; openedAt: string; priority: string; status: string; description: string };
type IncidentScene = { id: string; caseId: string; title: string; description: string; address: string; latitude?: number; longitude?: number; createdAt: string };
type Interview = { id: string; caseId: string; interviewee: string; occurredAt: string; protocolText: string; createdAt: string };
type Evidence = { id: string; caseId: string; registrationNumber: string; name: string; type: string; importance: string; description: string; discoveryDateTime: string; latitude?: number; longitude?: number; locationTitle?: string; status: string };
type TaskItem = { id: string; caseId: string; registrationNumber: string; title: string; description: string; assigneeId: string; priority: string; deadline: string; status: string; resultText?: string; resultEvidenceId?: string };
type NotificationItem = { id: string; type: string; payloadJson: string; readAt?: string; createdAt: string };
type LabRequest = { id: string; caseId: string; registrationNumber: string; evidenceId: string; profile: string; questions: string; desiredDueDate: string; status: string; requesterId: string; labAssigneeId?: string; resultText?: string };
type Graph = { nodes: { type: string; id: string; label: string; status: string; version: number }[]; edges: { id: string; source: { type: string; id: string }; target: { type: string; id: string }; semanticType: string; confidence: string; hypothesisTitle?: string; hypothesisText?: string }[]; filtered: boolean; warning?: string; graphRevision: number };
type ReportPreview = { content: string; hasOpenLabRequests: boolean; warning?: string };
type ReportFile = { id: string; registrationNumber: string; status: string; format: string };
type FormKind = "case" | "scene" | "interview" | "evidence" | "task" | "lab" | "labResult" | "edge" | "agentResult";
type FieldErrors = Record<string, string>;
type ApiErrorBody = { code?: string; message?: string; details?: Record<string, unknown> };
type CaseDraft = { title: string; openedAt: string; priority: string; status: string; description: string };
type SceneDraft = { title: string; description: string; address: string; latitude: string; longitude: string };
type InterviewDraft = { interviewee: string; occurredAt: string; protocolText: string };
type EvidenceDraft = { name: string; type: string; importance: string; description: string; discoveryDateTime: string; locationTitle: string; latitude: string; longitude: string };
type AgentResultDraft = { taskId: string; note: string; evidenceName: string; evidenceType: string; locationTitle: string; latitude: string; longitude: string; capturedAt: string };
type TaskDraft = { title: string; description: string; assigneeId: string; priority: string; deadline: string };
type LabDraft = { evidenceId: string; profile: string; questions: string; desiredDueDate: string };
type EdgeDraft = { source: string; target: string; semanticType: string; confidence: string; hypothesisTitle: string; hypothesisText: string };
type GraphNode = Graph["nodes"][number];
type GraphLayout = {positions:Record<string,{x:number;y:number}>;pan:{x:number;y:number}};

const roleTitles: Record<Role,string> = {DETECTIVE:"Ведущий детектив",ASSISTANT:"Ассистент",INSPECTOR:"Инспектор",AGENT:"Полевой агент",LAB_ANALYST:"Эксперт лаборатории",ADMIN:"Администратор"};
const graphNodeTypeTitles: Record<string,string> = {CASE:"Дела",EVIDENCE:"Улики",TASK:"Задачи",LAB_REQUEST:"Экспертизы",REPORT:"Отчеты",LOCATION:"Места",PERSON:"Люди",HYPOTHESIS:"Гипотезы"};

const emptyCaseDraft = (): CaseDraft => ({
  title: "",
  openedAt: new Date().toISOString().slice(0, 16),
  priority: "MEDIUM",
  status: "NEW",
  description: "",
});
const emptySceneDraft = (): SceneDraft => ({ title: "", description: "", address: "", latitude: "", longitude: "" });
const emptyInterviewDraft = (): InterviewDraft => ({ interviewee: "", occurredAt: new Date().toISOString().slice(0, 16), protocolText: "" });

const emptyEvidenceDraft = (): EvidenceDraft => ({
  name: "",
  type: "",
  importance: "MEDIUM",
  description: "",
  discoveryDateTime: new Date().toISOString().slice(0, 16),
  locationTitle: "",
  latitude: "",
  longitude: "",
});
const emptyAgentResultDraft = (): AgentResultDraft => ({ taskId: "", note: "", evidenceName: "", evidenceType: "цифровая", locationTitle: "", latitude: "", longitude: "", capturedAt: new Date().toISOString() });
const futureDate = (days: number) => new Date(Date.now() + days * 86400000).toISOString().slice(0, 16);
const emptyTaskDraft = (): TaskDraft => ({ title: "", description: "", assigneeId: "", priority: "MEDIUM", deadline: futureDate(1) });
const emptyLabDraft = (): LabDraft => ({ evidenceId: "", profile: "", questions: "", desiredDueDate: futureDate(2) });
const emptyEdgeDraft = (): EdgeDraft => ({ source: "", target: "", semanticType: "", confidence: "MEDIUM", hypothesisTitle: "", hypothesisText: "" });
const nodeKey = (node: Pick<GraphNode, "type" | "id">) => `${node.type}:${node.id}`;
const nodePoint = (index: number) => {
  const points = [[150, 90], [750, 105], [450, 215], [170, 340], [760, 330], [450, 70], [70, 215], [850, 215]];
  const [x, y] = points[index % points.length];
  const ring = Math.floor(index / points.length);
  return { x: x + ring * 18, y: y + ring * 10 };
};

const NETWORK_MESSAGE = "Нестабильность сети, проверьте подключение к интернету";
const DRAFTS_KEY = "dims-form-drafts-v1";
const GRAPH_LAYOUTS_KEY = "dims-graph-layouts-v1";
const EMPTY_GRAPH_LAYOUT: GraphLayout = {positions:{},pan:{x:0,y:0}};

function loadGraphLayouts(): Record<string,GraphLayout> {
  try {
    const value=JSON.parse(localStorage.getItem(GRAPH_LAYOUTS_KEY)??"null");
    if(value?.version===1&&value.layouts&&typeof value.layouts==="object")return value.layouts;
  } catch { /* Ignore damaged local preferences. */ }
  return {};
}

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
      if (response.status === 204) return undefined as T;
      return response.json();
    } catch (error) {
      if (error instanceof ApiRequestError) throw error;
      if (attempt + 1 === attempts) throw new ApiRequestError(NETWORK_MESSAGE, "NETWORK_ERROR");
      await new Promise((resolve) => setTimeout(resolve, 300));
    }
  }
  throw new ApiRequestError(NETWORK_MESSAGE, "NETWORK_ERROR");
}

async function uploadFile<T>(path: string, token: string, file: File, metadata: { capturedAt?: string; latitude?: string; longitude?: string } = {}): Promise<T> {
  const form = new FormData();
  form.append("file", file);
  const params = new URLSearchParams();
  if (metadata.capturedAt) params.set("capturedAt", metadata.capturedAt);
  if (metadata.latitude) params.set("latitude", metadata.latitude);
  if (metadata.longitude) params.set("longitude", metadata.longitude);
  const response = await fetch(`${API}${path}?${params}`, { method: "POST", headers: { "X-User-Id": token }, body: form, signal: AbortSignal.timeout(15000) });
  if (!response.ok) {
    const error: ApiErrorBody = await response.json().catch(() => ({ message: "Ошибка загрузки файла" }));
    throw new ApiRequestError(error.message ?? "Ошибка загрузки файла", error.code, error.details);
  }
  return response.json();
}

function App() {
  const [users, setUsers] = useState<User[]>([]);
  const [userId, setUserId] = useState("");
  const [currentUser, setCurrentUser] = useState<User | null>(null);
  const [login, setLogin] = useState("sherlock");
  const [password, setPassword] = useState("holmes");
  const [cases, setCases] = useState<CaseFile[]>([]);
  const [selectedCaseId, setSelectedCaseId] = useState("");
  const [evidence, setEvidence] = useState<Evidence[]>([]);
  const [scenes, setScenes] = useState<IncidentScene[]>([]);
  const [interviews, setInterviews] = useState<Interview[]>([]);
  const [participants, setParticipants] = useState<User[]>([]);
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [tasks, setTasks] = useState<TaskItem[]>([]);
  const [myTasks, setMyTasks] = useState<TaskItem[]>([]);
  const [labs, setLabs] = useState<LabRequest[]>([]);
  const [labQueue, setLabQueue] = useState<LabRequest[]>([]);
  const [graph, setGraph] = useState<Graph | null>(null);
  const [graphTypeFilter, setGraphTypeFilter] = useState("ALL");
  const [graphQuery, setGraphQuery] = useState("");
  const [preview, setPreview] = useState<ReportPreview | null>(null);
  const [approvedReport, setApprovedReport] = useState<ReportFile | null>(null);
  const [reportTemplate, setReportTemplate] = useState("FULL");
  const [reportFormat, setReportFormat] = useState("TEXT");
  const [message, setMessage] = useState("");
  const [theme, setTheme] = useState<"light" | "dark">("light");
  const [activeForm, setActiveForm] = useState<FormKind | null>(null);
  const [caseDraft, setCaseDraft] = useState<CaseDraft>(() => loadDrafts().caseDraft);
  const [sceneDraft, setSceneDraft] = useState<SceneDraft>(emptySceneDraft);
  const [interviewDraft, setInterviewDraft] = useState<InterviewDraft>(emptyInterviewDraft);
  const [evidenceDraft, setEvidenceDraft] = useState<EvidenceDraft>(() => loadDrafts().evidenceDraft);
  const [agentResultDraft, setAgentResultDraft] = useState<AgentResultDraft>(() => loadDrafts().agentResultDraft);
  const [taskDraft, setTaskDraft] = useState<TaskDraft>(emptyTaskDraft);
  const [labDraft, setLabDraft] = useState<LabDraft>(emptyLabDraft);
  const [edgeDraft, setEdgeDraft] = useState<EdgeDraft>(emptyEdgeDraft);
  const [connectSelection, setConnectSelection] = useState<string[]>([]);
  const [graphBoardHeight, setGraphBoardHeight] = useState(430);
  const [graphNodeScale, setGraphNodeScale] = useState(1);
  const [graphLayouts,setGraphLayouts]=useState<Record<string,GraphLayout>>(loadGraphLayouts);
  const graphCanvasRef = useRef<HTMLDivElement | null>(null);
  const graphDragRef = useRef<{key:string;pointerId:number;startX:number;startY:number;originX:number;originY:number;moved:boolean}|null>(null);
  const draggedGraphNodeRef = useRef("");
  const graphPanDragRef=useRef<{pointerId:number;startX:number;startY:number;originX:number;originY:number}|null>(null);
  const [resultPhoto, setResultPhoto] = useState<File | null>(null);
  const [evidenceFile, setEvidenceFile] = useState<File | null>(null);
  const [activeLabId, setActiveLabId] = useState("");
  const [duplicateLabId, setDuplicateLabId] = useState("");
  const [labResultText, setLabResultText] = useState("");
  const [editingTaskId, setEditingTaskId] = useState("");
  const [editingEvidenceId, setEditingEvidenceId] = useState("");
  const [editingCaseId, setEditingCaseId] = useState("");
  const [editingSceneId, setEditingSceneId] = useState("");
  const [editingInterviewId, setEditingInterviewId] = useState("");
  const [editingLabId, setEditingLabId] = useState("");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [isOnline, setIsOnline] = useState(() => navigator.onLine);
  const [isPending, startTransition] = useTransition();

  const selectedCase = cases.find((item) => item.id === selectedCaseId);
  const graphLayout=graphLayouts[selectedCaseId]??EMPTY_GRAPH_LAYOUT;
  const graphNodePositions=graphLayout.positions;
  const firstEvidence = evidence[0];
  const visibleTasks = currentUser?.role === "AGENT" || currentUser?.role === "ASSISTANT" ? myTasks : tasks;
  const visibleLabs = currentUser?.role === "LAB_ANALYST" ? labQueue : labs;
  const visibleGraphNodes = useMemo(() => {
    const query = graphQuery.trim().toLocaleLowerCase("ru-RU");
    return (graph?.nodes ?? []).filter((node) => {
      const matchesType=graphTypeFilter==="ALL"||node.type===graphTypeFilter;
      const searchableType=`${node.type} ${graphNodeTypeTitles[node.type]??""}`.toLocaleLowerCase("ru-RU");
      return matchesType&&(!query||node.label.toLocaleLowerCase("ru-RU").includes(query)||searchableType.includes(query));
    });
  }, [graph, graphTypeFilter, graphQuery]);
  const visibleGraphEdges = useMemo(() => {
    const keys = new Set(visibleGraphNodes.map(nodeKey));
    return (graph?.edges ?? []).filter((edge) => keys.has(nodeKey(edge.source)) && keys.has(nodeKey(edge.target)));
  }, [graph, visibleGraphNodes]);

  async function loadBase() {
    const [loadedUsers, loadedCases] = await Promise.all([
      request<User[]>("/users", userId),
      request<CaseFile[]>("/cases"),
    ]);
    setUsers(loadedUsers);
    setCases(loadedCases);
    setSelectedCaseId((prev) => prev || loadedCases[0]?.id || "");
  }

  async function loadCaseDetails(caseId: string) {
    const [loadedEvidence, loadedTasks, loadedGraph, loadedScenes, loadedLabs, loadedInterviews, loadedParticipants] = await Promise.all([
      request<Evidence[]>(`/cases/${caseId}/evidence`),
      request<TaskItem[]>(`/cases/${caseId}/tasks`),
      request<Graph>(`/cases/${caseId}/graph`),
      request<IncidentScene[]>(`/cases/${caseId}/scenes`),
      request<LabRequest[]>(`/cases/${caseId}/lab-requests`),
      request<Interview[]>(`/cases/${caseId}/interviews`),
      request<User[]>(`/cases/${caseId}/participants`),
    ]);
    setEvidence(loadedEvidence);
    setTasks(loadedTasks);
    setGraph(loadedGraph);
    setScenes(loadedScenes);
    setLabs(loadedLabs);
    setInterviews(loadedInterviews);
    setParticipants(loadedParticipants);
  }

  async function authenticate(event: React.FormEvent) {
    event.preventDefault();
    setFieldErrors({});
    try {
      const auth = await request<AuthResponse>("/auth/login", undefined, { method: "POST", body: JSON.stringify({ login: login.trim(), password }) });
      setUserId(auth.token ?? auth.user.id);
      setCurrentUser(auth.user);
      setMessage("");
    } catch (error) { handleFormError(error); }
  }

  useEffect(() => {
    if (userId && currentUser) loadBase().catch((error) => setMessage(error.message));
  }, [userId]);

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
    const timeout=window.setTimeout(()=>{try{localStorage.setItem(GRAPH_LAYOUTS_KEY,JSON.stringify({version:1,layouts:graphLayouts}));}catch{/* Layout persistence is optional. */}},180);
    return ()=>window.clearTimeout(timeout);
  },[graphLayouts]);

  useEffect(() => {
    if (!userId || !currentUser) return;
    request<NotificationItem[]>("/notifications",userId).then(setNotifications).catch((error)=>setMessage(error.message));
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

  const assignees = participants.filter((user) => user.role === "AGENT" || user.role === "ASSISTANT");

  async function createCase(draft: CaseDraft) {
    if (!userId) throw new ApiRequestError("Сеанс входа не получен. Войдите в систему повторно", "AUTH_REQUIRED");
    const created = await request<CaseFile>(editingCaseId ? `/cases/${editingCaseId}` : "/cases", userId, {
      method: editingCaseId ? "PATCH" : "POST",
      body: JSON.stringify({
        ...draft,
        title: draft.title.trim(),
        description: draft.description.trim(),
        openedAt: new Date(draft.openedAt).toISOString(),
      }),
    });
    setCases((items) => editingCaseId ? items.map((item) => item.id === created.id ? created : item) : [created, ...items]);
    setSelectedCaseId(created.id);
    setCaseDraft(emptyCaseDraft());
    setEditingCaseId("");
    setActiveForm(null);
    setMessage(editingCaseId ? `Дело ${created.registrationNumber} изменено` : `Создано дело ${created.registrationNumber}`);
  }

  async function createEvidence(draft: EvidenceDraft) {
    if (!userId || !selectedCaseId) return;
    const isEditing = Boolean(editingEvidenceId);
    const created = await request<Evidence>(editingEvidenceId ? `/evidence/${editingEvidenceId}` : `/cases/${selectedCaseId}/evidence`, userId, {
      method: editingEvidenceId ? "PATCH" : "POST",
      body: JSON.stringify({
        ...draft,
        name: draft.name.trim(),
        type: draft.type.trim(),
        description: draft.description.trim(),
        discoveryDateTime: new Date(draft.discoveryDateTime).toISOString(),
        locationTitle: draft.locationTitle.trim() || undefined,
        latitude: draft.latitude ? Number(draft.latitude) : undefined,
        longitude: draft.longitude ? Number(draft.longitude) : undefined,
      }),
    });
    if (evidenceFile) {
      await uploadFile(`/evidence/${created.id}/attachments`, userId, evidenceFile, { capturedAt: new Date(evidenceFile.lastModified).toISOString(), latitude: draft.latitude, longitude: draft.longitude });
    }
    setEvidence((items) => editingEvidenceId ? items.map((item) => item.id === created.id ? created : item) : [created, ...items]);
    setEvidenceDraft(emptyEvidenceDraft());
    setEvidenceFile(null);
    setEditingEvidenceId("");
    setActiveForm(null);
    setMessage(isEditing ? `Улика ${created.registrationNumber} обновлена, версия сохранена` : `Добавлена улика ${created.registrationNumber}`);
    await loadCaseDetails(selectedCaseId);
  }

  function openForm(kind: FormKind) {
    setFieldErrors({});
    setActiveForm(kind);
  }

  function closeForm() {
    setFieldErrors({});
    if (activeForm === "case") { setCaseDraft(emptyCaseDraft()); setEditingCaseId(""); }
    if (activeForm === "scene") { setSceneDraft(emptySceneDraft()); setEditingSceneId(""); }
    if (activeForm === "interview") { setInterviewDraft(emptyInterviewDraft()); setEditingInterviewId(""); }
    if (activeForm === "lab") { setLabDraft(emptyLabDraft()); setEditingLabId(""); }
    if (activeForm === "evidence") { setEvidenceDraft(emptyEvidenceDraft()); setEvidenceFile(null); setEditingEvidenceId(""); }
    setActiveForm(null);
    if (activeForm === "edge") setConnectSelection([]);
  }

  function selectGraphNode(node: GraphNode) {
    const key = nodeKey(node);
    if (!connectSelection.length) {
      if ((graph?.nodes.length ?? 0) < 2) {
        setMessage("Для связи нужны минимум два блока");
        return;
      }
      setConnectSelection([key]);
      setEdgeDraft(emptyEdgeDraft());
      setEdgeDraft((draft) => ({ ...draft, source: key }));
      setMessage("Теперь выберите второй блок");
      return;
    }
    if (connectSelection[0] === key) {
      setConnectSelection([]);
      setEdgeDraft(emptyEdgeDraft());
      setMessage("Выбор отменен");
      return;
    }
    setConnectSelection([connectSelection[0], key]);
    setEdgeDraft((draft) => ({ ...draft, target: key }));
    setFieldErrors({});
    setActiveForm("edge");
  }

  function graphNodePosition(node: GraphNode,index: number) {
    return graphNodePositions[nodeKey(node)] ?? {x:nodePoint(index).x/10,y:nodePoint(index).y/4.3};
  }

  function updateCurrentGraphLayout(update:(layout:GraphLayout)=>GraphLayout) {
    if(!selectedCaseId)return;
    setGraphLayouts((layouts)=>({...layouts,[selectedCaseId]:update(layouts[selectedCaseId]??EMPTY_GRAPH_LAYOUT)}));
  }

  function startGraphNodeDrag(event: React.PointerEvent<HTMLButtonElement>,node: GraphNode,index: number) {
    if(event.button!==0)return;
    const position=graphNodePosition(node,index);
    graphDragRef.current={key:nodeKey(node),pointerId:event.pointerId,startX:event.clientX,startY:event.clientY,originX:position.x,originY:position.y,moved:false};
    event.currentTarget.setPointerCapture(event.pointerId);
  }

  function moveGraphNode(event: React.PointerEvent<HTMLButtonElement>) {
    const drag=graphDragRef.current; const canvas=graphCanvasRef.current;
    if(!drag||drag.pointerId!==event.pointerId||!canvas)return;
    const rect=canvas.getBoundingClientRect(); const dx=event.clientX-drag.startX; const dy=event.clientY-drag.startY;
    if(Math.abs(dx)+Math.abs(dy)>4)drag.moved=true;
    if(!drag.moved)return;
    updateCurrentGraphLayout((layout)=>({...layout,positions:{...layout.positions,[drag.key]:{x:Math.min(94,Math.max(6,drag.originX+dx/rect.width*100)),y:Math.min(92,Math.max(8,drag.originY+dy/rect.height*100))}}}));
  }

  function finishGraphNodeDrag(event: React.PointerEvent<HTMLButtonElement>) {
    const drag=graphDragRef.current;
    if(!drag||drag.pointerId!==event.pointerId)return;
    if(drag.moved)draggedGraphNodeRef.current=drag.key;
    graphDragRef.current=null;
    if(event.currentTarget.hasPointerCapture(event.pointerId))event.currentTarget.releasePointerCapture(event.pointerId);
  }

  function activateGraphNode(node: GraphNode) {
    if(draggedGraphNodeRef.current===nodeKey(node)){draggedGraphNodeRef.current="";return;}
    selectGraphNode(node);
  }

  function startGraphPan(event: React.PointerEvent<HTMLDivElement>) {
    if(event.button!==0||(event.target as HTMLElement).closest(".connect-hint"))return;
    graphPanDragRef.current={pointerId:event.pointerId,startX:event.clientX,startY:event.clientY,originX:graphLayout.pan.x,originY:graphLayout.pan.y};
    event.currentTarget.setPointerCapture(event.pointerId);
  }

  function moveGraphPan(event: React.PointerEvent<HTMLDivElement>) {
    const drag=graphPanDragRef.current;
    if(!drag||drag.pointerId!==event.pointerId)return;
    updateCurrentGraphLayout((layout)=>({...layout,pan:{x:drag.originX+event.clientX-drag.startX,y:drag.originY+event.clientY-drag.startY}}));
  }

  function finishGraphPan(event: React.PointerEvent<HTMLDivElement>) {
    const drag=graphPanDragRef.current;
    if(!drag||drag.pointerId!==event.pointerId)return;
    graphPanDragRef.current=null;
    if(event.currentTarget.hasPointerCapture(event.pointerId))event.currentTarget.releasePointerCapture(event.pointerId);
  }

  function resetGraphLayout() {
    if(!selectedCaseId)return;
    setGraphLayouts((layouts)=>{const next={...layouts};delete next[selectedCaseId];return next;});
  }

  function openAgentResult(task: TaskItem) {
    setFieldErrors({});
    setAgentResultDraft((draft) => ({ ...draft, taskId: task.id, evidenceName: draft.taskId === task.id ? draft.evidenceName : task.title }));
    setResultPhoto(null);
    setActiveForm("agentResult");
    captureAgentLocation();
  }

  function captureAgentLocation() {
    if (!navigator.geolocation) { setMessage("Геолокация не поддерживается устройством"); return; }
    navigator.geolocation.getCurrentPosition(
      ({ coords }) => setAgentResultDraft((draft) => ({ ...draft, latitude: String(coords.latitude), longitude: String(coords.longitude), capturedAt: new Date().toISOString() })),
      () => setMessage("GPS недоступен: разрешите доступ к геолокации или введите место вручную"),
      { enableHighAccuracy: true, timeout: 10000 },
    );
  }

  function editEvidence(item: Evidence) {
    setEditingEvidenceId(item.id);
    setEvidenceDraft({ name: item.name, type: item.type, importance: item.importance, description: item.description, discoveryDateTime: new Date(item.discoveryDateTime).toISOString().slice(0, 16), locationTitle: item.locationTitle ?? "", latitude: item.latitude?.toString() ?? "", longitude: item.longitude?.toString() ?? "" });
    setEvidenceFile(null);
    openForm("evidence");
  }

  function editCase(item: CaseFile) {
    setEditingCaseId(item.id);
    setCaseDraft({ title:item.title, openedAt:new Date(item.openedAt).toISOString().slice(0,16), priority:item.priority, status:item.status, description:item.description });
    openForm("case");
  }

  function editScene(item: IncidentScene) {
    setEditingSceneId(item.id);
    setSceneDraft({ title:item.title, description:item.description, address:item.address, latitude:item.latitude?.toString() ?? "", longitude:item.longitude?.toString() ?? "" });
    openForm("scene");
  }

  function editInterview(item: Interview) {
    setEditingInterviewId(item.id);
    setInterviewDraft({ interviewee:item.interviewee, occurredAt:new Date(item.occurredAt).toISOString().slice(0,16), protocolText:item.protocolText });
    openForm("interview");
  }

  function editLab(item: LabRequest) {
    setEditingLabId(item.id);
    setLabDraft({ evidenceId:item.evidenceId, profile:item.profile, questions:item.questions, desiredDueDate:new Date(item.desiredDueDate).toISOString().slice(0,16) });
    openForm("lab");
  }

  async function deleteEntity(path: string, label: string, after?: () => void) {
    if (!userId || !window.confirm(`Удалить «${label}»? Действие нельзя отменить.`)) return;
    await request(path,userId,{method:"DELETE"});
    after?.();
    setMessage(`Удалено: ${label}`);
    if (selectedCaseId) await loadCaseDetails(selectedCaseId).catch(() => undefined);
  }

  async function deleteCase(item: CaseFile) {
    if (!userId || !window.confirm(`Удалить дело ${item.registrationNumber} и все связанные материалы? Действие нельзя отменить.`)) return;
    await request(`/cases/${item.id}`,userId,{method:"DELETE"});
    const remaining=cases.filter((candidate)=>candidate.id!==item.id);
    setCases(remaining); setSelectedCaseId(remaining[0]?.id ?? ""); setMessage(`Дело ${item.registrationNumber} удалено`);
  }

  function handleFormError(error: unknown) {
    if (error instanceof ApiRequestError) {
      if (error.code === "ACTIVE_LAB_REQUEST_EXISTS" && typeof error.details.activeLabRequestId === "string") {
        setDuplicateLabId(error.details.activeLabRequestId);
        setActiveForm(null);
        if (selectedCaseId) void loadCaseDetails(selectedCaseId).catch(() => undefined);
      }
      if (error.code === "GRAPH_STALE") {
        setActiveForm(null);
        setConnectSelection([]);
        if (selectedCaseId) void loadCaseDetails(selectedCaseId).catch(() => undefined);
      }
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

  function submitScene(event: React.FormEvent) {
    event.preventDefault();
    const errors = { title: required(sceneDraft.title, "Название"), address: required(sceneDraft.address, "Адрес"), description: required(sceneDraft.description, "Описание") };
    const visibleErrors = Object.fromEntries(Object.entries(errors).filter(([, value]) => value));
    setFieldErrors(visibleErrors);
    if (Object.keys(visibleErrors).length || !selectedCaseId || !userId) return;
    startTransition(async () => {
      try {
        const created = await request<IncidentScene>(editingSceneId ? `/cases/${selectedCaseId}/scenes/${editingSceneId}` : `/cases/${selectedCaseId}/scenes`, userId, { method: editingSceneId ? "PATCH" : "POST", body: JSON.stringify({ ...sceneDraft, latitude: sceneDraft.latitude ? Number(sceneDraft.latitude) : null, longitude: sceneDraft.longitude ? Number(sceneDraft.longitude) : null }) });
        setScenes((items) => editingSceneId ? items.map((item)=>item.id===created.id?created:item) : [created, ...items]);
        setSceneDraft(emptySceneDraft());
        setEditingSceneId("");
        setActiveForm(null);
        setMessage(editingSceneId ? "Место происшествия изменено" : "Место происшествия зафиксировано");
      } catch (error) { handleFormError(error); }
    });
  }

  function submitInterview(event: React.FormEvent) {
    event.preventDefault();
    const errors = { interviewee: required(interviewDraft.interviewee, "Опрашиваемый"), occurredAt: required(interviewDraft.occurredAt, "Дата"), protocolText: required(interviewDraft.protocolText, "Протокол") };
    const visibleErrors = Object.fromEntries(Object.entries(errors).filter(([, value]) => value)); setFieldErrors(visibleErrors);
    if (Object.keys(visibleErrors).length || !selectedCaseId || !userId) return;
    startTransition(async () => { try { const created=await request<Interview>(editingInterviewId?`/cases/${selectedCaseId}/interviews/${editingInterviewId}`:`/cases/${selectedCaseId}/interviews`,userId,{method:editingInterviewId?"PATCH":"POST",body:JSON.stringify({...interviewDraft,occurredAt:new Date(interviewDraft.occurredAt).toISOString()})}); setInterviews((items)=>editingInterviewId?items.map((item)=>item.id===created.id?created:item):[...items,created]); setInterviewDraft(emptyInterviewDraft()); setEditingInterviewId(""); setActiveForm(null); setMessage(editingInterviewId?"Интервью изменено":"Протокол интервью сохранен"); } catch(error){handleFormError(error);} });
  }

  function submitEvidence(event: React.FormEvent) {
    event.preventDefault();
    const errors = {
      name: required(evidenceDraft.name, "Название"),
      type: required(evidenceDraft.type, "Тип"),
      description: required(evidenceDraft.description, "Описание"),
      discoveryDateTime: required(evidenceDraft.discoveryDateTime, "Дата обнаружения"),
    };
    if (evidenceFile && evidenceFile.size > 20 * 1024 * 1024) errors.description = "Файл превышает лимит 20 МБ";
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
    if (resultPhoto && resultPhoto.size > 20 * 1024 * 1024) errors.note = "Фото превышает лимит 20 МБ";
    const visibleErrors = Object.fromEntries(Object.entries(errors).filter(([, value]) => value));
    setFieldErrors(visibleErrors);
    if (Object.keys(visibleErrors).length || !userId) return;
    startTransition(async () => {
      try {
        await request<TaskItem>(`/tasks/${agentResultDraft.taskId}/status`, userId, {
          method: "PATCH",
          body: JSON.stringify({ status: "DONE", resultText: agentResultDraft.note.trim() }),
        });
        const createdEvidence = await request<Evidence>(`/tasks/${agentResultDraft.taskId}/result/evidence`, userId, {
          method: "POST",
          body: JSON.stringify({ name: agentResultDraft.evidenceName.trim(), type: agentResultDraft.evidenceType.trim(), importance: "MEDIUM", locationTitle: agentResultDraft.locationTitle.trim() || null, latitude: agentResultDraft.latitude ? Number(agentResultDraft.latitude) : null, longitude: agentResultDraft.longitude ? Number(agentResultDraft.longitude) : null, capturedAt: agentResultDraft.capturedAt }),
        });
        if (resultPhoto) await uploadFile(`/evidence/${createdEvidence.id}/attachments`, userId, resultPhoto, agentResultDraft);
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

  function submitTask(event: React.FormEvent) {
    event.preventDefault();
    const errors = { title: required(taskDraft.title, "Название"), description: required(taskDraft.description, "Описание"), assigneeId: required(taskDraft.assigneeId, "Исполнитель"), deadline: required(taskDraft.deadline, "Срок") };
    const visibleErrors = Object.fromEntries(Object.entries(errors).filter(([, value]) => value));
    setFieldErrors(visibleErrors);
    if (Object.keys(visibleErrors).length) return;
    startTransition(() => createTask().catch(handleFormError));
  }

  function submitLab(event: React.FormEvent) {
    event.preventDefault();
    const errors = { evidenceId: required(labDraft.evidenceId, "Улика"), profile: required(labDraft.profile, "Профиль"), questions: required(labDraft.questions, "Вопросы"), desiredDueDate: required(labDraft.desiredDueDate, "Срок") };
    const visibleErrors = Object.fromEntries(Object.entries(errors).filter(([, value]) => value));
    setFieldErrors(visibleErrors);
    if (Object.keys(visibleErrors).length) return;
    startTransition(() => createLabRequest().catch(handleFormError));
  }

  function submitEdge(event: React.FormEvent) {
    event.preventDefault();
    const errors = { source: required(edgeDraft.source, "Источник"), target: required(edgeDraft.target, "Цель"), semanticType: required(edgeDraft.semanticType, "Тип связи"), hypothesisTitle: required(edgeDraft.hypothesisTitle, "Название гипотезы"), hypothesisText: required(edgeDraft.hypothesisText, "Обоснование") };
    if (edgeDraft.source && edgeDraft.source === edgeDraft.target) errors.target = "Выберите другой блок";
    const visibleErrors = Object.fromEntries(Object.entries(errors).filter(([, value]) => value));
    setFieldErrors(visibleErrors);
    if (Object.keys(visibleErrors).length) return;
    startTransition(() => createGraphEdge().catch(handleFormError));
  }

  async function createTask() {
    if (!userId || !selectedCaseId) return;
    const created = await request<TaskItem>(editingTaskId ? `/tasks/${editingTaskId}` : `/cases/${selectedCaseId}/tasks`, userId, {
      method: editingTaskId ? "PATCH" : "POST",
      body: JSON.stringify({
        ...taskDraft,
        title: taskDraft.title.trim(),
        description: taskDraft.description.trim(),
        deadline: new Date(taskDraft.deadline).toISOString(),
      }),
    });
    setTasks((items) => editingTaskId ? items.map((item) => item.id === created.id ? created : item) : [created, ...items]);
    setTaskDraft(emptyTaskDraft());
    setEditingTaskId("");
    setActiveForm(null);
    setMessage(editingTaskId ? `Задача ${created.title} переназначена` : `Назначена задача ${created.title}`);
  }

  function editTask(task: TaskItem) {
    setEditingTaskId(task.id);
    setTaskDraft({ title: task.title, description: task.description, assigneeId: task.assigneeId, priority: task.priority, deadline: new Date(task.deadline).toISOString().slice(0, 16) });
    openForm("task");
  }

  async function changeTaskStatus(task: TaskItem, status: "IN_PROGRESS" | "DONE") {
    if (!userId) return;
    const updated = await request<TaskItem>(`/tasks/${task.id}/status`, userId, { method: "PATCH", body: JSON.stringify({ status, resultText: task.resultText ?? null }) });
    setMyTasks((items) => items.map((item) => item.id === updated.id ? updated : item));
    setTasks((items) => items.map((item) => item.id === updated.id ? updated : item));
    setMessage(`Статус задачи: ${status}`);
  }

  async function changeLabStatus(id: string) {
    if (!userId) return;
    const updated = await request<LabRequest>(`/lab-requests/${id}/status`, userId, { method: "PATCH", body: JSON.stringify({ status: "IN_PROGRESS" }) });
    setLabQueue((items) => items.map((item) => item.id === id ? updated : item));
    setMessage("Экспертиза принята в работу");
  }

  async function completeLab() {
    if (!userId || !activeLabId) return;
    const updated = await request<LabRequest>(`/lab-requests/${activeLabId}/result`, userId, { method: "POST", body: JSON.stringify({ resultText: labResultText }) });
    setLabQueue((items) => items.map((item) => item.id === activeLabId ? updated : item));
    setActiveForm(null);
    setLabResultText("");
    setMessage("Заключение сохранено, экспертиза завершена");
    if (selectedCaseId) await loadCaseDetails(selectedCaseId);
  }

  function submitLabResult(event: React.FormEvent) {
    event.preventDefault();
    const resultLength=labResultText.trim().length;
    const error = resultLength < 1 || resultLength > 20_000 ? "Заключение должно содержать от 1 до 20 000 знаков" : "";
    setFieldErrors(error ? { resultText: error } : {});
    if (!error) startTransition(() => completeLab().catch(handleFormError));
  }

  async function createLabRequest() {
    if (!userId || !labDraft.evidenceId) return;
    const created = await request<LabRequest>(editingLabId ? `/lab-requests/${editingLabId}` : `/evidence/${labDraft.evidenceId}/lab-requests`, userId, {
      method: editingLabId ? "PATCH" : "POST",
      body: JSON.stringify({
        profile: labDraft.profile.trim(),
        questions: labDraft.questions.trim(),
        desiredDueDate: new Date(labDraft.desiredDueDate).toISOString(),
      }),
    });
    setLabs((items) => editingLabId ? items.map((item)=>item.id===created.id?created:item) : [created, ...items]);
    setDuplicateLabId("");
    setLabDraft(emptyLabDraft());
    setEditingLabId("");
    setActiveForm(null);
    setMessage(editingLabId ? "Лабораторный запрос изменен" : "Лабораторный запрос направлен");
  }

  async function createGraphEdge() {
    if (!userId || !selectedCase || !graph) return;
    const [sourceType, sourceId] = edgeDraft.source.split(":");
    const [targetType, targetId] = edgeDraft.target.split(":");
    const sourceNode = graph.nodes.find((node) => node.type === sourceType && node.id === sourceId);
    const targetNode = graph.nodes.find((node) => node.type === targetType && node.id === targetId);
    await request(`/cases/${selectedCase.id}/graph/edges`, userId, {
      method: "POST",
      body: JSON.stringify({
        source: { type: sourceType, id: sourceId, version: sourceNode?.version },
        target: { type: targetType, id: targetId, version: targetNode?.version },
        semanticType: edgeDraft.semanticType.trim(),
        confidence: edgeDraft.confidence,
        hypothesisTitle: edgeDraft.hypothesisTitle.trim(),
        hypothesisText: edgeDraft.hypothesisText.trim(),
        expectedGraphRevision: graph.graphRevision,
      }),
    });
    setEdgeDraft(emptyEdgeDraft());
    setConnectSelection([]);
    setActiveForm(null);
    setMessage("Связь и гипотеза добавлены в граф");
    await loadCaseDetails(selectedCase.id);
  }

  function createTaskFromHypothesis(edge: Graph["edges"][number]) {
    setTaskDraft({ ...emptyTaskDraft(), title: `Проверить гипотезу: ${edge.hypothesisTitle ?? edge.semanticType}`, description: edge.hypothesisText ?? `Проверить связь «${edge.semanticType}»` });
    openForm("task");
  }

  async function deleteGraphEdge(edge: Graph["edges"][number]) {
    if(!userId||!selectedCaseId||!graph)return;
    if(!window.confirm(`Удалить связь «${edge.semanticType}» и связанную гипотезу?`))return;
    await request(`/cases/${selectedCaseId}/graph/edges/${edge.id}?expectedGraphRevision=${graph.graphRevision}`,userId,{method:"DELETE"});
    setMessage("Связь удалена из графа");
    setConnectSelection([]);
    await loadCaseDetails(selectedCaseId);
  }

  async function generateReport(force: boolean | null = null) {
    if (!userId || !selectedCaseId) return;
    if (force === null) {
      const data = await request<ReportPreview>(`/cases/${selectedCaseId}/reports/preview?template=${reportTemplate}&format=${reportFormat}`, userId, { method: "POST" });
      setPreview(data);
      setMessage(data.warning ?? "Предпросмотр отчета готов");
      return;
    }
    const report = await request<ReportFile>(`/cases/${selectedCaseId}/reports?force=${force}&template=${reportTemplate}&format=${reportFormat}`, userId, { method: "POST" });
    setApprovedReport(report);
    setMessage(`Отчет ${report.registrationNumber} утвержден и сохранен`);
  }

  async function downloadReport() {
    if (!approvedReport) return;
    const response = await fetch(`${API}/reports/${approvedReport.id}/download`, { headers: { "X-User-Id": userId } });
    if (!response.ok) throw new Error("Не удалось скачать отчет");
    const url = URL.createObjectURL(await response.blob());
    const link = document.createElement("a");
    link.href = url;
    link.download = `${approvedReport.registrationNumber}.${approvedReport.format === "HTML" ? "html" : "txt"}`;
    link.click();
    URL.revokeObjectURL(url);
  }

  const actions = useMemo(
    () => [
      { label: "Создать дело", icon: BookOpen, run: () => Promise.resolve(openForm("case")) },
      { label: "Место происшествия", icon: RadioTower, run: () => Promise.resolve(openForm("scene")) },
      { label: "Добавить интервью", icon: ClipboardList, run: () => Promise.resolve(openForm("interview")) },
      { label: "Добавить улику", icon: Plus, run: () => Promise.resolve(openForm("evidence")) },
      { label: "Назначить задачу", icon: ClipboardList, run: () => Promise.resolve(openForm("task")) },
      { label: "Запрос в лабораторию", icon: Beaker, run: () => { setLabDraft((draft) => ({ ...draft, evidenceId: draft.evidenceId || firstEvidence?.id || "" })); return Promise.resolve(openForm("lab")); } },
      { label: "Предпросмотр отчета", icon: FileText, run: () => generateReport(null) },
    ],
    [userId, selectedCaseId, firstEvidence?.id, reportTemplate, reportFormat],
  );

  if (!currentUser) {
    return (
      <main className="login-shell">
        <form className="login-card" onSubmit={authenticate}>
          <Shield size={38} />
          <h1>Вход в DIMS</h1>
          <FormField label="Логин" error={fieldErrors.login}><input value={login} onChange={(event) => setLogin(event.target.value)} autoFocus /></FormField>
          <FormField label="Пароль" error={fieldErrors.password}><input type="password" value={password} onChange={(event) => setPassword(event.target.value)} /></FormField>
          {message ? <div className="warning" role="alert">{message}</div> : null}
          <button className="primary" type="submit" disabled={isPending}>Войти</button>
        </form>
      </main>
    );
  }

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
        <div className="user-profile">
          <div className="user-avatar" aria-hidden="true">{currentUser.displayName.split(" ").map((part)=>part[0]).slice(0,2).join("")}</div>
          <div className="user-profile__body"><span>Пользователь</span><strong>{currentUser.displayName}</strong><small>{roleTitles[currentUser.role]}</small><b className={`role-badge role-${currentUser.role.toLowerCase()}`}>{currentUser.role}</b></div>
        </div>
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
        <button className="theme-toggle" onClick={() => { fetch(`${API}/auth/logout`, { method: "POST", headers: { "X-User-Id": userId } }).finally(() => { setUserId(""); setCurrentUser(null); setCases([]); }); }}>Выйти</button>
      </aside>

      <section className="workspace">
        <header className="topbar">
          <div>
            <h1>{selectedCase?.title ?? "DIMS"}</h1>
            <p>{selectedCase?.description ?? "Единая среда расследования"}</p>
          </div>
          <div className={`status-icon ${isOnline?"is-online":"is-offline"}`} role="status" aria-label={isPending?"Синхронизация данных":isOnline?"Сетевое подключение доступно":"Сетевое подключение отсутствует"} title={isPending?"Синхронизация данных":isOnline?"Сеть доступна":"Сеть недоступна"}>
            {isPending ? <Loader2 className="spin" size={18}/> : <RadioTower size={18}/>}
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
          <button className="primary" onClick={() => startTransition(() => generateReport(false).catch(handleFormError))}>
            {isPending ? <Loader2 className="spin" size={18} /> : <CheckCircle2 size={18} />}
            Утвердить отчет
          </button>
        </section>

        {message && <div className="notice">{message}{duplicateLabId ? <button className="result-action" onClick={() => document.getElementById(`lab-${duplicateLabId}`)?.scrollIntoView({ behavior: "smooth", block: "center" })}>Перейти к активному запросу</button> : null}</div>}
        {!isOnline ? <div className="warning" role="alert">{NETWORK_MESSAGE}</div> : null}

        {activeForm === "case" ? (
          <FormDialog title={editingCaseId ? "Изменить дело" : "Новое дело"} onClose={closeForm}>
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
              {editingCaseId ? <FormField label="Статус"><select value={caseDraft.status} onChange={(event)=>setCaseDraft((draft)=>({...draft,status:event.target.value}))}><option value="NEW">Новое</option><option value="IN_PROGRESS">В работе</option><option value="CLOSED">Закрыто</option></select></FormField> : null}
              <FormField label="Описание" error={fieldErrors.description} wide>
                <textarea value={caseDraft.description} onChange={(event) => setCaseDraft((draft) => ({ ...draft, description: event.target.value }))} rows={4} />
              </FormField>
              <FormActions pending={isPending} onCancel={closeForm} />
            </form>
          </FormDialog>
        ) : null}

        {activeForm === "scene" ? (
          <FormDialog title="Место происшествия" onClose={closeForm}>
            <form className="entity-form" onSubmit={submitScene} noValidate>
              <FormField label="Название" error={fieldErrors.title}><input value={sceneDraft.title} onChange={(event) => setSceneDraft((draft) => ({ ...draft, title: event.target.value }))} autoFocus /></FormField>
              <FormField label="Адрес" error={fieldErrors.address}><input value={sceneDraft.address} onChange={(event) => setSceneDraft((draft) => ({ ...draft, address: event.target.value }))} /></FormField>
              <FormField label="Широта"><input type="number" min="-90" max="90" step="any" value={sceneDraft.latitude} onChange={(event) => setSceneDraft((draft) => ({ ...draft, latitude: event.target.value }))} /></FormField>
              <FormField label="Долгота"><input type="number" min="-180" max="180" step="any" value={sceneDraft.longitude} onChange={(event) => setSceneDraft((draft) => ({ ...draft, longitude: event.target.value }))} /></FormField>
              <FormField label="Первичное описание" error={fieldErrors.description} wide><textarea rows={4} value={sceneDraft.description} onChange={(event) => setSceneDraft((draft) => ({ ...draft, description: event.target.value }))} /></FormField>
              <FormActions pending={isPending} onCancel={closeForm} />
            </form>
          </FormDialog>
        ) : null}

        {activeForm === "interview" ? (
          <FormDialog title="Протокол интервью" onClose={closeForm}>
            <form className="entity-form" onSubmit={submitInterview} noValidate>
              <FormField label="Опрашиваемый" error={fieldErrors.interviewee}><input value={interviewDraft.interviewee} onChange={(event)=>setInterviewDraft((draft)=>({...draft,interviewee:event.target.value}))} autoFocus /></FormField>
              <FormField label="Дата и время" error={fieldErrors.occurredAt}><input type="datetime-local" value={interviewDraft.occurredAt} onChange={(event)=>setInterviewDraft((draft)=>({...draft,occurredAt:event.target.value}))} /></FormField>
              <FormField label="Протокол" error={fieldErrors.protocolText} wide><textarea rows={8} value={interviewDraft.protocolText} onChange={(event)=>setInterviewDraft((draft)=>({...draft,protocolText:event.target.value}))} /></FormField>
              <FormActions pending={isPending} onCancel={closeForm} />
            </form>
          </FormDialog>
        ) : null}

        {activeForm === "evidence" ? (
          <FormDialog title={editingEvidenceId ? "Изменить улику" : "Новая улика"} onClose={closeForm}>
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
              <FormField label="Широта"><input type="number" step="any" value={evidenceDraft.latitude} onChange={(event) => setEvidenceDraft((draft) => ({ ...draft, latitude: event.target.value }))} /></FormField>
              <FormField label="Долгота"><input type="number" step="any" value={evidenceDraft.longitude} onChange={(event) => setEvidenceDraft((draft) => ({ ...draft, longitude: event.target.value }))} /></FormField>
              <FormField label="Фото или файл до 20 МБ" error={evidenceFile && evidenceFile.size > 20 * 1024 * 1024 ? "Файл превышает лимит 20 МБ" : undefined} wide>
                <input type="file" onChange={(event) => setEvidenceFile(event.target.files?.[0] ?? null)} />
              </FormField>
              <FormField label="Описание" error={fieldErrors.description} wide>
                <textarea value={evidenceDraft.description} onChange={(event) => setEvidenceDraft((draft) => ({ ...draft, description: event.target.value }))} rows={4} />
              </FormField>
              <FormActions pending={isPending} onCancel={closeForm} />
            </form>
          </FormDialog>
        ) : null}

        {activeForm === "task" ? (
          <FormDialog title={editingTaskId ? "Переназначить задачу" : "Новое поручение"} onClose={closeForm}>
            <form className="entity-form" onSubmit={submitTask} noValidate>
              <FormField label="Название" error={fieldErrors.title}><input value={taskDraft.title} onChange={(event) => setTaskDraft((draft) => ({ ...draft, title: event.target.value }))} autoFocus /></FormField>
              <FormField label="Исполнитель" error={fieldErrors.assigneeId}><select value={taskDraft.assigneeId} onChange={(event) => setTaskDraft((draft) => ({ ...draft, assigneeId: event.target.value }))}><option value="">Выберите</option>{assignees.map((user) => <option key={user.id} value={user.id}>{user.displayName}</option>)}</select></FormField>
              <FormField label="Приоритет"><select value={taskDraft.priority} onChange={(event) => setTaskDraft((draft) => ({ ...draft, priority: event.target.value }))}><option value="LOW">Низкий</option><option value="MEDIUM">Средний</option><option value="HIGH">Высокий</option></select></FormField>
              <FormField label="Срок" error={fieldErrors.deadline}><input type="datetime-local" value={taskDraft.deadline} onChange={(event) => setTaskDraft((draft) => ({ ...draft, deadline: event.target.value }))} /></FormField>
              <FormField label="Описание" error={fieldErrors.description} wide><textarea rows={4} value={taskDraft.description} onChange={(event) => setTaskDraft((draft) => ({ ...draft, description: event.target.value }))} /></FormField>
              <FormActions pending={isPending} onCancel={closeForm} />
            </form>
          </FormDialog>
        ) : null}

        {activeForm === "lab" ? (
          <FormDialog title={editingLabId ? "Изменить запрос на экспертизу" : "Запрос на экспертизу"} onClose={closeForm}>
            <form className="entity-form" onSubmit={submitLab} noValidate>
              <FormField label="Дело"><input value={selectedCase ? `${selectedCase.registrationNumber} · ${selectedCase.title}` : ""} readOnly /></FormField>
              <FormField label="Улика" error={fieldErrors.evidenceId}><select value={labDraft.evidenceId} disabled={Boolean(editingLabId)} onChange={(event) => setLabDraft((draft) => ({ ...draft, evidenceId: event.target.value }))}><option value="">Выберите</option>{evidence.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></FormField>
              <FormField label="Данные улики" wide><input value={evidence.find((item) => item.id === labDraft.evidenceId)?.description ?? "Выберите улику"} readOnly /></FormField>
              <FormField label="Профиль экспертизы" error={fieldErrors.profile}><input value={labDraft.profile} onChange={(event) => setLabDraft((draft) => ({ ...draft, profile: event.target.value }))} autoFocus /></FormField>
              <FormField label="Желаемый срок" error={fieldErrors.desiredDueDate}><input type="datetime-local" min={new Date().toISOString().slice(0, 16)} value={labDraft.desiredDueDate} onChange={(event) => setLabDraft((draft) => ({ ...draft, desiredDueDate: event.target.value }))} /></FormField>
              <FormField label="Вопросы эксперту" error={fieldErrors.questions} wide><textarea rows={4} value={labDraft.questions} onChange={(event) => setLabDraft((draft) => ({ ...draft, questions: event.target.value }))} /></FormField>
              <FormActions pending={isPending} onCancel={closeForm} />
            </form>
          </FormDialog>
        ) : null}

        {activeForm === "labResult" ? (
          <FormDialog title="Заключение эксперта" onClose={closeForm}>
            <form className="entity-form" onSubmit={submitLabResult} noValidate>
              <FormField label={`Текст заключения · ${labResultText.length}/20 000`} error={fieldErrors.resultText} wide>
                <textarea rows={12} maxLength={20_000} value={labResultText} onChange={(event) => setLabResultText(event.target.value)} autoFocus />
              </FormField>
              <FormActions pending={isPending} onCancel={closeForm} />
            </form>
          </FormDialog>
        ) : null}

        {activeForm === "edge" ? (
          <FormDialog title="Описание связи" onClose={closeForm}>
            <form className="entity-form" onSubmit={submitEdge} noValidate>
              <FormField label="От блока" error={fieldErrors.source}><GraphNodeSelect value={edgeDraft.source} nodes={graph?.nodes ?? []} onChange={(source) => setEdgeDraft((draft) => ({ ...draft, source }))} /></FormField>
              <FormField label="К блоку" error={fieldErrors.target}><GraphNodeSelect value={edgeDraft.target} nodes={graph?.nodes ?? []} onChange={(target) => setEdgeDraft((draft) => ({ ...draft, target }))} /></FormField>
              <FormField label="Тип связи" error={fieldErrors.semanticType}><input value={edgeDraft.semanticType} onChange={(event) => setEdgeDraft((draft) => ({ ...draft, semanticType: event.target.value }))} placeholder="например, подтверждает" /></FormField>
              <FormField label="Достоверность"><select value={edgeDraft.confidence} onChange={(event) => setEdgeDraft((draft) => ({ ...draft, confidence: event.target.value }))}><option value="LOW">Низкая</option><option value="MEDIUM">Средняя</option><option value="HIGH">Высокая</option></select></FormField>
              <FormField label="Гипотеза" error={fieldErrors.hypothesisTitle} wide><input value={edgeDraft.hypothesisTitle} onChange={(event) => setEdgeDraft((draft) => ({ ...draft, hypothesisTitle: event.target.value }))} /></FormField>
              <FormField label="Обоснование" error={fieldErrors.hypothesisText} wide><textarea rows={3} value={edgeDraft.hypothesisText} onChange={(event) => setEdgeDraft((draft) => ({ ...draft, hypothesisText: event.target.value }))} /></FormField>
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
              <FormField label="GPS-координаты" wide>
                <div className="row-actions"><span>{agentResultDraft.latitude && agentResultDraft.longitude ? `${agentResultDraft.latitude}, ${agentResultDraft.longitude}` : "Ожидание координат"}</span><button type="button" className="result-action" onClick={captureAgentLocation}>Получить GPS</button></div>
              </FormField>
              <FormActions pending={isPending} onCancel={closeForm} />
            </form>
          </FormDialog>
        ) : null}

        <section className="grid">
          <Panel title="Дела" count={cases.length}>
            {cases.map((item) => (
              <article className={`row${item.id===selectedCaseId?" active-row":""}`} key={item.id}>
                <button className="row-select" onClick={() => setSelectedCaseId(item.id)}><strong>{item.registrationNumber}</strong></button>
                <span>{item.status} · {item.priority}</span>
                {currentUser?.role==="DETECTIVE"?<WindowActions onEdit={()=>editCase(item)} onDelete={()=>startTransition(()=>deleteCase(item).catch(handleFormError))}/>:null}
              </article>
            ))}
          </Panel>
          <Panel title="Улики" count={evidence.length}>
            {evidence.map((item) => (
              <article className="row" key={item.id}>
                <strong>{item.name}</strong>
                <span>{item.type} · {item.status}</span>
                <WindowActions onEdit={currentUser?.role === "DETECTIVE" || currentUser?.role === "ASSISTANT" || currentUser?.role === "INSPECTOR" ? ()=>editEvidence(item) : undefined} onDelete={currentUser?.role==="DETECTIVE" ? ()=>startTransition(()=>deleteEntity(`/evidence/${item.id}`,item.name).catch(handleFormError)) : undefined}/>
              </article>
            ))}
          </Panel>
          <Panel title="Места происшествия" count={scenes.length}>
            {scenes.map((item) => <article className="row" key={item.id}><strong>{item.title}</strong><span>{item.address}</span>{currentUser?.role==="DETECTIVE"?<WindowActions onEdit={()=>editScene(item)} onDelete={()=>startTransition(()=>deleteEntity(`/cases/${selectedCaseId}/scenes/${item.id}`,item.title).catch(handleFormError))}/>:null}</article>)}
          </Panel>
          <Panel title="Интервью" count={interviews.length}>
            {interviews.map((item)=><article className="row" key={item.id}><strong>{item.interviewee}</strong><span>{new Date(item.occurredAt).toLocaleString("ru-RU")}</span><small>{item.protocolText}</small>{currentUser?.role==="DETECTIVE"?<WindowActions onEdit={()=>editInterview(item)} onDelete={()=>startTransition(()=>deleteEntity(`/cases/${selectedCaseId}/interviews/${item.id}`,item.interviewee).catch(handleFormError))}/>:null}</article>)}
          </Panel>
          <Panel title={currentUser?.role === "AGENT" || currentUser?.role === "ASSISTANT" ? "Мои задачи" : "Задачи"} count={visibleTasks.length}>
            {visibleTasks.map((item) => (
              <article className="row" key={item.id}>
                <strong>{item.registrationNumber} · {item.title}</strong>
                <span>{item.status} · {new Date(item.deadline).toLocaleDateString("ru-RU")}</span>
                {(currentUser?.role === "AGENT" || currentUser?.role === "ASSISTANT") && item.status !== "DONE" ? (
                  <div className="row-actions">
                    {item.status === "ASSIGNED" ? <button className="result-action" onClick={() => startTransition(() => changeTaskStatus(item, "IN_PROGRESS").catch(handleFormError))}>Принять в работу</button> : null}
                    {item.status === "IN_PROGRESS" ? <button className="result-action" onClick={() => openAgentResult(item)}><Camera size={16} /> Передать результат</button> : null}
                  </div>
                ) : null}
                {currentUser?.role === "DETECTIVE" ? <WindowActions onEdit={item.status !== "DONE" ? ()=>editTask(item) : undefined} onDelete={()=>startTransition(()=>deleteEntity(`/tasks/${item.id}`,item.title).catch(handleFormError))}/> : null}
              </article>
            ))}
          </Panel>
          <Panel title="Уведомления" count={notifications.length}>
            {notifications.map((item)=><article className="row" key={item.id}><strong>{item.type === "TASK_ASSIGNED" ? "Назначена новая задача" : item.type === "TASK_REASSIGNED" ? "Задача переназначена вам" : item.type}</strong><span>{new Date(item.createdAt).toLocaleString("ru-RU")}</span></article>)}
          </Panel>
          <Panel title={currentUser?.role === "LAB_ANALYST" ? "Моя очередь" : "Лаборатория"} count={visibleLabs.length}>
            {visibleLabs.map((item) => (
              <article className="row" id={`lab-${item.id}`} key={item.id}>
                <strong>{item.registrationNumber} · {item.profile}</strong>
                <span>{item.status} · до {new Date(item.desiredDueDate).toLocaleDateString("ru-RU")}</span>
                {currentUser?.role === "LAB_ANALYST" && item.status === "CREATED" ? <button className="result-action" onClick={() => startTransition(() => changeLabStatus(item.id).catch(handleFormError))}>Принять в работу</button> : null}
                {currentUser?.role === "LAB_ANALYST" && item.status !== "COMPLETED" ? <button className="result-action" onClick={() => { setActiveLabId(item.id); setFieldErrors({}); setActiveForm("labResult"); }}>Внести заключение</button> : null}
                {currentUser?.role === "DETECTIVE" ? <WindowActions onEdit={item.status!=="COMPLETED" ? ()=>editLab(item) : undefined} onDelete={()=>startTransition(()=>deleteEntity(`/lab-requests/${item.id}`,item.registrationNumber).catch(handleFormError))}/> : null}
              </article>
            ))}
          </Panel>
        </section>

        <section className="analysis-toolbar" aria-label="Навигация по графу">
          <div className="graph-search">
            <Search size={17} />
            <input value={graphQuery} onChange={(event) => setGraphQuery(event.target.value)} onKeyDown={(event)=>{if(event.key==="Escape")setGraphQuery("");}} placeholder="Название или тип: TASK, EVIDENCE…" aria-label="Поиск по названию или типу объекта" />
            {graphQuery?<button type="button" className="graph-search-clear" onClick={()=>setGraphQuery("")} title="Очистить поиск" aria-label="Очистить поиск"><X size={14}/></button>:null}
          </div>
          <label className="graph-type-filter">
            <ListFilter size={16}/><span className="sr-only">Тип объекта</span>
            <select value={graphTypeFilter} onChange={(event) => setGraphTypeFilter(event.target.value)} aria-label="Тип объекта"><option value="ALL">Все объекты</option>{Array.from(new Set((graph?.nodes ?? []).map((node) => node.type))).map((type) => <option key={type} value={type}>{graphNodeTypeTitles[type]??type}</option>)}</select>
          </label>
          {graphTypeFilter !== "ALL" || graphQuery ? <button type="button" className="filter-reset" onClick={() => { setGraphTypeFilter("ALL"); setGraphQuery(""); }} title="Сбросить фильтры" aria-label="Сбросить фильтры"><RotateCcw size={15}/></button> : null}
        </section>

        <section className="analysis">
          <div className="graph-panel">
            <div className="panel-heading">
              <h2>Граф связей</h2>
              <div className="graph-view-controls">
                <span>{visibleGraphNodes.length}/{graph?.nodes.length ?? 0} узлов · {visibleGraphEdges.length} связей</span>
                <div className="graph-control-group" aria-label="Размер доски">
                  <small>Доска</small>
                  <button type="button" onClick={() => setGraphBoardHeight((height) => Math.max(320, height - 160))} disabled={graphBoardHeight <= 320} title="Уменьшить доску" aria-label="Уменьшить доску"><Minimize2 size={15} /></button>
                  <button type="button" onClick={() => setGraphBoardHeight((height) => Math.min(1200, height + 160))} disabled={graphBoardHeight >= 1200} title="Увеличить доску" aria-label="Увеличить доску"><Maximize2 size={15} /></button>
                </div>
                <div className="graph-control-group" aria-label="Размер карточек">
                  <small>Карточки</small>
                  <button type="button" onClick={() => setGraphNodeScale((scale) => Math.max(0.65, Number((scale - 0.15).toFixed(2))))} disabled={graphNodeScale <= 0.65} title="Уменьшить карточки" aria-label="Уменьшить карточки">−</button>
                  <button type="button" onClick={() => setGraphNodeScale((scale) => Math.min(1.1, Number((scale + 0.15).toFixed(2))))} disabled={graphNodeScale >= 1.1} title="Увеличить карточки" aria-label="Увеличить карточки">+</button>
                </div>
                <div className="graph-control-group" aria-label="Положение графа"><button type="button" onClick={resetGraphLayout} title="Сбросить расположение и центрировать" aria-label="Сбросить расположение и центрировать"><RotateCcw size={15}/></button></div>
              </div>
            </div>
            {graph?.warning && <div className="warning">{graph.warning}</div>}
            <div ref={graphCanvasRef} className={`graph-canvas${connectSelection.length ? " has-selection" : ""}`} style={{ height: graphBoardHeight,backgroundPosition:`${graphLayout.pan.x}px ${graphLayout.pan.y}px` }} onPointerDown={startGraphPan} onPointerMove={moveGraphPan} onPointerUp={finishGraphPan} onPointerCancel={finishGraphPan}>
              {connectSelection.length ? (
                <div className="connect-hint">
                  <strong>Выберите второй блок · повторный клик отменит выбор</strong>
                </div>
              ) : null}
              <div className="graph-world" style={{transform:`translate3d(${graphLayout.pan.x}px,${graphLayout.pan.y}px,0)`}}>
              <svg className="graph-lines" viewBox="0 0 1000 430" preserveAspectRatio="none" aria-hidden="true">
                <defs><marker id="arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto"><path d="M0,0 L8,4 L0,8 Z" /></marker></defs>
                {visibleGraphEdges.map((edge) => {
                  const sourceIndex = visibleGraphNodes.findIndex((node) => nodeKey(node) === nodeKey(edge.source));
                  const targetIndex = visibleGraphNodes.findIndex((node) => nodeKey(node) === nodeKey(edge.target));
                  if (sourceIndex < 0 || targetIndex < 0) return null;
                  const sourcePosition=graphNodePosition(visibleGraphNodes[sourceIndex],sourceIndex);
                  const targetPosition=graphNodePosition(visibleGraphNodes[targetIndex],targetIndex);
                  return <line key={edge.id} x1={sourcePosition.x*10} y1={sourcePosition.y*4.3} x2={targetPosition.x*10} y2={targetPosition.y*4.3} markerEnd="url(#arrow)" />;
                })}
              </svg>
              {visibleGraphNodes.map((node, index) => (
                <button
                  type="button"
                  className={`node${connectSelection.includes(nodeKey(node)) ? " is-selected" : ""}`}
                  style={{ left: `${graphNodePosition(node,index).x}%`, top: `${graphNodePosition(node,index).y}%`, "--node-scale": graphNodeScale } as React.CSSProperties}
                  key={nodeKey(node)}
                  onPointerDown={(event)=>{event.stopPropagation();startGraphNodeDrag(event,node,index);}}
                  onPointerMove={moveGraphNode}
                  onPointerUp={finishGraphNodeDrag}
                  onPointerCancel={finishGraphNodeDrag}
                  onClick={() => activateGraphNode(node)}
                  aria-pressed={connectSelection.includes(nodeKey(node))}
                >
                  <strong>{node.label}</strong>
                  <span>{node.type}</span>
                </button>
              ))}
              </div>
            </div>
            <div className="edge-list">
              <div className="edge-list-heading"><div><strong>Связи и гипотезы</strong><span>Логика между объектами выбранного дела</span></div><b>{visibleGraphEdges.length}</b></div>
              {visibleGraphEdges.map((edge) => {
                const source = graph?.nodes.find((node) => node.type === edge.source.type && node.id === edge.source.id);
                const target = graph?.nodes.find((node) => node.type === edge.target.type && node.id === edge.target.id);
                return <GraphEdgeCard key={edge.id} edge={edge} sourceLabel={source?.label??edge.source.type} targetLabel={target?.label??edge.target.type} canDelete={currentUser?.role==="DETECTIVE"} onTask={()=>createTaskFromHypothesis(edge)} onReport={()=>startTransition(()=>generateReport(null).catch(handleFormError))} onDelete={()=>startTransition(()=>deleteGraphEdge(edge).catch(handleFormError))}/>;
              })}
              {!visibleGraphEdges.length?<div className="edge-empty"><strong>Связей пока нет</strong><span>Выберите два объекта на доске, чтобы описать связь между ними.</span></div>:null}
            </div>
          </div>
          <div className="report-panel">
            <div className="panel-heading">
              <h2>Отчет</h2>
              <Activity size={18} />
            </div>
            <div className="report-options">
              <label>Формат<select value={reportFormat} onChange={(event)=>setReportFormat(event.target.value)}><option value="TEXT">TEXT</option><option value="HTML">HTML</option></select></label>
              <label>Шаблон<select value={reportTemplate} onChange={(event) => setReportTemplate(event.target.value)}><option value="FULL">Полный аналитический</option><option value="SUMMARY">Краткая сводка</option></select></label>
            </div>
            {preview && reportFormat === "HTML" ? <div className="html-preview" dangerouslySetInnerHTML={{__html:preview.content}} /> : <pre>{preview?.content ?? "Сформируйте предпросмотр, чтобы проверить обязательные поля, экспертизы и гипотезы."}</pre>}
            {preview?.hasOpenLabRequests ? <button className="force-button" onClick={() => startTransition(() => generateReport(true).catch(handleFormError))}>Продолжить с пометкой «Данные ожидаются»</button> : null}
            {approvedReport ? (
              <div className="report-file-actions"><button className="download-button" onClick={() => startTransition(() => downloadReport().catch((error) => setMessage(error.message)))} disabled={isPending}><FileText size={18} /> Скачать {approvedReport.registrationNumber}</button>{currentUser?.role==="DETECTIVE"?<button className="result-action danger-action" onClick={()=>startTransition(()=>deleteEntity(`/reports/${approvedReport.id}`,approvedReport.registrationNumber,()=>setApprovedReport(null)).catch(handleFormError))}>Удалить отчет</button>:null}</div>
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

function WindowActions({onEdit,onDelete}:{onEdit?:()=>void;onDelete?:()=>void}) {
  if(!onEdit&&!onDelete)return null;
  return <div className="window-actions">{onEdit?<button type="button" onClick={onEdit} title="Изменить" aria-label="Изменить"><Pencil size={14}/></button>:null}{onDelete?<button type="button" className="window-delete" onClick={onDelete} title="Удалить" aria-label="Удалить"><X size={16}/></button>:null}</div>;
}

function GraphEdgeCard({edge,sourceLabel,targetLabel,canDelete,onTask,onReport,onDelete}:{edge:Graph["edges"][number];sourceLabel:string;targetLabel:string;canDelete:boolean;onTask:()=>void;onReport:()=>void;onDelete:()=>void}) {
  return <article className="edge-card">
    <div className="edge-route">
      <div className="edge-endpoint"><small>Откуда</small><strong title={sourceLabel}>{sourceLabel}</strong></div>
      <div className="edge-relation"><span>{edge.semanticType}</span><ArrowRight size={17}/></div>
      <div className="edge-endpoint"><small>Куда</small><strong title={targetLabel}>{targetLabel}</strong></div>
    </div>
    <div className="edge-summary">
      <div className="edge-hypothesis"><small>Гипотеза</small><strong>{edge.hypothesisTitle??"Без названия"}</strong>{edge.hypothesisText?<p>{edge.hypothesisText}</p>:null}</div>
      <span className={`confidence-badge confidence-${edge.confidence.toLowerCase()}`}>{edge.confidence}</span>
    </div>
    <div className="edge-actions"><button type="button" onClick={onTask}><ClipboardList size={15}/>Назначить проверку</button><button type="button" onClick={onReport}><FileText size={15}/>В итоговый отчет</button>{canDelete?<button type="button" className="edge-delete" onClick={onDelete} title="Удалить связь" aria-label="Удалить связь"><X size={16}/></button>:null}</div>
  </article>;
}

function GraphNodeSelect({ value, nodes, onChange }: { value: string; nodes: Graph["nodes"]; onChange: (value: string) => void }) {
  return (
    <select value={value} onChange={(event) => onChange(event.target.value)}>
      <option value="">Выберите блок</option>
      {nodes.map((node) => <option key={`${node.type}:${node.id}`} value={`${node.type}:${node.id}`}>{node.label} · {node.type}</option>)}
    </select>
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
