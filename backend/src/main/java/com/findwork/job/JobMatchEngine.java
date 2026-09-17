package com.findwork.job;

import com.findwork.candidate.CandidateProfile;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class JobMatchEngine {
    private static final String VERSION = "v1-role30-skill25-experience20-location15-freshness10";
    private static final Map<String, String> ALIASES = Map.of(
            "js", "javascript", "ts", "typescript", "postgres", "postgresql", "spring", "spring boot");
    private static final Set<String> SENIOR_LEVELS = Set.of("senior", "staff", "principal", "director", "manager");

    private JobMatchEngine() {
    }

    public static String algorithmVersion() {
        return VERSION;
    }

    public static JobMatchResult calculate(CandidateProfile profile, JobPosting job,
                                            JobSemanticAnalysis analysis, String profileHash, Instant now) {
        String title = lower(job.title());
        String category = analysis == null ? "" : lower(analysis.jobCategory());
        List<String> targetRoles = profile == null || profile.targetRoleFamilies() == null ? List.of() : profile.targetRoleFamilies();
        int roleScore = roleScore(title, category, targetRoles);

        List<String> required = analysis != null && !analysis.requiredSkills().isEmpty()
                ? analysis.requiredSkills() : job.skills();
        List<String> preferred = analysis == null ? List.of() : analysis.preferredSkills();
        Set<String> candidateSkills = normalized(profile == null ? List.of() : profile.skills());
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String skill : required) {
            if (candidateSkills.contains(normalize(skill))) matched.add(skill);
            else missing.add(skill);
        }
        int skillScore = skillScore(required, preferred, candidateSkills);

        String seniority = analysis == null ? lower(job.experienceLevel()) : lower(analysis.seniorityLevel());
        int experienceScore = experienceScore(profile, seniority, analysis);
        List<String> concerns = new ArrayList<>();
        if (SENIOR_LEVELS.contains(seniority)) concerns.add("职位级别高于当前优先经验阶段");
        if (!missing.isEmpty()) concerns.add("缺少必需技能：" + String.join("、", missing));

        List<String> hardReasons = new ArrayList<>();
        int locationScore = locationScore(profile, job, hardReasons);
        int freshnessScore = freshnessScore(job.postedAt(), now);
        boolean hardPassed = hardReasons.isEmpty();

        List<String> positive = new ArrayList<>();
        if (roleScore >= 24) positive.add("职位方向匹配");
        if (!matched.isEmpty()) positive.add("技能匹配：" + String.join("、", matched));
        if (analysis != null && Boolean.TRUE.equals(analysis.graduateFriendly())) positive.add("职位明确支持应届/毕业生");
        if (job.city() != null && profile != null && profile.allowedCities().contains(job.city())) positive.add("地点符合候选人偏好");
        if ("REMOTE".equals(job.remoteType()) && profile != null && Boolean.TRUE.equals(profile.remoteAllowed())) positive.add("远程岗位且候选人允许远程");

        int total = Math.max(0, Math.min(100, roleScore + skillScore + experienceScore + locationScore + freshnessScore));
        return new JobMatchResult(total, roleScore, skillScore, experienceScore, locationScore, freshnessScore,
                List.copyOf(matched), List.copyOf(missing), List.copyOf(positive), List.copyOf(concerns),
                hardPassed, List.copyOf(hardReasons), profileHash, VERSION, now);
    }

    private static int roleScore(String title, String category, List<String> roles) {
        if (roles == null || roles.isEmpty()) return 0;
        int best = 0;
        for (String role : roles) {
            String value = lower(role);
            int score = 0;
            if (!value.isBlank() && title.contains(value)) score = 30;
            else if (value.contains("backend") && (title.contains("backend") || title.contains("software engineer"))) score = 26;
            else if (value.contains("ai") && (title.contains("ai") || title.contains("machine learning") || title.contains("llm") || category.equals("data"))) score = 26;
            else if (value.contains("solution") && (title.contains("solution") || category.equals("solutions_engineering"))) score = 24;
            else if (value.contains("software") && (title.contains("engineer") || category.equals("software_engineering"))) score = 24;
            best = Math.max(best, score);
        }
        return best;
    }

    private static int skillScore(List<String> required, List<String> preferred, Set<String> candidate) {
        int requiredPart = required.isEmpty() ? 20 : (int) Math.round(20d * overlap(required, candidate));
        int preferredPart = preferred.isEmpty() ? 5 : (int) Math.round(5d * overlap(preferred, candidate));
        return Math.min(25, requiredPart + preferredPart);
    }

    private static double overlap(List<String> skills, Set<String> candidate) {
        if (skills.isEmpty()) return 1;
        long matched = skills.stream().map(JobMatchEngine::normalize).filter(candidate::contains).count();
        return matched / (double) skills.size();
    }

    private static int experienceScore(CandidateProfile profile, String seniority, JobSemanticAnalysis analysis) {
        if (profile == null) return 0;
        if ("实习".equals(profile.experienceLevel())) return "intern".equals(seniority) ? 20 : SENIOR_LEVELS.contains(seniority) ? 4 : 12;
        if (SENIOR_LEVELS.contains(seniority)) return 4;
        if ("entry".equals(seniority) || "intern".equals(seniority) || "应届/初级".equals(seniority)
                || analysis != null && Boolean.TRUE.equals(analysis.graduateFriendly())) return 20;
        if ("mid".equals(seniority)) return 12;
        return 10;
    }

    private static int locationScore(CandidateProfile profile, JobPosting job, List<String> hardReasons) {
        if (profile == null) return 0;
        if (job.country() != null && !profile.preferredRegions().contains(job.country())) {
            hardReasons.add("LOCATION_NOT_SUPPORTED");
            return 0;
        }
        if ("China".equals(job.country()) && job.city() != null && !profile.allowedCities().contains(job.city())) {
            hardReasons.add("CITY_NOT_SUPPORTED");
            return 0;
        }
        if ("REMOTE".equals(job.remoteType()) && Boolean.TRUE.equals(profile.remoteAllowed())) return 15;
        if (job.city() != null && profile.allowedCities().contains(job.city())) return 15;
        if ("Singapore".equals(job.country()) && profile.preferredRegions().contains("Singapore")) return 15;
        if (job.country() == null || job.city() == null || "UNKNOWN".equals(job.remoteType())) return 8;
        if (!profile.preferredRegions().contains(job.country())) hardReasons.add("LOCATION_NOT_SUPPORTED");
        return 0;
    }

    private static int freshnessScore(Instant postedAt, Instant now) {
        if (postedAt == null || now == null) return 5;
        long days = Math.max(0, Duration.between(postedAt, now).toDays());
        return days <= 1 ? 10 : days <= 7 ? 8 : days <= 30 ? 5 : days <= 90 ? 2 : 1;
    }

    private static Set<String> normalized(List<String> values) {
        Set<String> result = new HashSet<>();
        for (String value : values) result.add(normalize(value));
        return result;
    }

    private static String normalize(String value) {
        String normalized = lower(value).replaceAll("[^a-z0-9+#.]", " ").trim().replaceAll("\\s+", " ");
        return ALIASES.getOrDefault(normalized, normalized);
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
