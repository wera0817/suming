package cn.guanxiang.mapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class ReportMapper {
    private final JdbcTemplate db;

    public ReportMapper(JdbcTemplate db) { this.db = db; }

    public List<Map<String, Object>> findRecent(String owner) {
        return db.queryForList("SELECT id,kind,question,status,error_code,created_at FROM reports WHERE owner=? ORDER BY created_at DESC,id LIMIT 100", owner);
    }
    public Optional<Map<String, Object>> findByIdAndOwner(String id, String owner) {
        return db.queryForList("SELECT id,kind,question,background,chart,observation,sources,status,error_code,created_at FROM reports WHERE id=? AND owner=?", id, owner).stream().findFirst();
    }
    public void insert(String id, String owner, String kind, String question, String background, String chart, String observation, String sources) {
        db.update("INSERT INTO reports(id,owner,kind,question,background,chart,observation,sources,status) VALUES (?,?,?,?,?,?,?,?,?)",
            id, owner, kind, question, background, chart, observation, sources, "draft");
    }
    public int deleteUnlessRunning(String id, String owner) {
        return db.update("DELETE FROM reports WHERE id=? AND owner=? AND status<>'running'", id, owner);
    }
    public int markRunning(String id, String owner) {
        return db.update("UPDATE reports SET status='running',error_code=NULL WHERE id=? AND owner=? AND status<>'running'", id, owner);
    }
    public void updateStatus(String id, String status, String errorCode) {
        db.update("UPDATE reports SET status=?,error_code=? WHERE id=?", status, errorCode, id);
    }
    public void interruptRunning() {
        db.update("UPDATE reports SET status='interrupted',error_code='SERVER_RESTARTED' WHERE status='running'");
    }
}
