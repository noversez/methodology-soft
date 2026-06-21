package ru.casebook.dims.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.casebook.dims.api.ApiException;
import ru.casebook.dims.api.dto.ReportDtos.ReportPreviewResponse;
import ru.casebook.dims.config.DimsProperties;
import ru.casebook.dims.domain.*;
import ru.casebook.dims.repo.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.*;
import java.util.List;

@Service
public class ReportService {
    private final CaseRepository cases; private final EvidenceRepository evidence; private final LabRequestRepository labs;
    private final GraphEdgeRepository edges; private final HypothesisRepository hypotheses; private final InterviewRepository interviews;
    private final AttachmentRepository attachments; private final ReportRepository reports; private final CurrentUserService currentUsers;
    private final AuditService audit; private final DimsProperties properties;

    public ReportService(CaseRepository cases, EvidenceRepository evidence, LabRequestRepository labs, GraphEdgeRepository edges,
            HypothesisRepository hypotheses, InterviewRepository interviews, AttachmentRepository attachments, ReportRepository reports,
            CurrentUserService currentUsers, AuditService audit, DimsProperties properties) {
        this.cases=cases; this.evidence=evidence; this.labs=labs; this.edges=edges; this.hypotheses=hypotheses;
        this.interviews=interviews; this.attachments=attachments; this.reports=reports; this.currentUsers=currentUsers; this.audit=audit; this.properties=properties;
    }

    public ReportPreviewResponse preview(UUID caseId) { return preview(caseId, "FULL", "TEXT"); }
    public ReportPreviewResponse preview(UUID caseId, String template) { return preview(caseId, template, "TEXT"); }
    public ReportPreviewResponse preview(UserAccount actor, UUID caseId, String template, String format) {
        currentUsers.requireAnyRole(actor, Role.DETECTIVE, Role.INSPECTOR);
        return preview(caseId, template, format);
    }
    @Transactional(readOnly = true)
    public ReportPreviewResponse preview(UUID caseId, String template, String format) {
        CaseFile item = requireActiveCase(caseId); validateRequired(item); String normalizedFormat=normalizeFormat(format);
        List<LabRequest> open = openLabs(caseId); boolean optimize = shouldOptimizeMedia(caseId);
        String warning = warning(open, optimize);
        return new ReportPreviewResponse(format(renderText(item, template, !open.isEmpty(), List.of(), optimize), normalizedFormat), !open.isEmpty(), warning);
    }

