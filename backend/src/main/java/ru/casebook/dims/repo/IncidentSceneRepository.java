package ru.casebook.dims.repo;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.casebook.dims.domain.IncidentScene;
import java.util.List;
import java.util.UUID;
public interface IncidentSceneRepository extends JpaRepository<IncidentScene, UUID> { List<IncidentScene> findByCaseFileId(UUID caseId); }
