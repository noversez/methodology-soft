package ru.casebook.dims.repo;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.casebook.dims.domain.CaseParticipant;
import java.util.List;
import java.util.UUID;
public interface CaseParticipantRepository extends JpaRepository<CaseParticipant,UUID>{
    List<CaseParticipant> findByCaseFileId(UUID caseId);
    boolean existsByCaseFileIdAndUserAccountId(UUID caseId,UUID userId);
}
