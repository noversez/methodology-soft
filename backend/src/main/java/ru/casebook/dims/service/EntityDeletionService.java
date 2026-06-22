package ru.casebook.dims.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.casebook.dims.api.ApiException;
import ru.casebook.dims.domain.Role;
import ru.casebook.dims.domain.UserAccount;

import java.util.List;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class EntityDeletionService {
    private final JdbcTemplate jdbc;
    private final CurrentUserService currentUsers;
    private final AuditService audit;

    public EntityDeletionService(JdbcTemplate jdbc, CurrentUserService currentUsers, AuditService audit) {
        this.jdbc = jdbc; this.currentUsers = currentUsers; this.audit = audit;
    }

    @Transactional
    public void deleteCase(UserAccount actor, UUID caseId) {
        requireDetective(actor); requireExists("cases", caseId, "CASE_NOT_FOUND", "Дело не найдено");
        List<String> files=jdbc.query("select storage_path from attachments where owner_id in (select id from evidence where case_file_id = ?) or owner_id in (select id from tasks where case_file_id = ?)",(rs,row)->rs.getString(1),caseId,caseId);
        files.addAll(jdbc.query("select storage_path from reports where case_file_id = ?",(rs,row)->rs.getString(1),caseId));
        scheduleFileDeletion(files);
        audit.record(actor, "CASE_DELETED", "Case", caseId, "{}");
        jdbc.update("delete from attachments where owner_type = 'Evidence' and owner_id in (select id from evidence where case_file_id = ?)", caseId);
        jdbc.update("delete from attachments where owner_type = 'Task' and owner_id in (select id from tasks where case_file_id = ?)", caseId);
        jdbc.update("delete from graph_edges where case_file_id = ?", caseId);
        jdbc.update("delete from reports where case_file_id = ?", caseId);
        jdbc.update("delete from lab_requests where case_file_id = ?", caseId);
        jdbc.update("delete from tasks where case_file_id = ?", caseId);
        jdbc.update("delete from evidence_versions where evidence_id in (select id from evidence where case_file_id = ?)", caseId);
        jdbc.update("delete from evidence where case_file_id = ?", caseId);
        jdbc.update("delete from hypotheses where case_file_id = ?", caseId);
        jdbc.update("delete from incident_scenes where case_file_id = ?", caseId);
        jdbc.update("delete from interviews where case_file_id = ?", caseId);
        jdbc.update("delete from case_participants where case_file_id = ?", caseId);
        jdbc.update("delete from cases where id = ?", caseId);
    }

    @Transactional public void deleteEvidence(UserAccount actor, UUID id) {
        requireDetective(actor); UUID caseId = caseId("evidence", id, "EVIDENCE_NOT_FOUND", "Улика не найдена");
        scheduleFileDeletion(jdbc.query("select storage_path from attachments where owner_type = 'Evidence' and owner_id = ?",(rs,row)->rs.getString(1),id));
        deleteGraphReferences(caseId, "EVIDENCE", id); jdbc.update("update tasks set result_evidence_id = null where result_evidence_id = ?", id);
        jdbc.update("delete from lab_requests where evidence_id = ?", id); jdbc.update("delete from attachments where owner_type = 'Evidence' and owner_id = ?", id);
        jdbc.update("delete from evidence_versions where evidence_id = ?", id);
        jdbc.update("delete from evidence where id = ?", id); advanceGraph(caseId); audit.record(actor, "EVIDENCE_DELETED", "Evidence", id, "{}");
    }
    @Transactional public void deleteTask(UserAccount actor, UUID id) { scheduleFileDeletion(jdbc.query("select storage_path from attachments where owner_type = 'Task' and owner_id = ?",(rs,row)->rs.getString(1),id)); deleteNode(actor,"tasks","Task","TASK",id); jdbc.update("delete from attachments where owner_type = 'Task' and owner_id = ?",id); }
    @Transactional public void deleteScene(UserAccount actor, UUID id) { deleteNode(actor,"incident_scenes","IncidentScene","LOCATION",id); }
    @Transactional public void deleteInterview(UserAccount actor, UUID id) { deleteSimple(actor,"interviews","Interview",id); }
    @Transactional public void deleteReport(UserAccount actor, UUID id) { scheduleFileDeletion(jdbc.query("select storage_path from reports where id = ?",(rs,row)->rs.getString(1),id)); deleteNode(actor,"reports","Report","REPORT",id); }
    @Transactional public void deleteLab(UserAccount actor, UUID id) {
        requireDetective(actor); UUID caseId=caseId("lab_requests",id,"LAB_REQUEST_NOT_FOUND","Экспертиза не найдена");
        List<UUID> evidenceIds=jdbc.query("select evidence_id from lab_requests where id = ?",(rs,row)->rs.getObject(1,UUID.class),id);
        deleteGraphReferences(caseId,"LAB_REQUEST",id); jdbc.update("delete from lab_requests where id = ?",id);
        evidenceIds.forEach(evidenceId->jdbc.update("update evidence set status = 'REGISTERED' where id = ? and not exists (select 1 from lab_requests where evidence_id = ?)",evidenceId,evidenceId));
        advanceGraph(caseId); audit.record(actor,"LAB_REQUEST_DELETED","LabRequest",id,"{}");
    }

    private void deleteNode(UserAccount actor,String table,String entity,String nodeType,UUID id) { requireDetective(actor); UUID caseId=caseId(table,id,entity.toUpperCase()+"_NOT_FOUND","Объект не найден"); deleteGraphReferences(caseId,nodeType,id); jdbc.update("delete from "+table+" where id = ?",id); advanceGraph(caseId); audit.record(actor,entity.toUpperCase()+"_DELETED",entity,id,"{}"); }
    private void deleteSimple(UserAccount actor,String table,String entity,UUID id) { requireDetective(actor); caseId(table,id,entity.toUpperCase()+"_NOT_FOUND","Объект не найден"); jdbc.update("delete from "+table+" where id = ?",id); audit.record(actor,entity.toUpperCase()+"_DELETED",entity,id,"{}"); }
    private void deleteGraphReferences(UUID caseId,String type,UUID id) {
        List<UUID> hypothesisIds=jdbc.query("select hypothesis_id from graph_edges where case_file_id = ? and hypothesis_id is not null and ((source_type = ? and source_id = ?) or (target_type = ? and target_id = ?))",(rs,row)->rs.getObject(1,UUID.class),caseId,type,id,type,id);
        jdbc.update("delete from graph_edges where case_file_id = ? and ((source_type = ? and source_id = ?) or (target_type = ? and target_id = ?))",caseId,type,id,type,id);
        hypothesisIds.forEach(hypothesisId->jdbc.update("delete from hypotheses where id = ? and not exists (select 1 from graph_edges where hypothesis_id = ?)",hypothesisId,hypothesisId));
    }
    private void advanceGraph(UUID caseId) { jdbc.update("update cases set graph_revision = graph_revision + 1 where id = ?",caseId); }
    private UUID caseId(String table,UUID id,String code,String message) { return jdbc.query("select case_file_id from "+table+" where id = ?",(rs,row)->rs.getObject(1,UUID.class),id).stream().findFirst().orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,code,message)); }
    private void requireExists(String table,UUID id,String code,String message) { Long count=jdbc.queryForObject("select count(*) from "+table+" where id = ?",Long.class,id); if(count==null||count==0)throw new ApiException(HttpStatus.NOT_FOUND,code,message); }
    private void requireDetective(UserAccount actor) { currentUsers.requireAnyRole(actor,Role.DETECTIVE); }
    private void scheduleFileDeletion(List<String> paths) {
        if(paths.isEmpty())return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { paths.forEach(path->{try{Files.deleteIfExists(Path.of(path));}catch(Exception ignored){}}); }
        });
    }
}
