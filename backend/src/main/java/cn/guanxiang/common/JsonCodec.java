package cn.guanxiang.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class JsonCodec {
    private final ObjectMapper mapper;

    public JsonCodec(ObjectMapper mapper) { this.mapper = mapper; }

    public ObjectMapper mapper() { return mapper; }
    public String encode(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    public <T> T decode(String value, Class<T> type) {
        try { return mapper.readValue(value, type); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    public Object decode(String value) {
        try { return mapper.readValue(value, new TypeReference<Object>() {}); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
