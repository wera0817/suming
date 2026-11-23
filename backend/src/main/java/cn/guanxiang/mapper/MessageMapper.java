package cn.guanxiang.mapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class MessageMapper {
    private final JdbcTemplate db;

    public MessageMapper(JdbcTemplate db) { this.db = db; }

    public List<Map<String, Object>> findByReportId(String reportId) {
        return db.queryForList("SELECT role,content FROM messages WHERE report_id=? ORDER BY id", reportId);
    }
    public void insert(String reportId, String role, String content) {
        db.update("INSERT INTO messages(report_id,role,content) VALUES (?,?,?)", reportId, role, content);
    }
}
