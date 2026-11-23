package cn.guanxiang;

import cn.guanxiang.mapper.AppStateMapper;
import cn.guanxiang.service.ProviderService;
import cn.guanxiang.service.ConsultationService;
import cn.guanxiang.service.PhotoService;
import cn.guanxiang.security.Crypto;
import cn.guanxiang.common.Problem;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BoundaryTest {
    @Test void missingApiNeverReturnsASimulatedReport() {
        AppStateMapper state=mock(AppStateMapper.class); when(state.find("providers")).thenReturn(Optional.empty());
        Problem error=assertThrows(Problem.class,()->new ProviderService(state,null,null).required());
        assertEquals(503,error.status); assertEquals("API_NOT_CONFIGURED",error.code);
    }
    @Test void citationsAreRestrictedToRetrievedDocuments() {
        Set<String> allowed=Set.of("gua-1","rule-v1");
        assertTrue(ConsultationService.validCitations("原文 [K:gua-1]，规则 [K:rule-v1]",allowed));
        assertFalse(ConsultationService.validCitations("伪造 [K:made-up]",allowed));
        assertFalse(ConsultationService.validCitations("错误编号 [K:gua 1]",allowed));
        assertFalse(ConsultationService.validCitations("空编号 [K:]",allowed));
        assertFalse(ConsultationService.validCitations("未闭合 [K:gua-1",allowed));
        assertEquals("伪造 [引用未通过核验]",ConsultationService.filterCitations("伪造 [K:made-up]",allowed));
    }
    @Test void encryptionIsRandomizedAndAuthenticated() {
        byte[] bytes=new byte[32]; new java.security.SecureRandom().nextBytes(bytes);
        Crypto crypto=new Crypto(Base64.getEncoder().encodeToString(bytes));
        String first=crypto.encrypt("test-key"),second=crypto.encrypt("test-key");
        assertNotEquals(first,second); assertEquals("test-key",crypto.decrypt(first));
        assertThrows(Problem.class,()->crypto.decrypt(first.substring(0,first.length()-4)+"AAAA"));
        assertNotEquals(Crypto.hash("visitor-a"),Crypto.hash("visitor-b"));
    }
    @Test void imageValidationUsesActualBytesNotFileNames() throws Exception {
        PhotoService photos=new PhotoService(null,null);
        assertThrows(Problem.class,()->photos.normalize(new MockMultipartFile("image","fake.jpg","image/jpeg","not an image".getBytes())));
        BufferedImage image=new BufferedImage(2400,1200,BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out=new ByteArrayOutputStream(); ImageIO.write(image,"png",out);
        byte[] normalized=photos.normalize(new MockMultipartFile("image","anything.txt","text/plain",out.toByteArray()));
        BufferedImage result=ImageIO.read(new ByteArrayInputStream(normalized));
        assertEquals(1600,result.getWidth()); assertEquals(800,result.getHeight());
        assertEquals((byte)0xff,normalized[0]); assertEquals((byte)0xd8,normalized[1]);
        assertThrows(Problem.class,()->photos.normalize(new MockMultipartFile("image",new byte[10*1024*1024+1])));
    }
    @Test void rejectsTinyImages() throws Exception {
        BufferedImage tiny=new BufferedImage(50,50,BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out=new ByteArrayOutputStream(); ImageIO.write(tiny,"png",out);
        assertThrows(Problem.class,()->new PhotoService(null,null).normalize(new MockMultipartFile("image",out.toByteArray())));
    }
}