    @Transactional public ReportFile approve(UserAccount actor, UUID caseId, boolean force) { return approve(actor, caseId, force, "FULL", "TEXT"); }
    @Transactional public ReportFile approve(UserAccount actor, UUID caseId, boolean force, String template) { return approve(actor, caseId, force, template, "TEXT"); }
    @Transactional
    public ReportFile approve(UserAccount actor, UUID caseId, boolean force, String template, String format) {
        currentUsers.requireAnyRole(actor, Role.DETECTIVE, Role.INSPECTOR);
        CaseFile caseFile=requireActiveCase(caseId); validateRequired(caseFile); String normalizedFormat=normalizeFormat(format);
        List<LabRequest> open=openLabs(caseId);
        if (!open.isEmpty() && !force) throw new ApiException(HttpStatus.CONFLICT,"REPORT_HAS_OPEN_LAB_REQUESTS",warning(open,false));
        String number="REP-"+caseFile.getOpenedAt().atZone(java.time.ZoneOffset.UTC).getYear()+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase();
        MediaOptimization media=optimizeMedia(caseId,number);
        String content=format(renderText(caseFile,template,!open.isEmpty(),media.files(),media.optimized()),normalizedFormat);
        byte[] bytes=content.getBytes(StandardCharsets.UTF_8); String hash=SecurityHash.sha256(bytes);
        String extension="HTML".equals(normalizedFormat)?".html":".txt";
        Path path=Path.of(properties.getStoragePath(),"reports",number+extension).toAbsolutePath().normalize();
        try { Files.createDirectories(path.getParent()); Files.write(path,bytes,StandardOpenOption.CREATE_NEW); makeReadOnly(path); }
        catch(IOException ex) { throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,"REPORT_STORAGE_ERROR","Не удалось сохранить неизменяемый файл отчета"); }
        ReportFile report=reports.save(new ReportFile(caseFile,number,normalizedFormat,ReportStatus.APPROVED,path.toString(),hash,actor,content));
        audit.record(actor,"REPORT_APPROVED","Report",report.getId(),"{\"hash\":\""+hash+"\",\"mediaOptimized\":"+media.optimized()+"}");
        return report;
    }

    public List<ReportFile> byCase(UUID caseId) { return reports.findByCaseFileId(caseId); }
    public ReportFile get(UUID id) { return reports.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,"REPORT_NOT_FOUND","Отчет не найден")); }

    public ReportDownload download(UserAccount actor, UUID id) {
        currentUsers.requireAnyRole(actor,Role.DETECTIVE,Role.INSPECTOR);
        ReportFile report=get(id);
        try {
            byte[] bytes=Files.readAllBytes(Path.of(report.getStoragePath()));
            if (!SecurityHash.sha256(bytes).equals(report.getSha256())) throw new ApiException(HttpStatus.CONFLICT,"REPORT_INTEGRITY_VIOLATION","Контрольная сумма отчета не совпадает");
            return new ReportDownload(bytes,"HTML".equals(report.getFormat())?"text/html":"text/plain",report.getRegistrationNumber()+("HTML".equals(report.getFormat())?".html":".txt"));
        } catch(IOException ex) { throw new ApiException(HttpStatus.NOT_FOUND,"REPORT_FILE_NOT_FOUND","Файл отчета отсутствует в хранилище"); }
    }

    private CaseFile requireActiveCase(UUID id) { CaseFile c=cases.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,"CASE_NOT_FOUND","Дело не найдено")); if(c.getStatus()==CaseStatus.CLOSED) throw new ApiException(HttpStatus.CONFLICT,"CASE_NOT_ACTIVE","Итоговый отчет формируется только для активного дела"); return c; }
    private void validateRequired(CaseFile c) { if(c.getTitle().isBlank()||c.getDescription().isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST,"REPORT_REQUIRED_FIELDS_EMPTY","Нельзя сформировать отчет: обязательные поля дела не заполнены"); }
    private List<LabRequest> openLabs(UUID caseId) { return labs.findByCaseFileId(caseId).stream().filter(i->i.getStatus()!=LabRequestStatus.COMPLETED).toList(); }
    private String warning(List<LabRequest> open, boolean optimize) { List<String> parts=new ArrayList<>(); if(!open.isEmpty()) parts.add("Незавершенные запросы: "+String.join(", ",open.stream().map(LabRequest::getRegistrationNumber).toList())); if(optimize) parts.add("Тяжелые изображения будут автоматически оптимизированы"); return parts.isEmpty()?null:String.join(". ",parts); }
    private String normalizeFormat(String value) { String result=value==null?"TEXT":value.toUpperCase(Locale.ROOT); if(!Set.of("TEXT","HTML").contains(result)) throw new ApiException(HttpStatus.BAD_REQUEST,"REPORT_FORMAT_INVALID","Поддерживаются форматы TEXT и HTML"); return result; }
    private String format(String text,String format) { if("TEXT".equals(format)) return text; return "<!doctype html><html lang=\"ru\"><meta charset=\"utf-8\"><title>Итоговый отчет</title><body><pre>"+escapeHtml(text)+"</pre></body></html>"; }
    private String escapeHtml(String text) { return text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;"); }

    private String renderText(CaseFile c,String template,boolean openLabs,List<String> mediaFiles,boolean optimized) {
        UUID id=c.getId(); List<Evidence> ev=evidence.findByCaseFileId(id); List<LabRequest> lab=labs.findByCaseFileId(id); List<GraphEdge> graph=edges.findByCaseFileId(id); List<Hypothesis> hyps=hypotheses.findByCaseFileId(id); List<Interview> ints=interviews.findByCaseFileIdOrderByOccurredAtAsc(id);
        StringBuilder out=new StringBuilder("Итоговый отчет по делу ").append(c.getRegistrationNumber()).append('\n').append(c.getTitle()).append("\n\nОписание: ").append(c.getDescription()).append("\nСтатус: ").append(c.getStatus()).append("\nДата открытия: ").append(c.getOpenedAt()).append("\n\n");
        if("SUMMARY".equalsIgnoreCase(template)) return out.append("Краткая сводка: улик — ").append(ev.size()).append(", экспертиз — ").append(lab.size()).append(", интервью — ").append(ints.size()).append(", связей — ").append(graph.size()).append(openLabs?"\nДанные ожидаются по незавершенным запросам.\n":"\n").append(optimized?"Тяжелые изображения оптимизированы.\n":"").toString();
        out.append("Хронология:\n"); timeline(c,ev,lab,hyps,ints).forEach(e->out.append("- ").append(e.at()).append(": ").append(e.text()).append('\n'));
        out.append("\nУлики:\n"); ev.forEach(e->out.append("- ").append(e.getRegistrationNumber()).append(": ").append(e.getName()).append(" [").append(e.getStatus()).append("] — ").append(e.getDescription()).append("; место: ").append(e.getLocationTitle()).append('\n'));
        out.append("\nЭкспертизы:\n"); lab.forEach(l->out.append("- ").append(l.getRegistrationNumber()).append(" / ").append(l.getProfile()).append(" [").append(l.getStatus()).append("]\n  Вопросы: ").append(l.getQuestions()).append("\n  Заключение: ").append(l.getResultText()==null?"Данные ожидаются":l.getResultText()).append('\n'));
        out.append("\nИнтервью:\n"); ints.forEach(i->out.append("- ").append(i.getOccurredAt()).append(" / ").append(i.getInterviewee()).append(": ").append(i.getProtocolText()).append('\n'));
        out.append("\nГипотезы:\n"); hyps.forEach(h->out.append("- ").append(h.getTitle()).append(" [").append(h.getConfidence()).append("]: ").append(h.getText()).append('\n'));
        out.append("\nСвязи графа:\n"); graph.forEach(e->out.append("- ").append(e.getSourceType()).append(':').append(e.getSourceId()).append(" — ").append(e.getSemanticType()).append(" → ").append(e.getTargetType()).append(':').append(e.getTargetId()).append(" [").append(e.getConfidence()).append("]\n"));
        if(optimized) { out.append("\nМедиа оптимизированы для ограничения памяти.\n"); mediaFiles.forEach(f->out.append("- ").append(f).append('\n')); }
        return out.toString();
    }

    private List<TimelineEntry> timeline(CaseFile c,List<Evidence> ev,List<LabRequest> labs,List<Hypothesis> hyps,List<Interview> ints) { List<TimelineEntry> result=new ArrayList<>(); result.add(new TimelineEntry(c.getOpenedAt(),"Дело открыто")); ev.forEach(e->result.add(new TimelineEntry(e.getDiscoveryDateTime(),"Обнаружена улика "+e.getRegistrationNumber()))); labs.forEach(l->result.add(new TimelineEntry(l.getCreatedAt(),"Создан запрос "+l.getRegistrationNumber()))); hyps.forEach(h->result.add(new TimelineEntry(h.getCreatedAt(),"Сформирована гипотеза «"+h.getTitle()+"»"))); ints.forEach(i->result.add(new TimelineEntry(i.getOccurredAt(),"Интервью: "+i.getInterviewee()))); result.sort(Comparator.comparing(TimelineEntry::at)); return result; }
    private List<Attachment> evidenceAttachments(UUID caseId) { return evidence.findByCaseFileId(caseId).stream().flatMap(e->attachments.findByOwnerTypeAndOwnerId("Evidence",e.getId()).stream()).toList(); }
    private boolean shouldOptimizeMedia(UUID caseId) { long limit=properties.getReportMediaMemoryLimitMb()*1024L*1024L; return evidenceAttachments(caseId).stream().mapToLong(Attachment::getSizeBytes).sum()>limit; }
    private MediaOptimization optimizeMedia(UUID caseId,String reportNumber) { if(!shouldOptimizeMedia(caseId)) return new MediaOptimization(false,List.of()); List<String> files=new ArrayList<>(); Path dir=Path.of(properties.getStoragePath(),"reports","assets",reportNumber).toAbsolutePath().normalize(); try { Files.createDirectories(dir); for(Attachment a:evidenceAttachments(caseId)) { if(!a.getMimeType().startsWith("image/")||!Files.exists(Path.of(a.getStoragePath()))) continue; BufferedImage source=ImageIO.read(Path.of(a.getStoragePath()).toFile()); if(source==null) continue; double scale=Math.min(1d,1280d/source.getWidth()); int width=Math.max(1,(int)(source.getWidth()*scale)),height=Math.max(1,(int)(source.getHeight()*scale)); BufferedImage target=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB); Graphics2D g=target.createGraphics(); g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR); g.drawImage(source,0,0,width,height,null); g.dispose(); Path output=dir.resolve(a.getId()+".jpg"); ImageIO.write(target,"jpg",output.toFile()); makeReadOnly(output); files.add(output.toString()); } return new MediaOptimization(true,files); } catch(IOException ex) { throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,"REPORT_MEDIA_OPTIMIZATION_ERROR","Не удалось оптимизировать изображения отчета"); } }
    private void makeReadOnly(Path path) throws IOException { DosFileAttributeView dos=Files.getFileAttributeView(path,DosFileAttributeView.class); if(dos!=null){dos.setReadOnly(true);return;} try{Files.setPosixFilePermissions(path,Set.of(PosixFilePermission.OWNER_READ,PosixFilePermission.GROUP_READ,PosixFilePermission.OTHERS_READ));}catch(UnsupportedOperationException ex){if(!path.toFile().setReadOnly())throw new IOException("Cannot mark report read-only");} }

    public record ReportDownload(byte[] content,String mediaType,String fileName) {}
    private record TimelineEntry(Instant at,String text) {}
    private record MediaOptimization(boolean optimized,List<String> files) {}
}
