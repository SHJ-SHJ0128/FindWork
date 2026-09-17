<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";

type CandidateProfile = {
  targetRoleFamilies: string[];
  allowedCities: string[];
  preferredRegions: string[];
  remoteAllowed: boolean;
  experienceLevel: string;
  skills: string[];
};

type JobPosting = {
  id: string;
  title: string;
  company: string;
  country: string | null;
  city: string | null;
  remoteType: "ONSITE" | "HYBRID" | "REMOTE" | "UNKNOWN";
  employmentType: string;
  experienceLevel: string;
  source: string;
  canonicalUrl: string | null;
  description: string;
  summary?: string | null;
  skills: string[];
  salaryMin?: number | null;
  salaryMax?: number | null;
  salaryCurrency?: string | null;
  salaryPeriod?: string | null;
  salaryText?: string | null;
  salarySource?: string | null;
  needsReview: boolean;
  postedAt: string | null;
};

type JobAnalysis = {
  jobCategory?: string | null;
  requiredSkills?: string[];
  preferredSkills?: string[];
  minimumExperienceYears?: number | null;
  maximumExperienceYears?: number | null;
  educationLevel?: string | null;
  seniorityLevel?: string | null;
  graduateFriendly?: boolean | null;
  employmentType?: string | null;
  workplaceType?: string | null;
  responsibilities?: string[];
  workAuthorizationRequired?: boolean | null;
  visaSponsorship?: boolean | null;
  salary?: Record<string, unknown> | null;
  evidence?: Record<string, unknown>;
};

type JobAnalysisResponse = {
  jobId: string;
  analysisStatus: "PENDING" | "ANALYZING" | "SUCCESS" | "FAILED";
  modelName?: string;
  analyzedAt?: string | null;
  analysisError?: string | null;
  analysis?: JobAnalysis | null;
};

type ResumeAnalysis = {
  summary?: string | null;
  experienceLevel?: string | null;
  yearsOfExperience?: number | null;
  technicalSkills?: string[];
  targetRoleSuggestions?: string[];
  strengths?: string[];
  evidence?: Record<string, unknown>;
};

type CandidateResume = {
  id: string;
  originalFilename: string;
  mediaType: string;
  sizeBytes: number;
  status: string;
  errorMessage?: string | null;
  current: boolean;
  createdAt: string;
  updatedAt: string;
  analysis?: { status: string; modelName: string; analysis?: ResumeAnalysis | null; errorMessage?: string | null; analyzedAt: string } | null;
};

const profileApi = "http://127.0.0.1:8080/api/candidate-profile";
const jobsApi = "http://127.0.0.1:8080/api/jobs";
const resumesApi = "http://127.0.0.1:8080/api/resumes";
const view = ref<"jobs" | "profile">("jobs");
const jobs = ref<JobPosting[]>([]);
const jobsLoading = ref(true);
const jobsError = ref("");
const profileLoading = ref(true);
const saving = ref(false);
const message = ref("");
const greenhouseBoard = ref("");
const importing = ref(false);
const importMessage = ref("");
const resume = ref<CandidateResume | null>(null);
const resumeFile = ref<File | null>(null);
const resumeLoading = ref(true);
const resumeUploading = ref(false);
const resumeAnalyzing = ref(false);
const resumeMessage = ref("");
const resumeApplyFields = reactive({ skills: true, targetRoleFamilies: true, experienceLevel: true });
const jobAnalyses = reactive<Record<string, JobAnalysisResponse | undefined>>({});
const jobAnalysisLoading = reactive<Record<string, boolean>>({});
const filters = reactive({ search: "", city: "全部城市", source: "全部来源", remoteOnly: false });

const profile = reactive<CandidateProfile>({
  targetRoleFamilies: ["Backend", "AI Application", "Solutions Engineering"],
  allowedCities: ["重庆", "成都", "广州", "深圳", "杭州"],
  preferredRegions: ["China", "Singapore"],
  remoteAllowed: true,
  experienceLevel: "internship-and-entry-level",
  skills: ["Java", "Spring Boot", "SQL"],
});

