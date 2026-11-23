package cn.guanxiang.service;

import cn.guanxiang.client.AiClient;

import cn.guanxiang.common.Problem;

import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class ProviderTestService {
    private final AiClient ai;
    private final PhotoService photos;
    private final RunLogService logs;

    public ProviderTestService(AiClient ai,PhotoService photos,RunLogService logs) {
        this.ai=ai; this.photos=photos; this.logs=logs;
    }
    public Map<String,Object> test(String kind) {
        long start=System.currentTimeMillis(); String id=UUID.randomUUID().toString();
        try {
            List<Map<String,Object>> messages;
            if(kind.equals("vision")) {
                String url="data:image/png;base64,"+Base64.getEncoder().encodeToString(photos.testImage());
                messages=List.of(Map.of("role","user","content",List.of(Map.of("type","text","text","请回答图片中央图形的颜色和形状，只返回中文短语。"),Map.of("type","image_url","image_url",Map.of("url",url)))));
            } else messages=List.of(Map.of("role","user","content","连接测试，请仅回复：连接成功"));
            AiClient.Result result=ai.call(kind,messages,false,s->{});
            if(kind.equals("vision")&&!(result.text().contains("红")&& (result.text().contains("方")||result.text().contains("矩形"))))
                throw new Problem(502,"VISION_TEST_FAILED","接口有响应，但未识别测试图中的红色方形，请确认视觉能力");
            logs.log(id,"test-"+kind,"OK",start,result.tokens());
            return Map.of("ok",true,"message",kind.equals("vision")?"视觉连接测试通过":"文本连接测试通过","durationMs",System.currentTimeMillis()-start);
        } catch(Problem p) { logs.log(id,"test-"+kind,p.code,start,null);throw p; }
    }
}
