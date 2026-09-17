<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";

type CandidateProfile = {
  targetRoleFamilies: string[];
  allowedCities: string[];
  preferredRegions: string[];
  remoteAllowed: boolean;
  experienceLevel: string;
  skills: string[];
};

const api = "http://127.0.0.1:8080/api/candidate-profile";
const loading = ref(true);
const saving = ref(false);
const message = ref("");
const profile = reactive<CandidateProfile>({
  targetRoleFamilies: ["Backend", "AI Application", "Solutions Engineering"],
  allowedCities: ["重庆", "成都", "广州", "深圳", "杭州"],
  preferredRegions: ["China", "Singapore"],
  remoteAllowed: true,
  experienceLevel: "internship-and-entry-level",
  skills: ["Java", "Spring Boot", "SQL"],
});

function split(value: string) {
  return value.split(",").map((item) => item.trim()).filter(Boolean);
}

const roleText = ref(profile.targetRoleFamilies.join(", "));
const cityText = ref(profile.allowedCities.join(", "));
const regionText = ref(profile.preferredRegions.join(", "));
const skillsText = ref(profile.skills.join(", "));

onMounted(async () => {
  try {
    const response = await fetch(api);
    if (response.ok) {
      Object.assign(profile, await response.json());
      roleText.value = profile.targetRoleFamilies.join(", ");
      cityText.value = profile.allowedCities.join(", ");
      regionText.value = profile.preferredRegions.join(", ");
      skillsText.value = profile.skills.join(", ");
    }
  } finally {
    loading.value = false;
  }
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
    const response = await fetch(api, {
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
  <main class="page">
    <header>
      <p class="eyebrow">FINDWORK / PHASE 1</p>
      <h1>候选人资料</h1>
      <p class="lede">先把你的目标、地点和技能变成可编辑的匹配输入。</p>
    </header>

    <section v-if="loading" class="panel">正在读取本地资料…</section>
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
  </main>
</template>
