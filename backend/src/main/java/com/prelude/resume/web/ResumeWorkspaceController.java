package com.prelude.resume.web;

import com.prelude.BusinessException;
import com.prelude.Result;
import com.prelude.identity.api.CurrentAccount;
import com.prelude.resume.api.ResumeAssistantMessageResponse;
import com.prelude.resume.api.ResumeConversationResponse;
import com.prelude.resume.api.ResumeInstructionRequest;
import com.prelude.resume.api.ResumeTurnResponse;
import com.prelude.resume.application.ResumeAssistantRunner;
import com.prelude.resume.application.ResumeWorkspaceService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resume/workspace")
@RequiredArgsConstructor
public class ResumeWorkspaceController {

    private final ResumeWorkspaceService workspace;
    private final ResumeAssistantRunner assistantRunner;
    private final CurrentAccount currentAccount;

    @GetMapping("/conversations")
    public Result<List<ResumeConversationResponse>> conversations() {
        return Result.success(workspace.listConversations(currentAccountId()));
    }

    @PostMapping("/conversations")
    public Result<ResumeConversationResponse> createConversation(@RequestBody Map<String, Long> body) {
        return Result.success(workspace.createConversation(currentAccountId(), body.get("resumeId")));
    }

    @PutMapping("/conversations/{conversationId}/pin")
    public Result<Void> pinConversation(
        @PathVariable Long conversationId,
        @RequestBody Map<String, Boolean> body
    ) {
        workspace.pinConversation(currentAccountId(), conversationId, Boolean.TRUE.equals(body.get("pinned")));
        return Result.success();
    }

    @DeleteMapping("/conversations/{conversationId}")
    public Result<Void> deleteConversation(@PathVariable Long conversationId) {
        workspace.deleteConversation(currentAccountId(), conversationId);
        return Result.success();
    }

    @GetMapping("/conversations/{conversationId}/turns")
    public Result<List<ResumeTurnResponse>> turns(@PathVariable Long conversationId) {
        return Result.success(workspace.listTurns(currentAccountId(), conversationId));
    }

    @PostMapping("/conversations/{conversationId}/turns")
    public Result<ResumeTurnResponse> submit(
        @PathVariable Long conversationId,
        @Valid @RequestBody ResumeInstructionRequest request
    ) {
        Long accountId = currentAccountId();
        ResumeTurnResponse turn = workspace.submitTurn(accountId, conversationId, request.instruction());
        if ("running".equals(turn.status())) {
            ResumeAssistantMessageResponse message =
                assistantRunner.run(accountId, turn.id(), request.instruction());
            workspace.completeTurn(accountId, turn.id());
            return Result.success(workspace.listTurns(accountId, conversationId).stream()
                .filter(row -> row.id().equals(turn.id()))
                .findFirst()
                .map(row -> new ResumeTurnResponse(
                    row.id(),
                    row.instruction(),
                    row.status(),
                    row.createdAt(),
                    row.startedAt(),
                    row.completedAt(),
                    List.of(message)
                ))
                .orElse(turn));
        }
        return Result.success(turn);
    }

    private Long currentAccountId() {
        Long accountId = currentAccount.idOrNull();
        if (accountId == null) throw BusinessException.unauthorized("请先登录");
        return accountId;
    }
}
