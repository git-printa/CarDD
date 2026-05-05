package com.cardamage.api.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

@Repository
public class ChatAuditRepository {

    private final JdbcTemplate jdbcTemplate;

    public ChatAuditRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertAudit(
        String userRole,
        String prompt,
        String chatMode,
        String selectedSkill,
        String parametersJson,
        String responseSummary,
        boolean success,
        String errorMessage
    ) {
        jdbcTemplate.update(
            "INSERT INTO chat_audit_logs (user_role, prompt, chat_mode, selected_skill, parameters_json, response_summary, success, error_message, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            userRole,
            prompt,
            chatMode,
            selectedSkill,
            parametersJson,
            responseSummary,
            success,
            errorMessage,
            Timestamp.from(Instant.now())
        );
    }
}
