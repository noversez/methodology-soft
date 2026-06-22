package ru.casebook.dims.api;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import ru.casebook.dims.api.dto.CaseContextDtos.*;
import ru.casebook.dims.service.*;
import java.util.List;
import java.util.UUID;
import ru.casebook.dims.api.dto.UserDto;

@RestController @RequestMapping("/api/cases/{caseId}")
public class CaseContextController {
    private final CaseContextService service; private final CurrentUserService users; private final EntityDeletionService deletion;
    public CaseContextController(CaseContextService service, CurrentUserService users, EntityDeletionService deletion) { this.service=service; this.users=users; this.deletion=deletion; }
    @GetMapping("/scenes") public List<SceneResponse> scenes(@PathVariable UUID caseId) { return service.scenes(caseId).stream().map(SceneResponse::from).toList(); }
    @PostMapping("/scenes") public SceneResponse scene(@RequestHeader("X-User-Id") String userId,@PathVariable UUID caseId,@Valid @RequestBody SceneRequest r) { return SceneResponse.from(service.addScene(users.requireUser(userId),caseId,r)); }
    @GetMapping("/interviews") public List<InterviewResponse> interviews(@PathVariable UUID caseId) { return service.interviews(caseId).stream().map(InterviewResponse::from).toList(); }
    @PostMapping("/interviews") public InterviewResponse interview(@RequestHeader("X-User-Id") String userId,@PathVariable UUID caseId,@Valid @RequestBody InterviewRequest r) { return InterviewResponse.from(service.addInterview(users.requireUser(userId),caseId,r)); }
    @PatchMapping("/scenes/{id}") public SceneResponse updateScene(@RequestHeader("X-User-Id") String userId,@PathVariable UUID id,@Valid @RequestBody SceneRequest r){return SceneResponse.from(service.updateScene(users.requireUser(userId),id,r));}
    @DeleteMapping("/scenes/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) public void deleteScene(@RequestHeader("X-User-Id") String userId,@PathVariable UUID id){deletion.deleteScene(users.requireUser(userId),id);}
    @PatchMapping("/interviews/{id}") public InterviewResponse updateInterview(@RequestHeader("X-User-Id") String userId,@PathVariable UUID id,@Valid @RequestBody InterviewRequest r){return InterviewResponse.from(service.updateInterview(users.requireUser(userId),id,r));}
    @DeleteMapping("/interviews/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) public void deleteInterview(@RequestHeader("X-User-Id") String userId,@PathVariable UUID id){deletion.deleteInterview(users.requireUser(userId),id);}
    @GetMapping("/participants") public List<UserDto> participants(@PathVariable UUID caseId){return service.participants(caseId).stream().map(UserDto::from).toList();}
    @PostMapping("/participants") public UserDto participant(@RequestHeader("X-User-Id") String userId,@PathVariable UUID caseId,@Valid @RequestBody ParticipantRequest r){return UserDto.from(service.addParticipant(users.requireUser(userId),caseId,r.userId()));}
}
