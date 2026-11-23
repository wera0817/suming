package cn.guanxiang.mapper;

import cn.guanxiang.dto.KnowledgeDoc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public class KnowledgeMapper {
    private final JdbcTemplate db;

    public KnowledgeMapper(JdbcTemplate db) { this.db = db; }

    public List<KnowledgeDoc> findAll() { return db.query("SELECT * FROM knowledge ORDER BY category,id",(r,i)->new KnowledgeDoc(r.getString("id"),r.getString("title"),r.getString("body"),r.getString("category"),r.getString("school"),r.getString("source"),r.getString("edition"),r.getString("rights"),r.getBoolean("verified"),r.getBoolean("enabled"))); }
    public void save(String id, KnowledgeDoc doc) {
        db.update("INSERT INTO knowledge(id,title,body,category,school,source,edition,rights,verified,enabled) VALUES (?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE title=VALUES(title),body=VALUES(body),category=VALUES(category),school=VALUES(school),source=VALUES(source),edition=VALUES(edition),rights=VALUES(rights),verified=VALUES(verified),enabled=VALUES(enabled)",
            id,doc.title(),doc.body(),doc.category(),doc.school(),doc.source(),doc.edition(),doc.rights(),doc.verified(),doc.enabled());
    }
}
