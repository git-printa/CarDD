package com.cardamage.api.service;

import com.cardamage.api.config.AppProperties;
import com.cardamage.api.model.analytics.AnalyticsBreakdownItem;
import com.cardamage.api.model.analytics.AnalyticsSummaryResponse;
import com.cardamage.api.model.chat.ChatQueryResponse;
import com.cardamage.api.model.chat.ChatSkillInfo;
import com.cardamage.api.repository.AssessmentRepository;
import com.cardamage.api.repository.ChatAuditRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ChatAnalyticsService {

    private static final String CHAT_MODE_ANALYTICS_ONLY = "analytics_only";
    private static final String CHAT_MODE_OLLAMA_HYBRID = "ollama_hybrid";
    private static final String CHAT_MODE_ASSISTANT_FULL = "assistant_full";

    private static final String ROLE_VIEWER = "viewer";
    private static final String ROLE_ANALYST = "analyst";
    private static final String ROLE_ADMIN = "admin";

    private final AnalyticsService analyticsService;
    private final AssessmentRepository assessmentRepository;
    private final ChatAuditRepository chatAuditRepository;
    private final AppProperties appProperties;
    private final OllamaService ollamaService;
    private final ObjectMapper objectMapper;

    public ChatAnalyticsService(
        AnalyticsService analyticsService,
        AssessmentRepository assessmentRepository,
        ChatAuditRepository chatAuditRepository,
        AppProperties appProperties,
        OllamaService ollamaService,
        ObjectMapper objectMapper
    ) {
        this.analyticsService = analyticsService;
        this.assessmentRepository = assessmentRepository;
        this.chatAuditRepository = chatAuditRepository;
        this.appProperties = appProperties;
        this.ollamaService = ollamaService;
        this.objectMapper = objectMapper;
    }

    public List<ChatSkillInfo> availableSkills() {
        return List.of(
            new ChatSkillInfo("getDamageSummary", "Total batches/images/detections/cost for a time window", ROLE_VIEWER),
            new ChatSkillInfo("getCostBreakdown", "Cost and counts grouped by part or damage type", ROLE_VIEWER),
            new ChatSkillInfo("comparePeriods", "Compare recent period against previous period", ROLE_ANALYST),
            new ChatSkillInfo("recommendTrainingNeed", "Class distribution signal for training data coverage", ROLE_ANALYST),
            new ChatSkillInfo("generalAssistant", "General assistant for product, workflow, and troubleshooting questions", ROLE_VIEWER)
        );
    }

    public ChatQueryResponse query(String userRole, String prompt, Integer preferredDays) {
        String role = normalizeRole(userRole);
        String safePrompt = prompt == null ? "" : prompt.trim();
        if (safePrompt.isBlank()) {
            throw new IllegalArgumentException("Prompt is required");
        }
        int days = clampDays(preferredDays == null ? inferDaysFromPrompt(safePrompt.toLowerCase(Locale.ROOT)) : preferredDays);
        String mode = effectiveChatMode();

        if (CHAT_MODE_ASSISTANT_FULL.equals(mode) && !isAnalyticsIntent(safePrompt)) {
            ChatQueryResponse response = runGeneralAssistant(safePrompt, days);
            chatAuditRepository.insertAudit(
                role,
                safePrompt,
                mode,
                "generalAssistant",
                "{\"time_range_days\":" + days + ",\"dimension\":\"none\"}",
                truncate(response.getAnswer(), 500),
                true,
                null
            );
            return response;
        }

        SkillPlan plan = selectSkillPlan(safePrompt, days);
        ensureRoleAllowed(role, plan.skillId);

        try {
            ChatQueryResponse response = executePlan(plan);
            if (CHAT_MODE_ASSISTANT_FULL.equals(mode)) {
                response = enrichAnalyticsAnswerWithAssistant(safePrompt, response);
            }
            chatAuditRepository.insertAudit(
                role,
                safePrompt,
                mode,
                plan.skillId,
                "{\"time_range_days\":" + plan.timeRangeDays + ",\"dimension\":\"" + plan.dimension + "\"}",
                truncate(response.getAnswer(), 500),
                true,
                null
            );
            return response;
        } catch (RuntimeException ex) {
            chatAuditRepository.insertAudit(
                role,
                safePrompt,
                mode,
                plan.skillId,
                "{\"time_range_days\":" + plan.timeRangeDays + ",\"dimension\":\"" + plan.dimension + "\"}",
                null,
                false,
                truncate(ex.getMessage(), 300)
            );
            throw ex;
        }
    }

    private ChatQueryResponse executePlan(SkillPlan plan) {
        switch (plan.skillId) {
            case "getDamageSummary":
                return runDamageSummary(plan.timeRangeDays);
            case "getCostBreakdown":
                return runBreakdown(plan.timeRangeDays, plan.dimension);
            case "comparePeriods":
                return runComparePeriods(plan.timeRangeDays);
            case "recommendTrainingNeed":
                return runTrainingRecommendation(plan.timeRangeDays);
            default:
                return runDamageSummary(plan.timeRangeDays);
        }
    }

    private ChatQueryResponse runDamageSummary(int days) {
        AnalyticsSummaryResponse summary = analyticsService.summary(days);
        String answer = String.format(
            Locale.ROOT,
            "In the last %d days: %d batches, %d images, %d billable detections, and estimated total cost $%d.",
            summary.getTimeRangeDays(),
            summary.getBatches(),
            summary.getImages(),
            summary.getDetections(),
            summary.getTotalCost()
        );
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("summary", summary);
        return new ChatQueryResponse(
            answer,
            "damage_summary",
            "getDamageSummary",
            days,
            List.of("upload_batches", "assessment_images", "assessment_detections"),
            data
        );
    }

    private ChatQueryResponse runBreakdown(int days, String dimension) {
        List<AnalyticsBreakdownItem> rows;
        String queryType;
        if ("damage_type".equals(dimension)) {
            rows = analyticsService.topDamageTypes(days, 6);
            queryType = "breakdown_damage_type";
        } else {
            rows = analyticsService.costByPart(days, 6);
            queryType = "breakdown_part";
        }
        String top = rows.isEmpty() ? "No records found." : rows.stream()
            .map(r -> r.getName() + " ($" + r.getTotalCost() + ", " + r.getCount() + " detections)")
            .collect(Collectors.joining("; "));
        String answer = "Top " + ("damage_type".equals(dimension) ? "damage types" : "parts")
            + " over the last " + days + " days: " + top;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dimension", dimension);
        data.put("rows", rows);
        return new ChatQueryResponse(
            answer,
            queryType,
            "getCostBreakdown",
            days,
            List.of("assessment_detections"),
            data
        );
    }

    private ChatQueryResponse runComparePeriods(int days) {
        int safeDays = Math.max(1, Math.min(days, 365));
        Instant now = Instant.now();
        Instant currentStart = now.minus(safeDays, ChronoUnit.DAYS);
        Instant previousStart = currentStart.minus(safeDays, ChronoUnit.DAYS);
        long currentCost = assessmentRepository.sumEstimatedCostSince(currentStart);
        long currentDetections = assessmentRepository.countDetectionsSince(currentStart);
        long previousCost = assessmentRepository.sumEstimatedCostSince(previousStart) - currentCost;
        long previousDetections = assessmentRepository.countDetectionsSince(previousStart) - currentDetections;
        long costDelta = currentCost - Math.max(previousCost, 0);
        long detDelta = currentDetections - Math.max(previousDetections, 0);
        String answer = String.format(
            Locale.ROOT,
            "Comparing the last %d days vs previous %d days: cost delta is $%d and detection delta is %d.",
            safeDays, safeDays, costDelta, detDelta
        );
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("current_period_days", safeDays);
        data.put("current_cost", currentCost);
        data.put("previous_cost", Math.max(previousCost, 0));
        data.put("current_detections", currentDetections);
        data.put("previous_detections", Math.max(previousDetections, 0));
        data.put("cost_delta", costDelta);
        data.put("detections_delta", detDelta);
        return new ChatQueryResponse(
            answer,
            "compare_periods",
            "comparePeriods",
            safeDays,
            List.of("assessment_detections"),
            data
        );
    }

    private ChatQueryResponse runTrainingRecommendation(int days) {
        List<AnalyticsBreakdownItem> classes = analyticsService.topDamageTypes(days, 10);
        Map<String, Long> byClass = new LinkedHashMap<>();
        byClass.put("scratch", 0L);
        byClass.put("dent", 0L);
        byClass.put("crack", 0L);
        for (AnalyticsBreakdownItem row : classes) {
            if (byClass.containsKey(row.getName())) {
                byClass.put(row.getName(), row.getCount());
            }
        }
        long min = byClass.values().stream().min(Long::compareTo).orElse(0L);
        long max = byClass.values().stream().max(Long::compareTo).orElse(0L);
        boolean imbalanced = max > Math.max(1, min) * 2;
        String answer;
        if (max == 0) {
            answer = "No detections are stored yet. Upload/analyze more data before retraining.";
        } else if (imbalanced) {
            answer = "Class distribution is imbalanced. Prioritize labeling for underrepresented classes before retraining.";
        } else {
            answer = "Class distribution looks reasonably balanced for a small incremental retraining run.";
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("counts", byClass);
        data.put("imbalanced", imbalanced);
        return new ChatQueryResponse(
            answer,
            "training_need",
            "recommendTrainingNeed",
            days,
            List.of("assessment_detections", "training_labels"),
            data
        );
    }

    private SkillPlan selectSkillPlan(String prompt, Integer preferredDays) {
        SkillPlan base = deterministicPlan(prompt, preferredDays);
        if (!CHAT_MODE_OLLAMA_HYBRID.equals(effectiveChatMode()) && !CHAT_MODE_ASSISTANT_FULL.equals(effectiveChatMode())) {
            return base;
        }
        try {
            String llmPrompt = "You are a router. Return JSON only with keys skill,time_range_days,dimension. "
                + "Allowed skill values: getDamageSummary,getCostBreakdown,comparePeriods,recommendTrainingNeed. "
                + "Allowed dimension values: part,damage_type. "
                + "User query: " + prompt;
            String raw = ollamaService.askForSkillJson(llmPrompt);
            JsonNode node = objectMapper.readTree(extractJson(raw));
            String skill = text(node, "skill", base.skillId);
            int days = intValue(node, "time_range_days", base.timeRangeDays);
            String dimension = text(node, "dimension", base.dimension);
            return new SkillPlan(validSkill(skill), clampDays(days), validDimension(dimension));
        } catch (Exception ignored) {
            return base;
        }
    }

    private SkillPlan deterministicPlan(String prompt, Integer preferredDays) {
        String q = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);
        int days = clampDays(preferredDays == null ? inferDaysFromPrompt(q) : preferredDays);
        if (q.contains("compare") || q.contains("previous") || q.contains("versus")) {
            return new SkillPlan("comparePeriods", days, "part");
        }
        if (q.contains("train") || q.contains("dataset") || q.contains("imbalance") || q.contains("coverage")) {
            return new SkillPlan("recommendTrainingNeed", days, "part");
        }
        if (q.contains("part") || q.contains("breakdown") || q.contains("top") || q.contains("cost by")) {
            String dimension = q.contains("type") || q.contains("class") ? "damage_type" : "part";
            return new SkillPlan("getCostBreakdown", days, dimension);
        }
        return new SkillPlan("getDamageSummary", days, "part");
    }

    private int inferDaysFromPrompt(String q) {
        if (q.contains("today") || q.contains("24h")) {
            return 1;
        }
        if (q.contains("week") || q.contains("7 days")) {
            return 7;
        }
        if (q.contains("month") || q.contains("30 days")) {
            return 30;
        }
        if (q.contains("quarter") || q.contains("90 days")) {
            return 90;
        }
        return 30;
    }

    private void ensureRoleAllowed(String userRole, String skill) {
        if (CHAT_MODE_ASSISTANT_FULL.equals(effectiveChatMode())) {
            return;
        }
        if ("comparePeriods".equals(skill) || "recommendTrainingNeed".equals(skill)) {
            if (!ROLE_ANALYST.equals(userRole) && !ROLE_ADMIN.equals(userRole)) {
                throw new IllegalArgumentException("Role " + userRole + " cannot use skill " + skill + ". Use analyst/admin role.");
            }
        }
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return ROLE_VIEWER;
        }
        String normalized = role.trim().toLowerCase(Locale.ROOT);
        if (ROLE_ADMIN.equals(normalized) || ROLE_ANALYST.equals(normalized) || ROLE_VIEWER.equals(normalized)) {
            return normalized;
        }
        return ROLE_VIEWER;
    }

    private String effectiveChatMode() {
        String mode = appProperties.getChatMode();
        if (mode == null || mode.isBlank()) {
            return CHAT_MODE_ANALYTICS_ONLY;
        }
        return mode.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isAnalyticsIntent(String prompt) {
        String q = prompt.toLowerCase(Locale.ROOT);
        return q.contains("damage")
            || q.contains("cost")
            || q.contains("trend")
            || q.contains("compare")
            || q.contains("breakdown")
            || q.contains("summary")
            || q.contains("batch")
            || q.contains("detection")
            || q.contains("dataset")
            || q.contains("train")
            || q.contains("label")
            || q.contains("class");
    }

    private ChatQueryResponse runGeneralAssistant(String prompt, int days) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mode", effectiveChatMode());
        data.put("ollama_available", ollamaService.isAvailable());
        data.put("time_range_days", days);

        if (!ollamaService.isAvailable()) {
            String answer = "General assistant is enabled, but the LLM provider is offline. "
                + "Start Ollama (or set OLLAMA_URL/OLLAMA_MODEL), then retry. "
                + "You can still ask analytics questions now and I will answer from persisted data.";
            return new ChatQueryResponse(
                answer,
                "general_assistant",
                "generalAssistant",
                days,
                List.of(),
                data
            );
        }

        String llmPrompt = "You are Car Damage AI assistant. Help with product usage, troubleshooting, training, deployment, "
            + "and workflow questions. Be concise and practical. If the user asks for app analytics values, say they should ask "
            + "an analytics question and provide an example.\n\n"
            + "User question: " + prompt;
        String answer;
        try {
            answer = ollamaService.ask(llmPrompt).trim();
        } catch (RuntimeException ex) {
            answer = "I could not reach the LLM provider right now. "
                + "Please retry in a few seconds, or ask a direct analytics question such as "
                + "\"compare last 7 days vs previous 7 days\".";
        }
        return new ChatQueryResponse(
            answer,
            "general_assistant",
            "generalAssistant",
            days,
            List.of(),
            data
        );
    }

    private ChatQueryResponse enrichAnalyticsAnswerWithAssistant(String prompt, ChatQueryResponse base) {
        if (!ollamaService.isAvailable()) {
            return base;
        }
        try {
            String llmPrompt = "You are Car Damage AI analytics assistant. Rewrite the answer for the user in clear concise form "
                + "with 2-4 short bullet points. Keep all numeric values exactly as provided.\n\n"
                + "User question: " + prompt + "\n"
                + "Skill used: " + base.getSkillUsed() + "\n"
                + "Raw answer: " + base.getAnswer() + "\n"
                + "Data JSON: " + objectMapper.writeValueAsString(base.getData());
            String rewritten = ollamaService.ask(llmPrompt).trim();
            if (rewritten.isBlank()) {
                return base;
            }
            return new ChatQueryResponse(
                rewritten,
                base.getQueryType(),
                base.getSkillUsed(),
                base.getTimeRangeDays(),
                base.getSources(),
                base.getData()
            );
        } catch (Exception ignored) {
            return base;
        }
    }

    private String extractJson(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("No JSON found");
        }
        return raw.substring(start, end + 1);
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode v = node.get(field);
        if (v == null || v.asText().isBlank()) {
            return fallback;
        }
        return v.asText();
    }

    private int intValue(JsonNode node, String field, int fallback) {
        JsonNode v = node.get(field);
        if (v == null || !v.canConvertToInt()) {
            return fallback;
        }
        return v.asInt();
    }

    private String validSkill(String skill) {
        List<String> allowed = availableSkills().stream().map(ChatSkillInfo::getId).collect(Collectors.toList());
        if (allowed.contains(skill)) {
            return skill;
        }
        return "getDamageSummary";
    }

    private String validDimension(String dimension) {
        if ("damage_type".equals(dimension)) {
            return dimension;
        }
        return "part";
    }

    private int clampDays(int days) {
        if (days <= 0) {
            return 30;
        }
        return Math.min(days, 3650);
    }

    private String truncate(String value, int maxLen) {
        if (value == null || value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen);
    }

    private static class SkillPlan {
        private final String skillId;
        private final int timeRangeDays;
        private final String dimension;

        private SkillPlan(String skillId, int timeRangeDays, String dimension) {
            this.skillId = skillId;
            this.timeRangeDays = timeRangeDays;
            this.dimension = dimension;
        }
    }
}
