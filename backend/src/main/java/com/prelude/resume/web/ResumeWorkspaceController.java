package com.prelude.resume.web;

import com.prelude.BusinessException;
import com.prelude.Result;
import com.prelude.identity.api.CurrentAccount;
import com.prelude.resume.api.ResumeConversationResponse;
import com.prelude.resume.api.ResumeCreateConversationRequest;
import com.prelude.resume.api.ResumeDocumentResponse;
import com.prelude.resume.api.ResumeInstructionRequest;
import com.prelude.resume.api.ResumePinRequest;
import com.prelude.resume.api.ResumeProposalResponse;
import com.prelude.resume.api.ResumeTurnResponse;
import com.prelude.resume.application.ResumePatchDecisionService;
import com.prelude.resume.application.ResumeWorkspaceService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP adapter for the resume workspace. Every path resolves the caller first. */
@RestController
@RequestMapping("/api/resume/workspace")
@RequiredArgsConstructor
public class ResumeWorkspaceController {

    private final ResumeWorkspaceService workspace;
    private final ResumePatchDecisionService decisions;
    private final CurrentAccount currentAccount;

    @GetMapping("/conversations")
    public Result<List<ResumeConversationResponse>> conversations() {
        return Result.success(workspace.listConversations(currentAccountId()));
    }

    @PostMapping("/conversations")
    public Result<ResumeConversationResponse> createConversation(
        @Valid @RequestBody ResumeCreateConversationRequest request
    ) {
        return Result.success(workspace.createConversation(currentAccountId(), request.resumeId()));
    }

    @PutMapping("/conversations/{conversationId}/pin")
    public Result<Void> pinConversation(
        @PathVariable Long conversationId,
        @Valid @RequestBody ResumePinRequest request
    ) {
        workspace.pinConversation(currentAccountId(), conversationId, request.pinned());
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
        return Result.success(workspace.submit(currentAccountId(), conversationId, request));
    }

    @DeleteMapping("/conversations/{conversationId}/turns/{turnId}")
    public Result<Void> cancel(
        @PathVariable Long conversationId,
        @PathVariable Long turnId
    ) {
        workspace.cancel(currentAccountId(), conversationId, turnId);
        return Result.success();
    }

    @GetMapping("/conversations/{conversationId}/document")
    public Result<ResumeDocumentResponse> document(@PathVariable Long conversationId) {
        return Result.success(workspace.document(currentAccountId(), conversationId));
    }

    /**
     * The patches awaiting a decision. Deliberately its own read rather than part of the turn
     * stream: the workspace that renders instructions stays as reviewed, and the decision
     * surface consumes this when it is built.
     */
    @GetMapping("/conversations/{conversationId}/proposals")
    public Result<List<ResumeProposalResponse>> proposals(@PathVariable Long conversationId) {
        return Result.success(workspace.proposals(currentAccountId(), conversationId));
    }

    @PostMapping("/proposals/{proposalId}/accept")
    public Result<ResumeProposalResponse> accept(@PathVariable Long proposalId) {
        return Result.success(decisions.accept(currentAccountId(), proposalId));
    }

    @PostMapping("/proposals/{proposalId}/reject")
    public Result<ResumeProposalResponse> reject(@PathVariable Long proposalId) {
        return Result.success(decisions.reject(currentAccountId(), proposalId));
    }

    private Long currentAccountId() {
        Long accountId = currentAccount.idOrNull();
        if (accountId == null) {
            throw BusinessException.unauthorized("请先登录");
        }
        return accountId;
    }
}
