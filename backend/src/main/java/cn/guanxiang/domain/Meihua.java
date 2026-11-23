package cn.guanxiang.domain;

import cn.guanxiang.common.Problem;

import java.util.*;

public final class Meihua {
    private Meihua() {}
    public static final String VERSION = "meihua-three-numbers-v1";
    public record Trigram(int number, String name, String nature, String element, List<Integer> lines) {}
    public record Hexagram(int number, String name, List<Integer> lines, Trigram upper, Trigram lower) {}
    public record Chart(String version, List<Long> numbers, int movingLine, Hexagram original,
                        Hexagram mutual, Hexagram changed, Trigram body, Trigram use) {}
    private static final String[] NAMES = {"乾","坤","屯","蒙","需","讼","师","比","小畜","履","泰","否","同人","大有","谦","豫","随","蛊","临","观","噬嗑","贲","剥","复","无妄","大畜","颐","大过","坎","离","咸","恒","遁","大壮","晋","明夷","家人","睽","蹇","解","损","益","夬","姤","萃","升","困","井","革","鼎","震","艮","渐","归妹","丰","旅","巽","兑","涣","节","中孚","小过","既济","未济"};
    // Rows are upper trigrams, columns lower trigrams, in the early-heaven numeric order.
    private static final int[][] ORDER = {
        {1,10,13,25,44,6,33,12},{43,58,49,17,28,47,31,45},
        {14,38,30,21,50,64,56,35},{34,54,55,51,32,40,62,16},
        {9,61,37,42,57,59,53,20},{5,60,63,3,48,29,39,8},
        {26,41,22,27,18,4,52,23},{11,19,36,24,46,7,15,2}
    };
    private static final String[] TRI = {"乾","兑","离","震","巽","坎","艮","坤"};
    private static final String[] NATURE = {"天","泽","火","雷","风","水","山","地"};
    private static final String[] ELEMENT = {"金","金","火","木","木","水","土","土"};
    private static final int[] BITS = {7,3,5,1,6,2,4,0};
    public static Trigram trigram(int number) {
        int i = number - 1;
        return new Trigram(number, TRI[i], NATURE[i], ELEMENT[i], List.of(BITS[i]&1,(BITS[i]>>1)&1,(BITS[i]>>2)&1));
    }
    public static Hexagram hex(List<Integer> lines) {
        int low = lines.get(0) | lines.get(1)<<1 | lines.get(2)<<2;
        int high = lines.get(3) | lines.get(4)<<1 | lines.get(5)<<2;
        int l = index(low), h = index(high), number = ORDER[h][l];
        return new Hexagram(number, NAMES[number-1], List.copyOf(lines), trigram(h+1), trigram(l+1));
    }
    private static int index(int bits) { for(int i=0;i<8;i++) if(BITS[i]==bits) return i; throw new IllegalArgumentException(); }
    public static Chart calculate(List<Long> numbers) {
        if(numbers == null || numbers.size()!=3 || numbers.stream().anyMatch(n-> n==null || n<1 || n>999999999L))
            throw Problem.bad("请输入三个 1 至 999999999 的整数");
        int upper=(int)((numbers.get(0)-1)%8)+1, lower=(int)((numbers.get(1)-1)%8)+1, moving=(int)((numbers.get(2)-1)%6)+1;
        List<Integer> lines = new ArrayList<>(trigram(lower).lines()); lines.addAll(trigram(upper).lines());
        List<Integer> changed = new ArrayList<>(lines); changed.set(moving-1, 1-changed.get(moving-1));
        List<Integer> mutual = List.of(lines.get(1),lines.get(2),lines.get(3),lines.get(2),lines.get(3),lines.get(4));
        return new Chart(VERSION, List.copyOf(numbers), moving, hex(lines), hex(mutual), hex(changed),
            trigram(moving<=3?upper:lower), trigram(moving<=3?lower:upper));
    }
    public static String name(int number) { return NAMES[number-1]; }
}
