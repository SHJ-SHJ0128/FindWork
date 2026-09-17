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
  skills: string[];
  score: number;
  needsReview: boolean;
  postedAt: string | null;
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

const cities = computed(() => ["全部城市", ...new Set(jobs.value.map((job) => job.city).filter(Boolean) as string[])]);
const sources = computed(() => ["全部来源", ...new Set(jobs.value.map((job) => job.source))]);
const filteredJobs = computed(() => jobs.value.filter((job) => {
  const needle = filters.search.trim().toLowerCase();
  const matchesSearch = !needle || `${job.title} ${job.company} ${job.description} ${job.skills.join(" ")}`.toLowerCase().includes(needle);
  const matchesCity = filters.city === "全部城市" || job.city === filters.city;
  const matchesSource = filters.source === "全部来源" || job.source === filters.source;
  const matchesRemote = !filters.remoteOnly || job.remoteType === "REMOTE";
  return matchesSearch && matchesCity && matchesSource && matchesRemote;
}));

function split(value: string) {
  return value.split(",").map((item) => item.trim()).filter(Boolean);
}

function cityLabel(job: JobPosting) {
  if (!job.city) return "地点待确认";
  return job.country && job.country !== "China" ? `${job.city} · ${job.country}` : job.city;
}

function remoteLabel(remoteType: JobPosting["remoteType"]) {
  return { ONSITE: "现场办公", HYBRID: "混合办公", REMOTE: "远程", UNKNOWN: "办公方式待确认" }[remoteType];
}

function postedLabel(value: string | null) {
  if (!value) return "时间待确认";
  return new Intl.DateTimeFormat("zh-CN", { month: "short", day: "numeric" }).format(new Date(value));
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
        <button :class="{ active: view === 'jobs' }" type="button" @click="view = 'jobs'">岗位 <span class="nav-count">{{ jobs.length }}</span></button>
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
        <article v-for="job in filteredJobs" :key="job.id" class="job-card">
          <div class="job-card-top">
            <div>
              <div class="source-line"><span class="source-badge">{{ job.source }}</span><span>{{ postedLabel(job.postedAt) }}</span></div>
              <h2>{{ job.title }}</h2>
              <p class="company">{{ job.company }} <span>·</span> {{ cityLabel(job) }}</p>
            </div>
            <div class="score"><strong>{{ job.score }}</strong><span>匹配分</span></div>
          </div>
          <p class="description">{{ job.description }}</p>
          <div class="tags">
            <span v-for="skill in job.skills" :key="skill" class="tag">{{ skill }}</span>
            <span class="tag location-tag">{{ remoteLabel(job.remoteType) }}</span>
            <span v-if="job.needsReview" class="tag review-tag">需审核</span>
          </div>
          <footer class="job-footer">
            <span>{{ job.employmentType }} · {{ job.experienceLevel }}</span>
            <a v-if="job.canonicalUrl" :href="job.canonicalUrl" target="_blank" rel="noreferrer">查看来源 ↗</a>
          </footer>
        </article>
      </div>
      <p class="demo-note">当前岗位为演示数据，用于验证列表、筛选和标记流程；接入 LinkedIn、BOSS、Gmail 后会替换为真实来源。</p>
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