const roleText = ref(profile.targetRoleFamilies.join(", "));
const cityText = ref(profile.allowedCities.join(", "));
const regionText = ref(profile.preferredRegions.join(", "));
const skillsText = ref(profile.skills.join(", "));

function isAllowedRegion(job: JobPosting) {
  return job.country === "China" || job.country === "Singapore";
}

function normalizeCity(value: string | null) {
  return (value ?? "").trim().toLowerCase().replace(/市$/, "");
}

const cities = computed(() => ["全部城市", ...new Set(jobs.value.filter(isAllowedRegion).map((job) => job.city).filter(Boolean) as string[])]);
const sources = computed(() => ["全部来源", ...new Set(jobs.value.map((job) => job.source))]);
const filteredJobs = computed(() => jobs.value.filter((job) => {
  const matchesRegion = isAllowedRegion(job);
  const needle = filters.search.trim().toLowerCase();
  const matchesSearch = !needle || `${job.title} ${job.company} ${job.description} ${job.skills.join(" ")}`.toLowerCase().includes(needle);
  const matchesCity = filters.city === "全部城市" || normalizeCity(job.city) === normalizeCity(filters.city);
  const matchesSource = filters.source === "全部来源" || job.source === filters.source;
  const matchesRemote = !filters.remoteOnly || job.remoteType === "REMOTE";
  return matchesRegion && matchesSearch && matchesCity && matchesSource && matchesRemote;
}));
const reviewCount = computed(() => filteredJobs.value.filter((job) => job.needsReview).length);
const remoteCount = computed(() => filteredJobs.value.filter((job) => job.remoteType === "REMOTE").length);
const sourceCount = computed(() => new Set(filteredJobs.value.map((job) => job.source)).size);
function split(value: string) {
  return value.split(",").map((item) => item.trim()).filter(Boolean);
}

function cityLabel(job: JobPosting) {
  if (!job.city) return "地点待确认";
  return job.country && job.country !== "China" ? `${job.city} · ${job.country}` : job.city;
}

function remoteLabel(remoteType: JobPosting["remoteType"]) {
  return { ONSITE: "现场办公", HYBRID: "混合办公", REMOTE: "远程", UNKNOWN: "" }[remoteType];
}

function formatJobDate(value: string | null) {
  if (!value) return "时间待确认";
  return new Intl.DateTimeFormat("zh-CN", { month: "short", day: "numeric" }).format(new Date(value));
}

function compactSalary(value: number) {
  if (value >= 1_000_000 && value % 1_000_000 === 0) return `${value / 1_000_000}M`;
  if (value >= 1_000 && value % 1_000 === 0) return `${value / 1_000}K`;
  if (value >= 1_000) return `${(value / 1_000).toFixed(1).replace(/\.0$/, "")}K`;
  return new Intl.NumberFormat("en-US", { maximumFractionDigits: 2 }).format(value);
}

function formatSalary(job: JobPosting) {
  const raw = job.salaryText?.trim();
  if (raw) return raw;
  if (job.salaryMin == null && job.salaryMax == null) return "";
  const symbol = { USD: "$", SGD: "S$", CNY: "¥", HKD: "HK$", GBP: "£", EUR: "€" }[job.salaryCurrency ?? ""] ?? "";
  const min = job.salaryMin == null ? "" : `${symbol}${compactSalary(job.salaryMin)}`;
  const max = job.salaryMax == null ? "" : `${symbol}${compactSalary(job.salaryMax)}`;
  const amount = min && max ? `${min}-${max}` : min || max;
  return amount ? `${amount}${job.salaryPeriod ? `/${job.salaryPeriod}` : ""}` : "";
}

function jobSummary(job: JobPosting) {
  return job.summary?.trim() || "职位提醒已整理，完整职责请打开原始职位。";
}

function sourceLabel(source: string) {
  return { LINKEDIN: "LinkedIn", GREENHOUSE: "Greenhouse", DEMO: "Demo" }[source] ?? source;
}

function jobAnalysisAction(job: JobPosting) {
  const status = jobAnalyses[job.id]?.analysisStatus;
  return status === "SUCCESS" ? "刷新 AI 整理" : "AI 整理职位";
}

