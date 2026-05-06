package tn.esprit.training;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tn.esprit.training.dto.*;
import tn.esprit.training.entity.Avis;
import tn.esprit.training.entity.TrainingContent;
import tn.esprit.training.repository.AvisRepository;
import tn.esprit.training.repository.TrainingContentRepository;
import tn.esprit.training.service.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/trainings")
public class TrainingsRESTApi {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static volatile LocalDateTime lastAdminVisitAt = LocalDateTime.now();
    private static final Map<Long, List<QuizQuestionDTO>> QUIZ_CACHE = new ConcurrentHashMap<>();

    private final TrainingService trainingService;
    private final AvisRepository avisRepository;
    private final TrainingContentRepository trainingContentRepository;
    private final AnthropicClientService anthropicClientService;
    private final AnthropicSentimentService anthropicSentimentService;
    private final AnthropicCourseSummaryService anthropicCourseSummaryService;
    private final GroqCourseSummaryService groqCourseSummaryService;
    private final AnthropicCourseQuizService anthropicCourseQuizService;
    private final GroqCourseQuizService groqCourseQuizService;
    private final AnthropicPriceOptimizationService anthropicPriceOptimizationService;
    private final AnthropicDiscoveryService anthropicDiscoveryService;
    private final GroqVisionProctorService groqVisionProctorService;
    private final ProctorSessionService proctorSessionService;
    private final TrainingSearchService trainingSearchService;

    @Value("${app.upload.contents-dir:uploads/contents}")
    private String contentsDir;

    public TrainingsRESTApi(
            TrainingService trainingService,
            AvisRepository avisRepository,
            TrainingContentRepository trainingContentRepository,
            AnthropicClientService anthropicClientService,
            AnthropicSentimentService anthropicSentimentService,
            AnthropicCourseSummaryService anthropicCourseSummaryService,
            GroqCourseSummaryService groqCourseSummaryService,
            AnthropicCourseQuizService anthropicCourseQuizService,
            GroqCourseQuizService groqCourseQuizService,
            AnthropicPriceOptimizationService anthropicPriceOptimizationService,
            AnthropicDiscoveryService anthropicDiscoveryService,
            GroqVisionProctorService groqVisionProctorService,
            ProctorSessionService proctorSessionService,
            TrainingSearchService trainingSearchService
    ) {
        this.trainingService = trainingService;
        this.avisRepository = avisRepository;
        this.trainingContentRepository = trainingContentRepository;
        this.anthropicClientService = anthropicClientService;
        this.anthropicSentimentService = anthropicSentimentService;
        this.anthropicCourseSummaryService = anthropicCourseSummaryService;
        this.groqCourseSummaryService = groqCourseSummaryService;
        this.anthropicCourseQuizService = anthropicCourseQuizService;
        this.groqCourseQuizService = groqCourseQuizService;
        this.anthropicPriceOptimizationService = anthropicPriceOptimizationService;
        this.anthropicDiscoveryService = anthropicDiscoveryService;
        this.groqVisionProctorService = groqVisionProctorService;
        this.proctorSessionService = proctorSessionService;
        this.trainingSearchService = trainingSearchService;
    }

    @GetMapping("/search")
    public ResponseEntity<List<tn.esprit.training.entity.Training>> search(@RequestParam("q") String q) {
        return ResponseEntity.ok(trainingSearchService.search(q));
    }

    @PostMapping("/{id}/proctor/session")
    public ResponseEntity<ProctorSessionResponseDTO> startProctorSession(@PathVariable Long id) {
        trainingService.getById(id);
        return ResponseEntity.ok(proctorSessionService.startSession(id));
    }

