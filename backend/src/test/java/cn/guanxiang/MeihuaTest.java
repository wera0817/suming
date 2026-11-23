package cn.guanxiang;

import cn.guanxiang.domain.Meihua;
import cn.guanxiang.common.Problem;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

class MeihuaTest {
    @Test void zeroRemaindersAndMovingLineSix() {
        var chart=Meihua.calculate(List.of(8L,8L,6L));
        assertEquals(2,chart.original().number()); assertEquals("坤",chart.original().name());
        assertEquals(List.of(0,0,0,0,0,0),chart.original().lines());
        assertEquals(6,chart.movingLine()); assertEquals(23,chart.changed().number());
        assertEquals("坤",chart.body().name()); assertEquals("坤",chart.use().name());
    }
    @Test void allSixMovingLinesOnPureQian() {
        int[] expected={44,13,10,9,14,43};
        for(int moving=1;moving<=6;moving++) {
            var chart=Meihua.calculate(List.of(1L,1L,(long)moving));
            assertEquals(1,chart.original().number()); assertEquals(expected[moving-1],chart.changed().number());
            assertEquals(1,chart.mutual().number()); assertEquals(moving,chart.movingLine());
        }
    }
    @Test void mixedHexagramMutualAndBodyUse() {
        // Upper Qian / lower Dui = Lu; inner lower Li / upper Xun = Jia Ren.
        var chart=Meihua.calculate(List.of(17L,26L,9L));
        assertEquals(10,chart.original().number()); assertEquals(37,chart.mutual().number()); assertEquals(1,chart.changed().number());
        assertEquals(3,chart.movingLine()); assertEquals("乾",chart.body().name()); assertEquals("兑",chart.use().name());
        var upperMoving=Meihua.calculate(List.of(1L,2L,4L));
        assertEquals("兑",upperMoving.body().name()); assertEquals("乾",upperMoving.use().name());
    }
    @Test void allSixtyFourAreUnique() {
        Set<Integer> ids=new HashSet<>();
        for(long upper=1;upper<=8;upper++) for(long lower=1;lower<=8;lower++) ids.add(Meihua.calculate(List.of(upper,lower,1L)).original().number());
        assertEquals(64,ids.size());
    }
    @Test void rejectsInvalidNumbers() {
        assertThrows(Problem.class,()->Meihua.calculate(List.of(0L,1L,1L)));
        assertThrows(Problem.class,()->Meihua.calculate(List.of(1L,1L)));
        assertThrows(Problem.class,()->Meihua.calculate(List.of(1000000000L,1L,1L)));
        assertThrows(Problem.class,()->Meihua.calculate(null));
    }
}
