package cn.guanxiang.client;

import cn.guanxiang.common.JsonCodec;
import cn.guanxiang.dto.ProviderSettings;
import cn.guanxiang.dto.ProviderConnection;
import cn.guanxiang.service.ProviderService;
import cn.guanxiang.common.Problem;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class AiClient {
    private final ProviderService providers;
    private final JsonCodec json;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final Semaphore slots=new Semaphore(3);
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().factory());
    public AiClient(ProviderService providers,JsonCodec json) { this.providers=providers; this.json=json; }
    public record Result(String text,Integer tokens) {}
    public Result call(String kind,List<Map<String,Object>> messages,boolean stream,Consumer<String> delta) {
        return call(kind,messages,stream,delta,Integer.MAX_VALUE,Integer.MAX_VALUE);
    }
    public Result call(String kind,List<Map<String,Object>> messages,boolean stream,Consumer<String> delta,int maxTokens,int timeoutSeconds) {
        ProviderSettings settings=providers.required();
        int timeout=Math.min(settings.timeoutSeconds(),timeoutSeconds);
        ProviderConnection c=providers.connection(settings,kind);
        if(!slots.tryAcquire()) throw new Problem(429,"SERVICE_BUSY","当前咨询较多，请稍后重试");
        InputStream body=null; ScheduledFuture<?> deadline=null; AtomicBoolean timedOut=new AtomicBoolean(false);
        try {
            Map<String,Object> payload=new LinkedHashMap<>(); payload.put("model",c.model()); payload.put("messages",messages);
            payload.put(settings.tokenParameter(),Math.min(settings.maxTokens(),maxTokens)); payload.put("stream",stream);
            String base=c.baseUrl().replaceAll("/+$","");
            String url=base.endsWith("/chat/completions")?base:base+"/chat/completions";
            HttpRequest request=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(timeout))
                .header("Authorization","Bearer "+c.apiKey()).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.encode(payload),StandardCharsets.UTF_8)).build();
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(timeout);
            HttpResponse<InputStream> response=client.send(request,HttpResponse.BodyHandlers.ofInputStream());
            body=response.body();
            if(response.statusCode()!=200) throw upstream(response.statusCode());
            InputStream toClose=body;
            deadline=timer.schedule(()->{timedOut.set(true);try{toClose.close();}catch(IOException ignored){}},Math.max(1,end-System.nanoTime()),TimeUnit.NANOSECONDS);
            StringBuilder output=new StringBuilder(); Integer tokens=null;
            if(!stream) {
                byte[] bytes=body.readNBytes(262145); if(bytes.length>262144) throw upstream(502);
                JsonNode data=json.mapper().readTree(bytes);
                String content=data.path("choices").path(0).path("message").path("content").asText("");
                if(content.isBlank()) throw new Problem(502,"EMPTY_RESPONSE","模型未返回可用内容，请检查模型配置");
                return new Result(content,data.path("usage").has("total_tokens")?data.path("usage").path("total_tokens").asInt():null);
            }
            BufferedReader reader=new BufferedReader(new InputStreamReader(body,StandardCharsets.UTF_8));
            boolean finished=false; String line;
            while((line=readBoundedLine(reader))!=null) {
                if(!line.startsWith("data:")) continue;
                String data=line.substring(5).trim(); if(data.isEmpty()) continue;
                if(data.equals("[DONE]")) { finished=true; break; }
                JsonNode chunk;
                try { chunk=json.mapper().readTree(data); } catch(Exception e) { throw new Problem(502,"INVALID_STREAM","服务返回了无法解析的流式数据"); }
                if(chunk.has("error")) throw upstream(502);
                if(chunk.path("usage").has("total_tokens")) tokens=chunk.path("usage").path("total_tokens").asInt();
                JsonNode choice=chunk.path("choices").path(0);
                String part=choice.path("delta").path("content").asText("");
                if(!part.isEmpty()) { output.append(part); if(output.length()>60000) throw new Problem(502,"OUTPUT_TOO_LONG","模型输出过长，请降低输出长度"); delta.accept(part); }
                String reason=choice.path("finish_reason").asText("");
                if(reason.equals("length")) throw new Problem(502,"OUTPUT_TRUNCATED","模型达到输出上限，报告未完成，请提高输出长度后重试");
                if(reason.equals("stop")) finished=true;
                else if(!reason.isEmpty()&&!reason.equals("null")) throw new Problem(502,"INCOMPLETE_RESPONSE","模型未完整完成解读");
            }
            if(timedOut.get()) throw new Problem(504,"UPSTREAM_TIMEOUT","AI 服务响应超时，请稍后手动重试");
            if(!finished||output.isEmpty()) throw new Problem(502,"INTERRUPTED_STREAM","连接中断，未得到完整报告，可手动重试");
            return new Result(output.toString(),tokens);
        } catch(Problem e) { throw e; }
        catch(HttpTimeoutException e) { throw new Problem(504,"UPSTREAM_TIMEOUT","AI 服务响应超时，请稍后手动重试"); }
        catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new Problem(502,"UPSTREAM_INTERRUPTED","AI 调用已中断"); }
        catch(Exception e) {
            if(timedOut.get()) throw new Problem(504,"UPSTREAM_TIMEOUT","AI 服务响应超时，请稍后手动重试");
            throw new Problem(502,"UPSTREAM_CONNECTION","无法完成 AI 请求，请检查地址、网络及接口兼容性");
        }
        finally { if(deadline!=null) deadline.cancel(false); if(body!=null) try{body.close();}catch(IOException ignored){} slots.release(); }
    }
    private static String readBoundedLine(Reader reader)throws IOException {
        StringBuilder line=new StringBuilder(); int c;
        while((c=reader.read())!=-1) { if(c=='\n') break; if(c!='\r') line.append((char)c); if(line.length()>131072) throw new IOException("Oversized SSE line"); }
        return c==-1&&line.isEmpty()?null:line.toString();
    }
    private Problem upstream(int code) {
        return switch(code) {
            case 401,403 -> new Problem(502,"UPSTREAM_AUTH","API 鉴权失败，请检查密钥和模型权限");
            case 429 -> new Problem(429,"UPSTREAM_RATE_LIMIT","API 额度不足或调用受限，请检查服务商后台");
            case 400,404,422 -> new Problem(502,"UPSTREAM_UNSUPPORTED","接口、模型或参数不受支持；视觉模型还需确认图片输入能力");
            default -> new Problem(502,"UPSTREAM_ERROR","外部 AI 服务返回异常，请稍后手动重试");
        };
    }
}