async function analyzeJob(job: JobPosting) {
  if (jobAnalysisLoading[job.id]) return;
  jobAnalysisLoading[job.id] = true;
  try {
    const response = await fetch(`${jobsApi}/${job.id}/analysis`, { method: "POST" });
    if (!response.ok) throw new Error(response.status === 503 ? "DeepSeek 尚未启用，请检查本地 DEEPSEEK_ENABLED 配置" : "职位分析失败，请稍后重试");
    jobAnalyses[job.id] = await response.json();
  } catch (error) {
    jobAnalyses[job.id] = {
      jobId: job.id,
      analysisStatus: "FAILED",
      analysisError: error instanceof Error ? error.message : "职位分析失败",
      analysis: null,
    };
  } finally {
    jobAnalysisLoading[job.id] = false;
  }
}

async function loadJobs() {
  jobsLoading.value = true;
  jobsError.value = "";
  try {
    const response = await fetch(jobsApi);
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    jobs.value = await response.json();
  } catch {
    jobsError.value = "岗位接口暂时不可用，请确认 Spring Boot 已启动。";
  } finally {
    jobsLoading.value = false;
  }
}

async function importGreenhouse() {
  const board = greenhouseBoard.value.trim();
  if (!board) {
    importMessage.value = "请输入 Greenhouse 岗位板 slug 或 URL";
    return;
  }
  importing.value = true;
  importMessage.value = "正在读取公开岗位…";
  try {
    const response = await fetch("http://127.0.0.1:8080/api/providers/greenhouse/import", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ board }),
    });
    if (!response.ok) {
      const detail = await response.text();
      throw new Error(detail || `HTTP ${response.status}`);
    }
    const result: { imported: number; board: string } = await response.json();
    importMessage.value = `已从 ${result.board} 导入 ${result.imported} 条岗位`;
    await loadJobs();
  } catch {
    importMessage.value = "导入失败，请检查岗位板 slug/URL 和后端网络连接";
  } finally {
    importing.value = false;
  }
}

async function loadResume() {
  resumeLoading.value = true;
  try {
    const response = await fetch(`${resumesApi}/current`);
    resume.value = response.status === 204 ? null : response.ok ? await response.json() : null;
  } catch {
    resumeMessage.value = "简历接口暂时不可用，请确认 Spring Boot 已启动。";
  } finally {
    resumeLoading.value = false;
  }
}

function chooseResume(event: Event) {
  const input = event.target as HTMLInputElement;
  resumeFile.value = input.files?.[0] ?? null;
  resumeMessage.value = resumeFile.value ? `已选择 ${resumeFile.value.name}` : "";
}

async function uploadResume() {
  if (!resumeFile.value) {
    resumeMessage.value = "请选择 PDF 或 DOCX 简历";
    return;
  }
  resumeUploading.value = true;
  resumeMessage.value = "正在提取简历文字…";
  try {
    const body = new FormData();
    body.append("file", resumeFile.value);
    const response = await fetch(resumesApi, { method: "POST", body });
    if (!response.ok) throw new Error(await response.text());
    resume.value = await response.json();
    resumeFile.value = null;
    resumeMessage.value = resume.value?.status === "SUCCESS" ? "简历已上传并完成分析" : "简历已上传，等待 AI 分析";
  } catch (error) {
    resumeMessage.value = error instanceof Error && error.message ? error.message : "简历上传失败，请检查文件格式和后端连接";
  } finally {
    resumeUploading.value = false;
  }
}

async function analyzeResume() {
  if (!resume.value) return;
  resumeAnalyzing.value = true;
  resumeMessage.value = "正在请求 DeepSeek 分析…";
  try {
    const response = await fetch(`${resumesApi}/${resume.value.id}/analyze`, { method: "POST" });
    if (!response.ok) throw new Error(await response.text());
    resume.value = await response.json();
    resumeMessage.value = "简历分析已完成，请核对字段建议";
  } catch (error) {
    resumeMessage.value = error instanceof Error && error.message ? error.message : "简历分析失败";
    await loadResume();
  } finally {
    resumeAnalyzing.value = false;
  }
}

