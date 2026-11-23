package cn.guanxiang.service;

import cn.guanxiang.mapper.RunLogMapper;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class RunLogService {
    private final RunLogMapper mapper;

    public RunLogService(RunLogMapper mapper) { this.mapper = mapper; }

    public void log(String id, String operation, String status, long start, Integer tokens) {
        mapper.insert(id, operation, status, System.currentTimeMillis() - start, tokens);
    }
    public List<Map<String, Object>> list() { return mapper.findRecent(); }
}
