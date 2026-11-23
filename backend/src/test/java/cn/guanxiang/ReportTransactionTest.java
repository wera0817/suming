package cn.guanxiang;

import cn.guanxiang.common.JsonCodec;
import cn.guanxiang.mapper.MessageMapper;
import cn.guanxiang.mapper.ReportMapper;
import cn.guanxiang.service.ReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportTransactionTest {
    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean ReportMapper reports() { return mock(ReportMapper.class); }
        @Bean MessageMapper messages() { return mock(MessageMapper.class); }
        @Bean JsonCodec json() { return new JsonCodec(new ObjectMapper()); }
        @Bean ReportService service(ReportMapper reports, MessageMapper messages, JsonCodec json) {
            return new ReportService(reports, messages, json);
        }
        @Bean PlatformTransactionManager transactionManager() {
            var manager = mock(PlatformTransactionManager.class);
            when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
            return manager;
        }
    }
    @Test void completionCommitsThroughSpringTransactionProxy() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            context.getBean(ReportService.class).complete("id", "followup", "answer");
            var manager = context.getBean(PlatformTransactionManager.class);
            verify(manager).commit(any());
            verify(manager, never()).rollback(any());
        }
    }
    @Test void messageFailureRollsBackAndNeverMarksReportCompleted() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var messages = context.getBean(MessageMapper.class);
            doThrow(new IllegalStateException("write failed")).when(messages).insert("id", "assistant", "answer");
            assertThrows(IllegalStateException.class, () -> context.getBean(ReportService.class).complete("id", "followup", "answer"));
            var manager = context.getBean(PlatformTransactionManager.class);
            verify(manager).rollback(any());
            verify(manager, never()).commit(any());
            verify(context.getBean(ReportMapper.class), never()).updateStatus(anyString(), anyString(), any());
        }
    }
}