async function applyResumeSuggestions() {
  if (!resume.value?.analysis?.analysis) return;
  const fields = Object.entries(resumeApplyFields).filter(([, selected]) => selected).map(([field]) => field);
  if (!fields.length) {
    resumeMessage.value = "至少选择一项资料字段";
    return;
  }
  try {
    const response = await fetch(`${resumesApi}/${resume.value.id}/apply-to-profile`, {
      method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ fields }),
    });
    if (!response.ok) throw new Error(await response.text());
    const updated: CandidateProfile = await response.json();
    Object.assign(profile, updated);
    roleText.value = profile.targetRoleFamilies.join(", ");
    skillsText.value = profile.skills.join(", ");
    resumeMessage.value = "已将选中的建议写入候选人资料";
  } catch (error) {
    resumeMessage.value = error instanceof Error && error.message ? error.message : "资料建议应用失败";
  }
}

onMounted(async () => {
  await Promise.all([
    loadJobs(),
    loadResume(),
    fetch(profileApi).then(async (response) => {
      if (response.ok) {
        Object.assign(profile, await response.json());
        roleText.value = profile.targetRoleFamilies.join(", ");
        cityText.value = profile.allowedCities.join(", ");
        regionText.value = profile.preferredRegions.join(", ");
        skillsText.value = profile.skills.join(", ");
      }
    }).catch(() => undefined).finally(() => { profileLoading.value = false; }),
  ]);
});

