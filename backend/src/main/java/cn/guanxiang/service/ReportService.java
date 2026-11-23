package cn.guanxiang.service;

import cn.guanxiang.common.JsonCodec;
import cn.guanxiang.common.Problem;
import cn.guanxiang.mapper.MessageMapper;
import cn.guanxiang.mapper.ReportMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class ReportService {
    private final ReportMapper reports;
    private final MessageMapper messages;
    private final JsonCodec json;

    public ReportService(ReportMapper reports, MessageMapper messages, JsonCodec json) {
        this.reports = reports;
        this.messages = messages;
        this.json = json;
    }
    public List<Map<String, Object>> list(String owner) { return reports.findRecent(owner); }
    public Map<String, Object> get(String id, String owner) {
        Map<String, Object> row = new LinkedHashMap<>(reports.findByIdAndOwner(id, owner).orElseThrow(Problem::missing));
        for (String field : List.of("chart", "sources")) {
            if (row.get(field) != null) row.put(field, json.decode(row.get(field).toString()));
        }
        row.put("messages", messages.findByReportId(id));
        return row;
    }
    public void delete(String id, String owner) {
        var report = get(id, owner);
        if ("running".equals(report.get("status")) || reports.deleteUnlessRunning(id, owner) == 0)
            throw new Problem(409, "REPORT_RUNNING", "请等待生成结束后再删除");
    }
    @Transactional
    public void complete(String id, String followup, String content) {
        if (followup != null && !followup.isBlank()) messages.insert(id, "user", followup);
        messages.insert(id, "assistant", content);
        reports.updateStatus(id, "completed", null);
    }
}
