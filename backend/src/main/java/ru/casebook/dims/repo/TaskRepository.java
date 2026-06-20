package ru.casebook.dims.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.casebook.dims.domain.TaskItem;

import java.util.List;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<TaskItem, UUID> {
    List<TaskItem> findByCaseFileId(UUID caseId);
    List<TaskItem> findByAssigneeId(UUID assigneeId);
}