async function save() {
  saving.value = true;
  message.value = "";
  Object.assign(profile, {
    targetRoleFamilies: split(roleText.value),
    allowedCities: split(cityText.value),
    preferredRegions: split(regionText.value),
    skills: split(skillsText.value),
  });
  try {
    const response = await fetch(profileApi, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(profile),
    });
    message.value = response.ok ? "已保存" : "保存失败，请检查后端服务";
  } catch {
    message.value = "无法连接 Spring Boot 后端";
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <main class="shell">
    <header class="topbar">
      <div class="brand"><span class="brand-mark">FW</span><span>FindWork</span></div>
      <nav aria-label="主导航">
        <button :class="{ active: view === 'jobs' }" type="button" @click="view = 'jobs'">岗位 <span class="nav-count">{{ filteredJobs.length }}</span></button>
        <button :class="{ active: view === 'profile' }" type="button" @click="view = 'profile'">候选人资料</button>
      </nav>
      <span class="system-status"><i></i> 本地运行</span>
    </header>

    <section v-if="view === 'jobs'" class="jobs-view">
      <div class="jobs-heading">
        <div>
          <div class="eyebrow-row"><p class="eyebrow">JOB INBOX / RECALL FIRST</p><span class="date-stamp">本地工作区 · 2026</span></div>
          <h1>值得先看的岗位<span class="heading-mark">.</span></h1>
          <p class="lede">按你的方向和地点偏好排列。先保留更多可能，再用筛选快速缩小范围。</p>
        </div>
        <div class="summary-rail" aria-label="岗位概览">
          <div class="summary-cell summary-cell-primary"><strong>{{ filteredJobs.length }}</strong><span>当前可审阅</span></div>
          <div class="summary-cell"><strong>{{ remoteCount }}</strong><span>远程选项</span></div>
          <div class="summary-cell"><strong>{{ reviewCount }}</strong><span>待确认</span></div>
          <div class="summary-cell"><strong>{{ sourceCount }}</strong><span>岗位来源</span></div>
        </div>
      </div>

      <section class="import-panel">
        <div>
          <h2>导入真实岗位</h2>
          <p>先支持公开的 Greenhouse 岗位板，不需要账号或密码。</p>
        </div>
        <form class="import-form" @submit.prevent="importGreenhouse">
          <label>岗位板 slug 或 URL
            <input v-model="greenhouseBoard" aria-label="Greenhouse 岗位板 slug 或 URL" placeholder="例如：company 或 boards.greenhouse.io/company" />
          </label>
          <button :disabled="importing" type="submit">{{ importing ? "导入中…" : "导入 Greenhouse" }}</button>
        </form>
        <p v-if="importMessage" class="import-message">{{ importMessage }}</p>
      </section>

      <div class="toolbar-head"><span>筛选岗位</span><span>{{ filteredJobs.length }} 条结果</span></div>
      <div class="toolbar">
        <input v-model="filters.search" aria-label="搜索岗位" placeholder="搜索职位、公司或技能" />
        <select v-model="filters.city" aria-label="按城市筛选"><option v-for="city in cities" :key="city" :value="city">{{ city }}</option></select>
        <select v-model="filters.source" aria-label="按来源筛选"><option v-for="source in sources" :key="source">{{ source }}</option></select>
        <label class="remote-filter"><input v-model="filters.remoteOnly" type="checkbox" /> 只看远程</label>
      </div>

      <div v-if="jobsLoading" class="job-list" aria-busy="true">
        <div v-for="index in 3" :key="index" class="job-card skeleton"><span></span><span></span><span></span></div>
      </div>
      <div v-else-if="jobsError" class="state-panel error-state">{{ jobsError }}</div>
      <div v-else-if="filteredJobs.length === 0" class="state-panel">没有符合当前筛选条件的岗位。</div>
      <div v-else class="job-list">
        <article v-for="job in filteredJobs" :id="`job-${job.id}`" :key="job.id" class="job-card">
          <div class="job-card-top">
            <div>
              <div class="source-line"><span class="source-badge">{{ sourceLabel(job.source) }}</span><span>{{ formatJobDate(job.postedAt) }}</span></div>
              <h2>{{ job.title }}</h2>
              <p class="company">{{ job.company }} <span>·</span> {{ cityLabel(job) }}</p>
            </div>
          </div>
          <p v-if="formatSalary(job)" class="salary">{{ formatSalary(job) }}</p>
          <p class="description">{{ jobSummary(job) }}</p>
          <div class="tags">
            <span v-for="skill in job.skills" :key="skill" class="tag">{{ skill }}</span>
            <span v-if="remoteLabel(job.remoteType)" class="tag location-tag">{{ remoteLabel(job.remoteType) }}</span>
            <span v-if="job.needsReview" class="tag review-tag">需审核</span>
          </div>
          <div class="job-ai-actions">
            <button type="button" class="text-button job-ai-button" :disabled="jobAnalysisLoading[job.id]" @click="analyzeJob(job)">
              {{ jobAnalysisLoading[job.id] ? "分析中…" : jobAnalysisAction(job) }}
            </button>
            <span>只整理 JD，不生成匹配分</span>
          </div>
          <section v-if="jobAnalyses[job.id]" class="job-analysis" aria-live="polite">
            <div v-if="jobAnalyses[job.id]?.analysis" class="job-analysis-content">
              <div class="job-analysis-label">AI 职位整理</div>
              <p v-if="jobAnalyses[job.id]?.analysis?.jobCategory" class="job-analysis-meta">{{ jobAnalyses[job.id]?.analysis?.jobCategory }}</p>
              <div class="job-analysis-groups">
                <div v-if="jobAnalyses[job.id]?.analysis?.requiredSkills?.length"><strong>必备技能</strong><span>{{ jobAnalyses[job.id]?.analysis?.requiredSkills?.join(" · ") }}</span></div>
                <div v-if="jobAnalyses[job.id]?.analysis?.preferredSkills?.length"><strong>加分技能</strong><span>{{ jobAnalyses[job.id]?.analysis?.preferredSkills?.join(" · ") }}</span></div>
                <div v-if="jobAnalyses[job.id]?.analysis?.responsibilities?.length"><strong>主要职责</strong><span>{{ jobAnalyses[job.id]?.analysis?.responsibilities?.slice(0, 3).join(" · ") }}</span></div>
              </div>
            </div>
            <p v-else class="job-analysis-error">{{ jobAnalyses[job.id]?.analysisError || "暂无可用分析结果" }}</p>
          </section>
          <footer class="job-footer">
            <span>{{ job.employmentType }} · {{ job.experienceLevel }}</span>
            <a v-if="job.canonicalUrl" :href="job.canonicalUrl" target="_blank" rel="noopener noreferrer">查看原始职位 ↗</a>
            <span v-else class="missing-link">暂无原始职位链接</span>
          </footer>
        </article>
      </div>
      <p class="demo-note">列表仅保留已导入的真实来源；国家和地点不明的记录不会进入当前审阅视图。</p>
    </section>

    <section v-else class="profile-view">
      <header>
        <p class="eyebrow">FINDWORK / PROFILE</p>
        <h1>候选人资料</h1>
        <p class="lede">先把你的目标、地点和技能变成可编辑的筛选输入。</p>
      </header>
      <section v-if="profileLoading" class="panel">正在读取本地资料…</section>
      <form v-else class="panel" @submit.prevent="save">
        <div class="panel-intro"><span class="panel-kicker">PROFILE INPUTS</span><p>这些偏好只用于整理岗位，不会替你自动投递。</p></div>
        <label>目标职位族（逗号分隔）<input v-model="roleText" /></label>
        <label>允许城市（逗号分隔）<input v-model="cityText" /></label>
        <label>目标地区（逗号分隔）<input v-model="regionText" /></label>
        <label>技能（逗号分隔）<input v-model="skillsText" /></label>
        <label>经验层级
          <select v-model="profile.experienceLevel">
            <option value="internship-and-entry-level">实习 + 应届/初级全职</option>
            <option value="full-time">全职</option>
          </select>
        </label>
        <label class="check"><input v-model="profile.remoteAllowed" type="checkbox" /> 纳入远程职位（单独标记）</label>
        <div class="actions"><button :disabled="saving" type="submit">{{ saving ? "保存中…" : "保存资料" }}</button><span>{{ message }}</span></div>
      </form>
      <section class="resume-panel">
        <div class="panel-intro"><span class="panel-kicker">RESUME AI</span><h2>简历解析</h2><p>上传 PDF 或 DOCX，AI 只提出建议；确认后才会写入资料。</p></div>
        <div v-if="resumeLoading" class="resume-state">正在读取当前简历…</div>
        <template v-else>
          <div class="resume-upload">
            <label class="file-picker">选择简历<input type="file" accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document" @change="chooseResume" /></label>
            <span class="file-name">{{ resumeFile?.name || "支持 PDF / DOCX，最大 10MB" }}</span>
            <button type="button" :disabled="resumeUploading" @click="uploadResume">{{ resumeUploading ? "提取中…" : "上传并解析" }}</button>
          </div>
          <div v-if="resume" class="resume-record">
            <div class="resume-record-head"><div><strong>{{ resume.originalFilename }}</strong><span>{{ Math.ceil(resume.sizeBytes / 1024) }} KB · {{ resume.status }}</span></div><button v-if="resume.analysis?.status !== 'SUCCESS'" type="button" class="text-button" :disabled="resumeAnalyzing" @click="analyzeResume">{{ resumeAnalyzing ? "分析中…" : "开始 AI 分析" }}</button></div>
            <p v-if="resume.errorMessage" class="resume-error">{{ resume.errorMessage }}</p>
            <div v-if="resume.analysis?.analysis" class="resume-analysis">
              <p v-if="resume.analysis.analysis.summary" class="resume-summary">{{ resume.analysis.analysis.summary }}</p>
              <div class="suggestion-groups">
                <label class="suggestion-group"><input v-model="resumeApplyFields.skills" type="checkbox" /><span><strong>技能建议</strong><small>{{ resume.analysis.analysis.technicalSkills?.join(" · ") || "暂无" }}</small></span></label>
                <label class="suggestion-group"><input v-model="resumeApplyFields.targetRoleFamilies" type="checkbox" /><span><strong>目标职位建议</strong><small>{{ resume.analysis.analysis.targetRoleSuggestions?.join(" · ") || "暂无" }}</small></span></label>
                <label class="suggestion-group"><input v-model="resumeApplyFields.experienceLevel" type="checkbox" /><span><strong>经验判断</strong><small>{{ resume.analysis.analysis.experienceLevel || "待确认" }}{{ resume.analysis.analysis.yearsOfExperience != null ? ` · ${resume.analysis.analysis.yearsOfExperience} 年` : "" }}</small></span></label>
              </div>
              <button type="button" class="apply-suggestions" @click="applyResumeSuggestions">应用选中的建议</button>
            </div>
          </div>
          <p v-if="resumeMessage" class="resume-message">{{ resumeMessage }}</p>
        </template>
      </section>
    </section>
  </main>
</template>
