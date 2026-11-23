package cn.guanxiang.mapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public class AppStateMapper {
    private final JdbcTemplate db;

    public AppStateMapper(JdbcTemplate db) { this.db = db; }

    public Optional<String> find(String name) {
        return db.query("SELECT value FROM app_state WHERE name=?", (r, i) -> r.getString(1), name).stream().findFirst();
    }
    public void save(String name, String value) {
        db.update("INSERT INTO app_state(name,value) VALUES (?,?) ON DUPLICATE KEY UPDATE value=?", name, value, value);
    }
}
