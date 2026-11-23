package cn.guanxiang;

import cn.guanxiang.common.JsonCodec;
import cn.guanxiang.common.Problem;
import cn.guanxiang.mapper.MessageMapper;
import cn.guanxiang.mapper.ReportMapper;
import cn.guanxiang.service.ReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportServiceTest {
    private final ReportMapper reports = mock(ReportMapper.class);
    private final MessageMapper messages = mock(MessageMapper.class);
    private final ReportService service = new ReportService(reports, messages, new JsonCodec(new ObjectMapper()));

    private void report(String status) {
        when(reports.findByIdAndOwner("id", "owner")).thenReturn(Optional.of(Map.of(
            "id", "id", "status", status, "chart", "{\"version\":\"v1\"}", "sources", "[]")));
        when(messages.findByReportId("id")).thenReturn(List.of());
    }
    @Test void missingOrForeignReportCannotReadMessagesOrDelete() {
        when(reports.findByIdAndOwner("id", "other-owner")).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(Problem.class, () -> service.get("id", "other-owner")).status);
        assertEquals(404, assertThrows(Problem.class, () -> service.delete("id", "other-owner")).status);
        verifyNoInteractions(messages);
        verify(reports, never()).deleteUnlessRunning(anyString(), anyString());
    }
    @Test void getMapsJsonAndLoadsMessagesOnlyAfterOwnershipCheck() {
        report("draft");
        var result = service.get("id", "owner");
        assertEquals(Map.of("version", "v1"), result.get("chart"));
        assertEquals(List.of(), result.get("sources"));
        assertEquals(List.of(), result.get("messages"));
        var order = inOrder(reports, messages);
        order.verify(reports).findByIdAndOwner("id", "owner");
        order.verify(messages).findByReportId("id");
    }
    @Test void runningReportIsNeverDeleted() {
        report("running");
        assertEquals("REPORT_RUNNING", assertThrows(Problem.class, () -> service.delete("id", "owner")).code);
        verify(reports, never()).deleteUnlessRunning(anyString(), anyString());
    }
    @Test void concurrentGenerationPreventsDeletion() {
        report("draft");
        when(reports.deleteUnlessRunning("id", "owner")).thenReturn(0);
        assertEquals(409, assertThrows(Problem.class, () -> service.delete("id", "owner")).status);
    }
    @Test void completedReportCanBeDeleted() {
        report("completed");
        when(reports.deleteUnlessRunning("id", "owner")).thenReturn(1);
        assertDoesNotThrow(() -> service.delete("id", "owner"));
    }
    @Test void followupAndAnswerAreSavedBeforeCompletionStatus() {
        service.complete("id", "followup", "answer");
        var order = inOrder(messages, reports);
        order.verify(messages).insert("id", "user", "followup");
        order.verify(messages).insert("id", "assistant", "answer");
        order.verify(reports).updateStatus("id", "completed", null);
    }
    @Test void firstAnswerDoesNotInsertAnEmptyUserMessage() {
        service.complete("id", null, "answer");
        verify(messages).insert("id", "assistant", "answer");
        verifyNoMoreInteractions(messages);
    }
}
