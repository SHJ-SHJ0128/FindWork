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
  score: number;
  needsReview: boolean;
  postedAt: string | null;
  analysisStatus?: "PENDING" | "ANALYZING" | "SUCCESS" | "FAILED";
  analysisError?: string | null;
  analysis?: { evidence?: Record<string, unknown> } | null;
  matchScore?: number | null;
  match?: JobMatchResult | null;
};

type JobMatchResult = {
  totalScore: number;
  matchedSkills: string[];
  missingRequiredSkills: string[];
  positiveReasons: string[];
  concerns: string[];
  hardFilterPassed: boolean;
};

const profileApi = "http://127.0.0.1:8080/api/candidate-profile";
const jobsApi = "http://127.0.0.1:8080/api/jobs";
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
const gmailSyncing = ref(false);
const gmailMessage = ref("");
const analyzingJobId = ref("");
const analyzeMessage = ref("");
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

const cities = computed(() => ["全部城市", ...new Set(jobs.value.filter(isAllowedRegion).map((job) => job.city).filter(Boolean) as string[])]);
const sources = computed(() => ["全部来源", ...new Set(jobs.value.map((job) => job.source))]);
const filteredJobs = computed(() => jobs.value.filter((job) => {
  const matchesRegion = isAllowedRegion(job);
  const needle = filters.search.trim().toLowerCase();
  const matchesSearch = !needle || `${job.title} ${job.company} ${job.description} ${job.skills.join(" ")}`.toLowerCase().includes(needle);
  const matchesCity = filters.city === "全部城市" || job.city === filters.city;
  const matchesSource = filters.source === "全部来源" || job.source === filters.source;
  const matchesRemote = !filters.remoteOnly || job.remoteType === "REMOTE";
  return matchesRegion && matchesSearch && matchesCity && matchesSource && matchesRemote;
}));
const analyzedCount = computed(() => filteredJobs.value.filter((job) => job.analysisStatus === "SUCCESS").length);
const pendingCount = computed(() => filteredJobs.value.filter((job) => !job.analysisStatus || job.analysisStatus === "PENDING").length);
const highMatchCount = computed(() => filteredJobs.value.filter((job) => (job.matchScore ?? -1) >= 80 && job.match?.hardFilterPassed !== false).length);
const recommendations = computed(() => [...filteredJobs.value]
  .filter((job) => job.matchScore != null && job.match?.hardFilterPassed !== false)
  .sort((a, b) => (b.matchScore ?? -1) - (a.matchScore ?? -1))
  .slice(0, 5));

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

function evidenceText(job: JobPosting) {
  const values = Object.values(job.analysis?.evidence ?? {}).flatMap((value) => Array.isArray(value) ? value : [value]);
  return values.filter((value): value is string => typeof value === "string" && value.trim().length > 0).slice(0, 2).join(" · ");
}

function focusJob(id: string) {
  document.getElementById(`job-${id}`)?.scrollIntoView({ behavior: "smooth", block: "center" });
}

function sourceLabel(source: string) {
  return { LINKEDIN: "LinkedIn", GREENHOUSE: "Greenhouse", DEMO: "Demo" }[source] ?? source;
}

function scoreLabel(job: JobPosting) {
  if (job.source === "DEMO") return String(job.score);
  return job.analysisStatus === "SUCCESS" && job.matchScore != null ? String(job.matchScore) : "—";
}

function scoreCaption(job: JobPosting) {
  if (job.source === "DEMO") return "匹配分";
  return { SUCCESS: "匹配度", FAILED: "分析失败", ANALYZING: "AI分析中", PENDING: "待分析" }[job.analysisStatus ?? "PENDING"];
}

