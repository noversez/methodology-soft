package ru.casebook.dims.repo;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.casebook.dims.domain.Interview;
import java.util.List;
import java.util.UUID;
public interface InterviewRepository extends JpaRepository<Interview, UUID> { List<Interview> findByCaseFileIdOrderByOccurredAtAsc(UUID caseId); }