    @PostMapping("/{id}/proctor/analyze")
    public ResponseEntity<ProctorAnalyzeResponseDTO> proctorAnalyze(@PathVariable Long id, @RequestBody ProctorAnalyzeRequestDTO body) {
        if (body == null || body.getSessionToken() == null || body.getSessionToken().isBlank()) {
            return ResponseEntity.badRequest().body(new ProctorAnalyzeResponseDTO(false, "low", "sessionToken is required", "WARN", "OK"));
        }
        if (body.getBase64Image() == null || body.getBase64Image().isBlank()) {
            return ResponseEntity.badRequest().body(new ProctorAnalyzeResponseDTO(false, "low", "base64Image is required", "WARN", "OK"));
        }
        proctorSessionService.validate(body.getSessionToken(), id);
        String phase = body.getPhase() != null ? body.getPhase().trim().toUpperCase() : "FACE_CHECK";
        if ("MONITOR".equals(phase)) {
            proctorSessionService.requireFaceCheckDoneForMonitor(body.getSessionToken());
        }
        if (!proctorSessionService.tryConsumeAnalyzeSlot(body.getSessionToken())) {
            return ResponseEntity.ok(new ProctorAnalyzeResponseDTO(false, "low", "Please wait before the next check", "WARN", "OK"));
        }
        ProctorAnalyzeResponseDTO out = groqVisionProctorService.analyzeFrame(body.getBase64Image(), phase);
        if ("FACE_CHECK".equals(phase) && !out.isCheating()) {
            String fq = out.getFrameQuality();
            boolean badFrame = "DARK".equals(fq) || "BLURRY".equals(fq) || "TOO_SMALL".equals(fq);
            if (!badFrame
                    && "ALLOW".equals(out.getAction())
                    && Boolean.TRUE.equals(out.getFaceVisible())
                    && ("OK".equals(fq) || fq == null)) {
                proctorSessionService.markFaceCheckOk(body.getSessionToken());
            }
        }
        return ResponseEntity.ok(out);
    }

