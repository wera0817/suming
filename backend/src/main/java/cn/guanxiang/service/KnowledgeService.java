package cn.guanxiang.service;

import cn.guanxiang.mapper.AppStateMapper;
import cn.guanxiang.mapper.ReportMapper;
import cn.guanxiang.dto.KnowledgeDoc;
import cn.guanxiang.mapper.KnowledgeMapper;
import cn.guanxiang.domain.Meihua;
import cn.guanxiang.common.Problem;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class KnowledgeService implements ApplicationRunner {
    private final KnowledgeMapper mapper;
    private final AppStateMapper state;
    private final ReportMapper reports;
    public KnowledgeService(KnowledgeMapper mapper,AppStateMapper state,ReportMapper reports) { this.mapper=mapper; this.state=state; this.reports=reports; }
    public List<KnowledgeDoc> all() { return mapper.findAll(); }
    public void save(KnowledgeDoc doc) {
        if(doc.enabled()&&!doc.verified()) throw Problem.bad("资料需核对来源后才能启用");
        String id=doc.id(); if(id==null||id.isBlank()) id=UUID.randomUUID().toString();
        if(!id.matches("[a-zA-Z0-9_-]{1,64}")) throw Problem.bad("条目编号无效");
        mapper.save(id,doc);
    }
    public List<KnowledgeDoc> search(String kind,String query,Meihua.Chart chart) {
        Set<String> exact=new LinkedHashSet<>();
        if(chart!=null) { for(Meihua.Hexagram hex:List.of(chart.original(),chart.mutual(),chart.changed())) exact.add("gua-"+hex.number()); exact.add("rule-v1"); exact.add("terms-v1"); }
        Set<String> terms=new HashSet<>();
        String text=query.replaceAll("\\s+",""); for(int i=0;i<text.length()-1;i++) terms.add(text.substring(i,i+2));
        return all().stream().filter(d->d.enabled()&&d.verified()&&d.category().equals(kind))
            .map(d->Map.entry(d,score(d,terms,exact)))
            .filter(e->e.getValue()>0).sorted(Comparator.<Map.Entry<KnowledgeDoc,Integer>>comparingInt(Map.Entry::getValue).reversed().thenComparing(e->e.getKey().id()))
            .limit(6).map(Map.Entry::getKey).toList();
    }
    private int score(KnowledgeDoc doc,Set<String> terms,Set<String> exact) {
        if(exact.contains(doc.id())) return 100000;
        int score=0; for(String term:terms) { if(doc.title().contains(term)) score+=8; if(doc.body().contains(term)) score++; }
        if(doc.id().equals("palm-guide")||doc.id().equals("face-guide")) score+=1;
        return score;
    }
    @Override public void run(ApplicationArguments args) {
        reports.interruptRunning();
        // Seed only once: disabled or edited entries must survive every restart.
        if(state.find("seed-v1").isPresent()) return;
        String[] texts={
            "元亨利贞。","元亨，利牝马之贞。君子有攸往，先迷后得主，利西南得朋，东北丧朋。安贞吉。",
            "元亨利贞。勿用有攸往，利建侯。","亨。匪我求童蒙，童蒙求我。初筮告，再三渎，渎则不告。利贞。",
            "有孚，光亨，贞吉。利涉大川。","有孚，窒惕，中吉，终凶。利见大人，不利涉大川。",
            "贞，丈人吉，无咎。","吉。原筮，元永贞，无咎。不宁方来，后夫凶。",
            "亨。密云不雨，自我西郊。","履虎尾，不咥人，亨。","小往大来，吉亨。","否之匪人，不利君子贞，大往小来。",
            "同人于野，亨。利涉大川，利君子贞。","元亨。","亨，君子有终。","利建侯行师。",
            "元亨利贞，无咎。","元亨，利涉大川。先甲三日，后甲三日。","元亨利贞。至于八月有凶。","盥而不荐，有孚颙若。",
            "亨，利用狱。","亨，小利有攸往。","不利有攸往。","亨。出入无疾，朋来无咎。反复其道，七日来复。利有攸往。",
            "元亨利贞。其匪正有眚，不利有攸往。","利贞，不家食吉，利涉大川。","贞吉。观颐，自求口实。","栋桡，利有攸往，亨。",
            "习坎，有孚，维心亨，行有尚。","利贞，亨。畜牝牛，吉。","亨，利贞，取女吉。","亨，无咎，利贞，利有攸往。",
            "亨，小利贞。","利贞。","康侯用锡马蕃庶，昼日三接。","利艰贞。","利女贞。","小事吉。",
            "利西南，不利东北。利见大人，贞吉。","利西南。无所往，其来复吉。有攸往，夙吉。",
            "有孚，元吉，无咎，可贞，利有攸往。曷之用，二簋可用享。","利有攸往，利涉大川。",
            "扬于王庭，孚号，有厉。告自邑，不利即戎，利有攸往。","女壮，勿用取女。",
            "亨。王假有庙，利见大人，亨，利贞。用大牲吉，利有攸往。","元亨，用见大人，勿恤，南征吉。",
            "亨，贞，大人吉，无咎。有言不信。","改邑不改井，无丧无得，往来井井。汔至亦未繘井，羸其瓶，凶。",
            "己日乃孚。元亨利贞，悔亡。","元吉，亨。","亨。震来虩虩，笑言哑哑。震惊百里，不丧匕鬯。",
            "艮其背，不获其身。行其庭，不见其人。无咎。","女归吉，利贞。","征凶，无攸利。",
            "亨，王假之。勿忧，宜日中。","小亨，旅贞吉。","小亨，利有攸往，利见大人。","亨，利贞。",
            "亨。王假有庙，利涉大川，利贞。","亨。苦节不可贞。","豚鱼吉，利涉大川，利贞。",
            "亨，利贞。可小事，不可大事。飞鸟遗之音，不宜上，宜下，大吉。","亨小，利贞。初吉终乱。","亨。小狐汔济，濡其尾，无攸利。"
        };
        for(int i=0;i<64;i++) save(new KnowledgeDoc("gua-"+(i+1),"《周易》·"+Meihua.name(i+1)+"卦",texts[i],"meihua","周易经文",
            "《周易》经文，第"+(i+1)+"卦；核对入口：https://zh.wikisource.org/wiki/周易", "通行经文，简体标点整理；不含现代注释", "古籍原文公版；本站录入，异体字与标点可能有版本差异",true,true));
        save(new KnowledgeDoc("rule-v1","三数起卦规则 v1","先天八卦数：乾一、兑二、离三、震四、巽五、坎六、艮七、坤八。本站固定采用：第一数除八取上卦，第二数除八取下卦，余零作八；第三数除六取动爻，余零作六。爻从下往上编号。互卦取二三四爻为下卦、三四五爻为上卦。动爻阴阳翻转得变卦。动爻所在经卦为用，另一经卦为体。这是本站明确选定的三数规则，不宣称覆盖所有流派。","meihua","本站三数规则","本站规则说明；参考传统先天八卦数和互卦术语，不作古籍逐字引文","meihua-three-numbers-v1","本站原创整理，可随本项目使用",true,true));
        save(new KnowledgeDoc("terms-v1","本卦、互卦、变卦与体用","本卦是起卦所得六爻结构；互卦重组本卦中间爻；变卦是动爻翻转后的结构。体用是传统解释框架，不是现实因果证明。不得将传统吉凶词直接等同于现实必然结果。","meihua","本站术语说明","本站原创教学整理，非古籍引文","初级版 v1","本站原创，可随本项目使用",true,true));
        save(new KnowledgeDoc("palm-guide","掌纹图解与观察边界","手相文化常用生命线、智慧线、感情线等名称。这些是传统俗称，不是寿命、智力、情感或健康的测量指标。只描述照片中可辨认的掌纹位置、走向与清晰度；不要凭图补出看不见的纹路，也不从纹路推断用户性格或命运。古籍手相专论尚未完成版本核对时，不可编造古籍引文。","palm","术语与文化背景","本站原创文化说明，非古籍引文","初级版 v1","本站原创，可随本项目使用",true,true));
        save(new KnowledgeDoc("face-guide","面相术语与观察边界","三庭、五官等是传统面相文化中常见的部位划分术语，不能据此判断一个人的能力、人格、诚信、健康、财富或未来。只对照片中可见的部位作中性位置描述，并介绍术语背景。不识别身份，不作美丑评分，不从相貌推断个人特质。专门相书尚未完成版本核对，不提供伪造的经典出处。","face","术语与文化背景","本站原创文化说明，非古籍引文","初级版 v1","本站原创，可随本项目使用",true,true));
        state.save("seed-v1","1");
    }
}
