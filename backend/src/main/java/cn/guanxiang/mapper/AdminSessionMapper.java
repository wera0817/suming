package cn.guanxiang.mapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;

@Repository
public class AdminSessionMapper {
    private final JdbcTemplate db;

    public AdminSessionMapper(JdbcTemplate db) { this.db = db; }

    public void deleteExpired() { db.update("DELETE FROM admin_sessions WHERE expires_at<CURRENT_TIMESTAMP"); }
    public boolean existsActive(String tokenHash) {
        return db.queryForObject("SELECT COUNT(*) FROM admin_sessions WHERE token=? AND expires_at>CURRENT_TIMESTAMP", Integer.class, tokenHash) > 0;
    }
    public void insert(String tokenHash, Timestamp expiresAt) {
        db.update("INSERT INTO admin_sessions(token,expires_at) VALUES (?,?)", tokenHash, expiresAt);
    }
    public void delete(String tokenHash) { db.update("DELETE FROM admin_sessions WHERE token=?", tokenHash); }
}
