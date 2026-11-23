package cn.guanxiang.mapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;

@Repository
public class RunLogMapper {
    private final JdbcTemplate db;

    public RunLogMapper(JdbcTemplate db) { this.db = db; }

    public void insert(String id, String operation, String status, long durationMs, Integer tokens) {
        db.update("INSERT INTO run_logs(id,operation,status,duration_ms,total_tokens) VALUES (?,?,?,?,?)", id, operation, status, durationMs, tokens);
    }
    public List<Map<String, Object>> findRecent() {
        return db.queryForList("SELECT * FROM run_logs ORDER BY created_at DESC LIMIT 100");
    }
}