function analysisStatusLabel(job: JobPosting) {
  if (job.source === "DEMO") return "演示";
  return { SUCCESS: "已分析", FAILED: "分析失败", ANALYZING: "AI分析中", PENDING: "待分析" }[job.analysisStatus ?? "PENDING"];
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

function connectGmail() {
  window.location.assign("http://127.0.0.1:8080/api/providers/gmail/authorize");
}

async function syncLinkedInMail() {
  gmailSyncing.value = true;
  gmailMessage.value = "正在读取 LinkedIn 邮件…";
  try {
    const response = await fetch("http://127.0.0.1:8080/api/providers/gmail/linkedin/import", { method: "POST" });
    if (!response.ok) {
      const detail = await response.text();
      throw new Error(detail || `HTTP ${response.status}`);
    }
    const result: { messagesScanned: number; imported: number; failed: number } = await response.json();
    gmailMessage.value = `已扫描 ${result.messagesScanned} 封邮件，导入 ${result.imported} 个岗位${result.failed ? `，${result.failed} 封读取失败` : ""}`;
    await loadJobs();
  } catch {
    gmailMessage.value = "同步失败，请先连接 Gmail 并确认后端配置正确";
  } finally {
    gmailSyncing.value = false;
  }
}

async function analyzeJob(job: JobPosting) {
  analyzingJobId.value = job.id;
  analyzeMessage.value = "正在分析…";
  try {
    const response = await fetch(`http://127.0.0.1:8080/api/ai/jobs/${job.id}/analyze`, { method: "POST" });
    if (!response.ok) throw new Error();
    const updated = await response.json() as JobPosting;
    const index = jobs.value.findIndex((item) => item.id === job.id);
    if (index >= 0) jobs.value[index] = updated;
    analyzeMessage.value = "分析完成";
  } catch {
    analyzeMessage.value = "分析失败，请检查 DeepSeek 配置";
  } finally {
    analyzingJobId.value = "";
  }
}

async function analyzePending() {
  analyzeMessage.value = "正在分析待处理岗位…";
  try {
    const response = await fetch("http://127.0.0.1:8080/api/ai/jobs/analyze-pending?limit=3", { method: "POST" });
    if (!response.ok) throw new Error();
    const result = await response.json() as { succeeded: number; failed: number };
    analyzeMessage.value = `本次完成 ${result.succeeded} 条${result.failed ? `，失败 ${result.failed} 条` : ""}`;
    await loadJobs();
  } catch {
    analyzeMessage.value = "批量分析失败，请检查 DeepSeek 配置";
  }
}

onMounted(async () => {
  await Promise.all([
    loadJobs(),
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
          <p class="eyebrow">JOB INBOX / RECALL FIRST</p>
          <h1>值得先看的岗位</h1>
          <p class="lede">按你的方向和地点偏好排列，远程与待确认职位会单独标记。</p>
          </div>
        <div class="result-summary"><strong>{{ filteredJobs.length }}</strong><span>当前可审阅</span></div>
      </div>

      <section class="recommendation-panel">
        <div class="recommendation-head"><div><p class="eyebrow">TODAY / SHORTLIST</p><h2>今日推荐</h2></div><button type="button" class="secondary-button" @click="analyzePending">分析待处理</button></div>
        <div class="stats"><span><strong>{{ analyzedCount }}</strong>已分析</span><span><strong>{{ pendingCount }}</strong>待分析</span><span><strong>{{ highMatchCount }}</strong>高匹配</span></div>
        <div v-if="recommendations.length" class="recommendation-list"><button v-for="job in recommendations" :key="job.id" type="button" @click="focusJob(job.id)"><strong>{{ job.matchScore }}%</strong><span>{{ job.title }}</span><small>{{ job.company }}</small></button></div>
        <p v-else class="empty-recommendation">完成岗位分析后，这里会显示最高匹配的可审阅职位。</p>
        <p v-if="analyzeMessage" class="import-message">{{ analyzeMessage }}</p>
      </section>

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

      <section class="import-panel gmail-panel">
        <div>
          <h2>同步 LinkedIn 邮件</h2>
          <p>通过 Gmail 只读权限解析岗位提醒；后端每天 08:00（本地时区）自动同步。</p>
        </div>
        <div class="gmail-actions">
          <button type="button" class="secondary-button" @click="connectGmail">连接 Gmail</button>
          <button type="button" :disabled="gmailSyncing" @click="syncLinkedInMail">{{ gmailSyncing ? "同步中…" : "立即同步" }}</button>
        </div>
        <p v-if="gmailMessage" class="import-message">{{ gmailMessage }}</p>
      </section>

      <div class="toolbar">
        <input v-model="filters.search" aria-label="搜索岗位" placeholder="搜索职位、公司或技能" />
        <select v-model="filters.city" aria-label="按城市筛选"><option v-for="city in cities" :key="city">{{ city }}</option></select>
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
            <div class="score"><strong>{{ scoreLabel(job) }}</strong><span>{{ scoreCaption(job) }}</span></div>
          </div>
          <p v-if="formatSalary(job)" class="salary">{{ formatSalary(job) }}</p>
          <p class="description">{{ jobSummary(job) }}</p>
          <p v-if="evidenceText(job)" class="evidence">证据：{{ evidenceText(job) }}</p>
          <div v-if="job.match?.positiveReasons?.length || job.match?.concerns?.length" class="match-notes">
            <span v-for="reason in job.match?.positiveReasons ?? []" :key="reason" class="positive-reason">✓ {{ reason }}</span>
            <span v-for="concern in job.match?.concerns ?? []" :key="concern" class="concern-reason">△ {{ concern }}</span>
          </div>
          <div class="tags">
            <span v-for="skill in job.skills" :key="skill" class="tag">{{ skill }}</span>
            <span v-if="remoteLabel(job.remoteType)" class="tag location-tag">{{ remoteLabel(job.remoteType) }}</span>
            <span v-if="job.needsReview" class="tag review-tag">需审核</span>
            <span v-if="job.source !== 'DEMO'" class="tag ai-tag">{{ analysisStatusLabel(job) }}</span>
          </div>
          <footer class="job-footer">
            <span>{{ job.employmentType }} · {{ job.experienceLevel }}</span>
            <button v-if="job.source !== 'DEMO' && job.analysisStatus !== 'SUCCESS'" type="button" class="analyze-button" :disabled="analyzingJobId === job.id" @click="analyzeJob(job)">{{ analyzingJobId === job.id ? "分析中…" : "分析职位" }}</button>
            <a v-if="job.canonicalUrl" :href="job.canonicalUrl" target="_blank" rel="noopener noreferrer">查看原始职位 ↗</a>
            <span v-else class="missing-link">暂无原始职位链接</span>
          </footer>
        </article>
      </div>
      <p class="demo-note">列表同时保留演示岗位和已导入的真实来源；演示岗位仅用于验证列表、筛选和标记流程。</p>
    </section>

    <section v-else class="profile-view">
      <header>
        <p class="eyebrow">FINDWORK / PROFILE</p>
        <h1>候选人资料</h1>
        <p class="lede">先把你的目标、地点和技能变成可编辑的匹配输入。</p>
      </header>
      <section v-if="profileLoading" class="panel">正在读取本地资料…</section>
      <form v-else class="panel" @submit.prevent="save">
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
    </section>
  </main>
</template>