    @PostMapping("/generate-description")
    public ResponseEntity<GeneratedDescriptionDTO> generateDescription(@RequestBody Map<String, String> body) {
        String title = body.getOrDefault("title", "").trim();
        String category = body.getOrDefault("category", "").trim();
        if (title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        String prompt = "Write a compelling 2-3 sentence marketing description for an online training course. "
                + "Title: " + title + ". Category: " + category + ".";
        String text = anthropicClientService.callClaude(prompt);
        return ResponseEntity.ok(new GeneratedDescriptionDTO(text));
    }

    @GetMapping("/price-optimization")
    public ResponseEntity<List<PriceOptimizationItemDTO>> priceOptimization() {
        return ResponseEntity.ok(anthropicPriceOptimizationService.optimizePrices());
    }

    @GetMapping("/{id}/sentiment")
    public ResponseEntity<SentimentResponseDTO> getReviewSentiment(@PathVariable Long id) {
        trainingService.getById(id);
        List<Avis> reviews = avisRepository.findByTraining_IdOrderByCreatedAtDesc(id);
        if (reviews.size() < 2) {
            throw new IllegalArgumentException("At least 2 reviews are required for sentiment analysis");
        }
        return ResponseEntity.ok(anthropicSentimentService.analyzeReviewsWithFallback(reviews));
    }

    @GetMapping("/{id}/summarize")
    public ResponseEntity<SummaryResponseDTO> summarize(@PathVariable Long id) {
        var course = trainingService.getById(id);
        var contents = trainingService.getContents(id);

        if (course.getDescription() == null || course.getDescription().isBlank()) {
            throw new IllegalArgumentException("Course must have a description");
        }
        if (contents == null || contents.isEmpty()) {
            throw new IllegalArgumentException("Course must have at least 1 lesson");
        }

        List<String> lessonTitles = contents.stream()
                .map(c -> c.getTitle() == null ? "" : c.getTitle().trim())
                .filter(s -> !s.isBlank())
                .toList();

        if (lessonTitles.isEmpty()) {
            throw new IllegalArgumentException("Course must have at least 1 lesson");
        }

        String summary;
        try {
            // Prefer Groq if configured (your setup), fallback to Anthropic if needed.
            summary = groqCourseSummaryService.summarize(course.getTitle(), course.getDescription(), lessonTitles);
        } catch (IllegalStateException groqNotConfiguredOrFailed) {
            summary = anthropicCourseSummaryService.summarize(course.getTitle(), course.getDescription(), lessonTitles);
        }
        return ResponseEntity.ok(new SummaryResponseDTO(summary));
    }

    @GetMapping("/{id}/quiz")
    public ResponseEntity<QuizResponseDTO> quiz(@PathVariable Long id) {
        QuizResponseDTO dto = buildQuizForCourse(id);
        QUIZ_CACHE.put(id, new ArrayList<>(dto.getQuestions()));
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/{id}/generate-quiz-content")
    public ResponseEntity<QuizResponseDTO> generateQuizContent(@PathVariable Long id) {
        QuizResponseDTO dto = buildQuizForCourse(id);
        QUIZ_CACHE.put(id, new ArrayList<>(dto.getQuestions()));
        return ResponseEntity.ok(dto);
    }

    /**
     * A self-contained HTML quiz page that students can answer.
     * Pass rule: score > 50% (strictly greater).
     */
    @GetMapping(value = "/{id}/quiz-page", produces = "text/html; charset=utf-8")
    public ResponseEntity<String> quizPage(@PathVariable Long id) {
        List<QuizQuestionDTO> questions = QUIZ_CACHE.get(id);
        if (questions == null) {
            QuizResponseDTO built = buildQuizForCourse(id);
            questions = new ArrayList<>(built.getQuestions());
            QUIZ_CACHE.put(id, questions);
        }
        String title = "AI Quiz";

        String questionsJson;
        try {
            questionsJson = MAPPER.writeValueAsString(questions);
        } catch (Exception e) {
            questionsJson = "[]";
        }

        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html><head><meta charset='utf-8' />")
                .append("<meta name='viewport' content='width=device-width, initial-scale=1' />")
                .append("<title>").append(escapeHtml(title)).append("</title>")
                .append("<style>")
                .append("body{font-family:system-ui,-apple-system,Segoe UI,Roboto,Arial,sans-serif;background:#f0f4ff;margin:0;padding:18px;color:#0f172a}")
                .append(".card{max-width:900px;margin:0 auto;background:#fff;border:1px solid #e2e8f0;border-radius:16px;box-shadow:0 2px 12px rgba(0,0,0,.06);overflow:hidden}")
                .append(".head{padding:14px 16px;border-bottom:1px solid #eef2ff;background:linear-gradient(180deg,#f8fafc,#fff)}")
                .append(".head h1{margin:0;font-size:18px;font-weight:900}")
                .append(".q{padding:14px 16px;border-bottom:1px solid #f1f5f9}")
                .append(".q:last-child{border-bottom:none}")
                .append(".qt{font-weight:900;margin-bottom:10px}")
                .append(".opt{display:flex;gap:10px;align-items:flex-start;padding:10px 10px;border:1px solid #e5e7eb;border-radius:12px;margin:8px 0;background:#f8fafc;cursor:pointer}")
                .append(".opt input{margin-top:3px}")
                .append(".actions{padding:12px 16px;border-top:1px solid #eef2ff;display:flex;gap:10px;align-items:center;justify-content:space-between;flex-wrap:wrap}")
                .append(".btn{border:none;border-radius:12px;padding:10px 14px;font-weight:900;cursor:pointer}")
                .append(".btn-primary{background:#4f46e5;color:#fff}")
                .append(".btn-ghost{background:#fff;border:1px solid #e2e8f0;color:#0f172a}")
                .append(".result{font-size:13px;font-weight:900}")
                .append(".ok{color:#065f46}")
                .append(".bad{color:#b91c1c}")
                .append(".foot{padding:12px 16px;color:#64748b;font-size:12px}")
                .append(".setup{padding:16px}")
                .append(".setup-grid{display:grid;grid-template-columns:1fr;gap:12px}")
                .append("@media(min-width:860px){.setup-grid{grid-template-columns:360px 1fr;align-items:start}}")
                .append(".videoBox{background:#0b1220;border-radius:14px;overflow:hidden;border:1px solid #0f172a}")
                .append(".videoBox video{width:100%;height:auto;display:block}")
                .append(".checklist{border:1px solid #e2e8f0;border-radius:14px;padding:12px;background:#fff}")
                .append(".check{display:flex;align-items:center;justify-content:space-between;padding:8px 6px;border-bottom:1px solid #f1f5f9;font-weight:900}")
                .append(".check:last-child{border-bottom:none}")
                .append(".pill{padding:4px 10px;border-radius:999px;font-size:12px;font-weight:900}")
                .append(".pill-ok{background:#ecfdf5;color:#065f46;border:1px solid #a7f3d0}")
                .append(".pill-warn{background:#fffbeb;color:#92400e;border:1px solid #fde68a}")
                .append(".pill-bad{background:#fee2e2;color:#b91c1c;border:1px solid #fecaca}")
                .append("</style></head><body><div class='card'>")
                .append("<div class='head'><h1>").append(escapeHtml(title)).append("</h1></div>")
                .append("<div id='sessionErr' class='bad' style='display:none;padding:12px 16px;font-weight:800;'></div>")
                .append("<div class='setup' id='setup'>")
                .append("<div class='setup-grid'>")
                .append("<div class='videoBox'><video id='cam' autoplay muted playsinline></video></div>")
                .append("<div>")
                .append("<div class='checklist'>")
                .append("<div class='check'><span>Camera</span><span class='pill pill-bad' id='camPill'>Off</span></div>")
                .append("<div class='check'><span>Microphone</span><span class='pill pill-bad' id='micPill'>Off</span></div>")
                .append("<div class='check'><span>Face OK</span><span class='pill pill-warn' id='facePill'>Pending</span></div>")
                .append("</div>")
                .append("<div style='margin-top:12px;display:flex;gap:10px;flex-wrap:wrap;'>")
                .append("<button class='btn btn-ghost' id='allowBtn' type='button'>Allow camera / mic</button>")
                .append("<button class='btn btn-primary' id='startBtn' type='button' disabled>Start quiz</button>")
                .append("</div>")
                .append("<div style='margin-top:10px;color:#b91c1c;font-weight:800;font-size:12px;display:none;' id='err'></div>")
                .append("</div></div></div>")
                .append("<div id='quizWrap' style='display:none;'>");

        for (int i = 0; i < questions.size(); i++) {
            QuizQuestionDTO q = questions.get(i);
            html.append("<div class='q'>")
                    .append("<div class='qt'>").append(i + 1).append(". ").append(escapeHtml(q.getQuestion())).append("</div>");
            List<String> opts = q.getOptions() == null ? List.of() : q.getOptions();
            for (int j = 0; j < opts.size(); j++) {
                String opt = opts.get(j);
                String name = "q" + i;
                html.append("<label class='opt'>")
                        .append("<input type='radio' name='").append(escapeHtml(name)).append("' />")
                        .append("<div>").append(escapeHtml(opt)).append("</div>")
                        .append("</label>");
            }
            html.append("</div>");
        }

        html.append("<div class='actions'>")
                .append("<div id='result' class='result'>Choose your answers then click Submit.</div>")
                .append("<div style='display:flex;gap:10px;align-items:center;'>")
                .append("<button class='btn btn-primary' id='submitBtn' type='button'>Submit</button>")
                .append("<button class='btn btn-ghost' id='retryBtn' type='button' style='display:none;'>Retry</button>")
                .append("</div>")
                .append("</div>")
                .append("<div class='foot'>Pass rule: score &gt; 50%. If you fail, you can retry.</div>")
                .append("</div>");

        html.append("<script>")
                .append("const COURSE_ID=").append(id).append(";")
                .append("const QUESTIONS=").append(questionsJson).append(";")
                .append("const PASS=50;")
                .append("const SESSION_URL='/trainings/'+COURSE_ID+'/proctor/session';")
                .append("const ANALYZE_URL='/trainings/'+COURSE_ID+'/proctor/analyze';")
                .append("const cam=document.getElementById('cam');")
                .append("const allowBtn=document.getElementById('allowBtn');")
                .append("const startBtn=document.getElementById('startBtn');")
                .append("const err=document.getElementById('err');")
                .append("const sessionErr=document.getElementById('sessionErr');")
                .append("const camPill=document.getElementById('camPill');")
                .append("const micPill=document.getElementById('micPill');")
                .append("const facePill=document.getElementById('facePill');")
                .append("const setup=document.getElementById('setup');")
                .append("const quizWrap=document.getElementById('quizWrap');")
                .append("const resultEl=document.getElementById('result');")
                .append("const submitBtn=document.getElementById('submitBtn');")
                .append("const retryBtn=document.getElementById('retryBtn');")
                .append("let sessionToken=null,stream=null,monitorId=null,ejected=false;")
                .append("function setPill(el,c,t){el.className='pill '+c;el.textContent=t;}")
                .append("function showErr(m){err.style.display='block';err.textContent=m;}")
                .append("function hideErr(){err.style.display='none';err.textContent='';}")
                .append("function hasFrames(){return cam.videoWidth>0&&cam.videoHeight>0;}")
                .append("function captureB64(){if(!hasFrames())return null;const w=640;const h=Math.max(1,Math.round((cam.videoHeight/cam.videoWidth)*w));")
                .append("const c=document.createElement('canvas');c.width=w;c.height=h;c.getContext('2d').drawImage(cam,0,0,w,h);")
                .append("return c.toDataURL('image/jpeg',0.82).replace(/^data:image\\/jpeg;base64,/, '');}")
                .append("async function callAnalyze(phase){const b64=captureB64();if(!b64)throw new Error('nocam');")
                .append("const res=await fetch(ANALYZE_URL,{method:'POST',headers:{'Content-Type':'application/json'},")
                .append("body:JSON.stringify({sessionToken:sessionToken,base64Image:b64,phase:phase})});")
                .append("if(!res.ok)throw new Error('http'+res.status);return res.json();}")
                .append("function interpretFace(o){")
                .append("if(o.frameQuality&&o.frameQuality!=='OK'&&o.frameQuality!=='SKIPPED')return{ok:false,reason:o.reason||'Bad frame'};")
                .append("if(o.frameQuality==='SKIPPED')return{ok:false,reason:o.reason||'Cannot verify your face (vision unavailable). Try again.'};")
                .append("if(o.action==='EJECT')return{ok:false,reason:o.reason||'Blocked'};")
                .append("if(o.action==='WARN')return{ok:false,reason:o.reason||'Face not verified — stay in frame and try again.'};")
                .append("if(o.faceVisible!==true)return{ok:false,reason:o.reason||'Your face must be clearly visible in the camera.'};")
                .append("if(o.cheating&&(o.confidence==='high'||o.confidence==='medium'))return{ok:false,reason:o.reason||'Check failed'};")
                .append("const r=(o.reason||'').toLowerCase();")
                .append("let fm=r.includes('no face')||r.includes('no person')||r.includes('nobody')||r.includes('missing')||r.includes('left frame')||r.includes('away from')||r.includes('empty frame')||r.includes('empty room')||r.includes('unclear');")
                .append("if((r.includes('not visible')||r.includes('cannot see')||r.includes('cant see'))&&!(r.includes('phone')||r.includes('tablet')))fm=true;")
                .append("if(fm&&!o.cheating)return{ok:false,reason:o.reason||'Face not visible'};")
                .append("return{ok:true};}")
                .append("function stopMonitor(){if(monitorId){clearInterval(monitorId);monitorId=null;}}")
                .append("function ejectQuiz(msg){ejected=true;stopMonitor();quizWrap.style.display='none';setup.style.display='block';")
                .append("showErr(msg||'Quiz ended: proctoring alert.');startBtn.disabled=true;submitBtn.disabled=true;")
                .append("try{window.parent?.postMessage({type:'SKILLIO_QUIZ_EJECTED',courseId:COURSE_ID,reason:msg},'*');}catch(e){}}")
                .append("(async function(){try{const sr=await fetch(SESSION_URL,{method:'POST',headers:{'Content-Type':'application/json'},body:'{}'});")
                .append("if(!sr.ok)throw 0;const sj=await sr.json();sessionToken=sj.sessionToken;}catch(x){")
                .append("sessionErr.style.display='block';sessionErr.textContent='Could not start proctor session. Refresh or check Training API.';return;}")
                .append("allowBtn.addEventListener('click',async()=>{hideErr();try{")
                .append("stream=await navigator.mediaDevices.getUserMedia({video:true,audio:true});cam.srcObject=stream;")
                .append("setPill(camPill,'pill-ok','On');setPill(micPill,'pill-ok','On');setPill(facePill,'pill-warn','Checking…');startBtn.disabled=true;")
                .append("const w=()=>new Promise(r=>setTimeout(r,280));for(let i=0;i<12;i++){if(hasFrames())break;await w();}")
                .append("try{const o=await callAnalyze('FACE_CHECK');const v=interpretFace(o);if(v.ok){setPill(facePill,'pill-ok',v.soft?'OK*':'Yes');startBtn.disabled=false;}else{setPill(facePill,'pill-bad','No');showErr(v.reason);}}")
                .append("catch(e){setPill(facePill,'pill-bad','No');showErr('Face check failed (API).');}")
                .append("}catch(e){showErr('Camera access is required.');}});")
                .append("startBtn.addEventListener('click',async()=>{if(ejected)return;hideErr();startBtn.disabled=true;setPill(facePill,'pill-warn','Checking…');")
                .append("try{const o=await callAnalyze('FACE_CHECK');const v=interpretFace(o);if(!v.ok){setPill(facePill,'pill-bad','No');showErr(v.reason);startBtn.disabled=false;return;}")
                .append("setPill(facePill,'pill-ok','Yes');setup.style.display='none';quizWrap.style.display='block';")
                .append("monitorId=setInterval(async()=>{if(ejected)return;try{const m=await callAnalyze('MONITOR');if(m.action==='EJECT')ejectQuiz(m.reason);}catch(e){}},18000);")
                .append("}catch(e){showErr('Could not verify face.');startBtn.disabled=false;}});")
                .append("function getSelectedIndex(qIdx){const name='q'+qIdx;")
                .append("const radios=[...document.querySelectorAll('input[type=radio][name=\"'+name+'\"]')];return radios.findIndex(r=>r.checked);}")
                .append("function clearAll(){document.querySelectorAll('input[type=radio]').forEach(r=>r.checked=false);")
                .append("resultEl.textContent='Choose your answers then click Submit.';resultEl.className='result';retryBtn.style.display='none';}")
                .append("submitBtn.addEventListener('click',()=>{if(ejected)return;const total=QUESTIONS.length||0;if(!total){resultEl.textContent='No quiz questions.';return;}")
                .append("let answered=0,correct=0;for(let i=0;i<total;i++){const sel=getSelectedIndex(i);if(sel>=0){answered++;if(sel===Number(QUESTIONS[i].correctIndex))correct++;}}")
                .append("if(answered<total){resultEl.textContent='Please answer all questions ('+answered+'/'+total+').';resultEl.className='result bad';return;}")
                .append("const pct=Math.round((correct/total)*100);if(pct>PASS){resultEl.textContent='Score: '+pct+'% — Passed';resultEl.className='result ok';retryBtn.style.display='none';")
                .append("stopMonitor();try{window.parent?.postMessage({type:'SKILLIO_QUIZ_PASSED',courseId:COURSE_ID,scorePercent:pct},'*');}catch(e){}}")
                .append("else{resultEl.textContent='Score: '+pct+'% — Failed (need > '+PASS+'%). Try again.';resultEl.className='result bad';retryBtn.style.display='inline-flex';}});")
                .append("retryBtn.addEventListener('click',()=>clearAll());")
                .append("})();")
                .append("</script>");

        html.append("</div></body></html>");
        return ResponseEntity.ok(html.toString());
    }

    private QuizResponseDTO buildQuizForCourse(Long id) {
        var course = trainingService.getById(id);
        var contents = trainingService.getContents(id);

        if (course.getDescription() == null || course.getDescription().isBlank()) {
            throw new IllegalArgumentException("Course must have a description");
        }
        if (contents == null || contents.isEmpty()) {
            throw new IllegalArgumentException("Course must have at least 1 lesson");
        }

        List<String> lessonTitles = contents.stream()
                .map(c -> c.getTitle() == null ? "" : c.getTitle().trim())
                .filter(s -> !s.isBlank())
                .toList();
        if (lessonTitles.isEmpty()) {
            throw new IllegalArgumentException("Course must have at least 1 lesson");
        }

        var questions = List.<QuizQuestionDTO>of();
        try {
            questions = groqCourseQuizService.generateQuiz(course.getTitle(), course.getDescription(), lessonTitles);
        } catch (Exception groqFailed) {
            questions = anthropicCourseQuizService.generateQuiz(course.getTitle(), course.getDescription(), lessonTitles);
        }
        if (questions == null || questions.isEmpty()) {
            // Safe fallback so UI never stays empty.
            questions = List.of(
                    new QuizQuestionDTO(
                            "What is the main goal of this course?",
                            List.of("Learn the topic basics and workflow", "Only watch videos", "Only read PDFs", "No learning objectives"),
                            0
                    ),
                    new QuizQuestionDTO(
                            "Which concept is most relevant to the lessons listed?",
                            List.of("Course modules and progression", "Cooking recipes", "Astronomy charts", "Music theory"),
                            0
                    )
            );
        }
        // Do not QUIZ_CACHE.put here: quizPage() loads with get → build → put; nested put during computeIfAbsent caused
        // ConcurrentHashMap "Recursive update" (HTTP 503). quiz / generate-quiz-content update the cache after build.
        return new QuizResponseDTO(questions, "OK");
    }

    private static String escapeHtml(String s) {
        String v = s == null ? "" : s;
        return v.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    @PostMapping("/contents/{contentId}/generate-script")
    public ResponseEntity<Map<String, String>> generateLessonScript(@PathVariable Long contentId) {
        TrainingContent content = trainingContentRepository.findByIdWithTraining(contentId)
                .orElseThrow(() -> new IllegalArgumentException("Content not found"));
        var training = content.getTraining();

        String courseTitle = training != null && training.getTitle() != null ? training.getTitle() : "";
        String lessonTitle = content.getTitle() != null ? content.getTitle() : "";
        String url = content.getContentUrl() != null ? content.getContentUrl() : "";

        String prompt = "Write a short instructor script (with bullet talking points) for teaching this lesson in a video. "
                + "Course: " + courseTitle + ". Lesson: " + lessonTitle + ". Resource URL: " + url;
        String script = anthropicClientService.callClaude(prompt, 2500);
        return ResponseEntity.ok(Map.of("script", script));
    }

    @GetMapping
    public ResponseEntity<List<TrainingResponseDTO>> getAll() {
        return ResponseEntity.ok(trainingService.getAll());
    }

    @GetMapping("/avis/recent")
    public ResponseEntity<List<Map<String, Object>>> recentAvis() {
        LocalDateTime since24h = LocalDateTime.now().minusHours(24);
        List<Map<String, Object>> recent = avisRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .filter(a -> a.getCreatedAt() != null && a.getCreatedAt().isAfter(since24h))
                .map(a -> Map.<String, Object>of(
                        "id", a.getId(),
                        "authorName", a.getAuthorName() == null ? "" : a.getAuthorName(),
                        "rating", a.getRating() == null ? 0 : a.getRating(),
                        "comment", a.getComment() == null ? "" : a.getComment(),
                        "trainingTitle", (a.getTraining() != null && a.getTraining().getTitle() != null) ? a.getTraining().getTitle() : "",
                        "createdAt", a.getCreatedAt()
                ))
                .toList();
        return ResponseEntity.ok(recent);
    }

    @GetMapping("/avis/unread-count")
    public ResponseEntity<Map<String, Long>> unreadAvisCount() {
        long count = avisRepository.findAll().stream()
                .filter(a -> a.getCreatedAt() != null && a.getCreatedAt().isAfter(lastAdminVisitAt))
                .count();
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PostMapping("/avis/mark-read")
    public ResponseEntity<Map<String, Object>> markAvisRead() {
        lastAdminVisitAt = LocalDateTime.now();
        return ResponseEntity.ok(Map.of("count", 0, "lastVisit", lastAdminVisitAt.toString()));
    }

    @PostMapping("/learning-path")
    public ResponseEntity<List<LearningPathStepDTO>> generateLearningPath(@RequestBody String body) {
        String goal = extractField(body, "goal");
        if (goal.isBlank()) {
            throw new IllegalArgumentException("goal is required");
        }
        List<TrainingResponseDTO> courses = trainingService.getAll();
        List<LearningPathStepDTO> path = anthropicDiscoveryService.generateLearningPath(goal, courses);
        return ResponseEntity.ok(path);
    }

    @PostMapping("/smart-search")
    public ResponseEntity<List<TrainingResponseDTO>> smartSearch(@RequestBody String body) {
        String query = extractField(body, "query");
        if (query.isBlank()) {
            throw new IllegalArgumentException("query is required");
        }
        List<TrainingResponseDTO> courses = trainingService.getAll();
        List<TrainingResponseDTO> ranked = anthropicDiscoveryService.smartSearch(query, courses);
        return ResponseEntity.ok(ranked);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TrainingResponseDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(trainingService.getById(id));
    }

    @PostMapping
    public ResponseEntity<TrainingResponseDTO> create(@Valid @RequestBody TrainingRequestDTO training) {
        return ResponseEntity.ok(trainingService.create(training));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TrainingResponseDTO> update(@PathVariable Long id, @Valid @RequestBody TrainingRequestDTO training) {
        return ResponseEntity.ok(trainingService.update(id, training));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        trainingService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/contents")
    public ResponseEntity<List<TrainingContentDTO>> getContents(@PathVariable Long id) {
        return ResponseEntity.ok(trainingService.getContents(id));
    }

    @PostMapping("/upload-content")
    public ResponseEntity<UploadContentResponseDTO> uploadContent(@RequestParam("file") MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }

        String original = file.getOriginalFilename() == null ? "content" : file.getOriginalFilename();
        String cleaned = original.replace("\\", "_").replace("/", "_");

        String ext = "";
        int dot = cleaned.lastIndexOf('.');
        if (dot > -1 && dot < cleaned.length() - 1) {
            ext = cleaned.substring(dot);
        }

        String generatedName = UUID.randomUUID().toString().replace("-", "") + ext;

        Path uploadPath = Paths.get(contentsDir).toAbsolutePath().normalize();
        Files.createDirectories(uploadPath);
        Files.copy(file.getInputStream(), uploadPath.resolve(generatedName), StandardCopyOption.REPLACE_EXISTING);

        // Return a URL that works for this microservice + gateway route (/uploads/**).
        // Frontend can use it directly.
        String publicUrl = "/uploads/contents/" + generatedName;
        return ResponseEntity.ok(new UploadContentResponseDTO(publicUrl));
    }

    @PostMapping("/{id}/contents")
    public ResponseEntity<TrainingContentDTO> addContent(@PathVariable Long id, @Valid @RequestBody TrainingContentCreateDTO content) {
        return ResponseEntity.ok(trainingService.addContent(id, content));
    }

    @DeleteMapping("/contents/{contentId}")
    public ResponseEntity<Void> deleteContent(@PathVariable Long contentId) {
        trainingService.deleteContent(contentId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/training-contents/{contentId}")
    public ResponseEntity<Void> deleteContentAlt(@PathVariable Long contentId) {
        trainingService.deleteContent(contentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/avis")
    public ResponseEntity<List<AvisDTO>> getAvis(@PathVariable Long id) {
        return ResponseEntity.ok(trainingService.getAvis(id));
    }

    @PostMapping("/{id}/avis")
    public ResponseEntity<AvisDTO> addAvis(@PathVariable Long id, @Valid @RequestBody AvisCreateDTO avis) {
        return ResponseEntity.ok(trainingService.addAvis(id, avis));
    }

    @DeleteMapping("/avis/{avisId}")
    public ResponseEntity<Void> deleteAvis(@PathVariable Long avisId) {
        trainingService.deleteAvis(avisId);
        return ResponseEntity.noContent().build();
    }

    private static String extractField(String body, String field) {
        String raw = body == null ? "" : body.trim();
        if (raw.isBlank()) return "";

        try {
            JsonNode root = MAPPER.readTree(raw);
            if (root.isObject()) {
                String v = root.path(field).asText("");
                if (!v.isBlank()) return v.trim();
            }
            if (root.isTextual()) {
                String nested = root.asText("");
                if (!nested.isBlank()) {
                    JsonNode nestedRoot = MAPPER.readTree(nested);
                    if (nestedRoot.isObject()) {
                        String v = nestedRoot.path(field).asText("");
                        if (!v.isBlank()) return v.trim();
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return raw.replace("\\\"", "\"").trim();
    }
}

