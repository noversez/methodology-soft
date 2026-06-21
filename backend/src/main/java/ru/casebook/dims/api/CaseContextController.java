package ru.casebook.dims.api;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import ru.casebook.dims.api.dto.CaseContextDtos.*;
import ru.casebook.dims.service.*;
import java.util.List;
import java.util.UUID;

@RestController @RequestMapping("/api/cases/{caseId}")
public class CaseContextController {
    private final CaseContextService service; private final CurrentUserService users;
    public CaseContextController(CaseContextService service, CurrentUserService users) { this.service=service; this.users=users; }
    @GetMapping("/scenes") public List<SceneResponse> scenes(@PathVariable UUID caseId) { return service.scenes(caseId).stream().map(SceneResponse::from).toList(); }
    @PostMapping("/scenes") public SceneResponse scene(@RequestHeader("X-User-Id") String userId,@PathVariable UUID caseId,@Valid @RequestBody SceneRequest r) { return SceneResponse.from(service.addScene(users.requireUser(userId),caseId,r)); }
}
