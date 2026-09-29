package com.prelude.resume.application;

import com.prelude.assets.api.AttachmentContextPort;
import com.prelude.llm.api.LlmPort;
import com.prelude.llm.api.ModelCapabilityResponse;
import com.prelude.llm.api.ModelConfigurationView;
import com.prelude.llm.api.ModelExecutionSnapshotRef;
import com.prelude.llm.api.ProviderDescriptorView;
import com.prelude.llm.api.PromptRegistry;
import com.prelude.llm.api.SaveConfigurationCommand;
import com.prelude.resume.application.port.ResumeRepository;
import com.prelude.resume.application.repository.ResumeDocumentRepository;
import com.prelude.resume.application.repository.ResumeTranscriptRepository;
import com.prelude.resume.application.repository.ResumeWorkspaceRepository;
import com.prelude.resume.domain.ResumeAgentRun;
import com.prelude.resume.domain.ResumeAgentStep;
import com.prelude.resume.domain.ResumeConversation;
import com.prelude.resume.domain.ResumeDocument;
import com.prelude.resume.domain.ResumePatchProposal;
import com.prelude.resume.domain.ResumeRevision;
import com.prelude.resume.domain.ResumeTurn;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a run is allowed to record and to say.
 *
 * <p>The model here is a stand-in that actually calls the tool bindings it is handed, which is
 * the only way to check the claim this file is about: a step exists because the model asked
 * for that work. The repositories are in-memory, so the assertions read the trace exactly as
 * the workspace would.
 */
class ResumeAssistantRunnerTest {

    private final RecordingTranscript transcript = new RecordingTranscript();
    private final RecordingDocuments documents = new RecordingDocuments();
    private final FakeLlm llm = new FakeLlm();
    private final ResumeAssistantRunner runner = new ResumeAssistantRunner(
        workspace(), transcript, documents, resumes(),
        Mockito.mock(AttachmentContextPort.class), llm, prompts(), new ResumePolicy(),
        new ObjectMapper());

    @Test
    @DisplayName("步骤只在模型真的调用工具时出现，没调用的就不该有")
    void stepsExistOnlyWhereTheModelActuallyCalled() {
        llm.callsTools("list_blocks", "search_material");
        llm.answer = """
            {"reason":"把技能块写成一行","operations":[\
            {"op":"replace","blockId":"技能-1","text":"Java（三年生产经验）"}]}""";

        runner.run(1L, turn());

        List<String> labels = transcript.steps.stream().map(ResumeAgentStep::label).toList();
        assertThat(labels).contains("查看文档结构", "检索原始材料");
        assertThat(labels).noneMatch(label -> label.startsWith("读取块 "));
        assertThat(llm.requestedTools).contains("list_blocks", "read_block", "search_material");
    }

    @Test
    @DisplayName("提案落地后，用户面读到的是通过校验的理由，不是模型的原始输出")
    void theMessageCarriesTheProposalReasonNotARawCompletion() {
        llm.callsTools("read_block");
        llm.answer = """
            {"reason":"按量化口径改写技能块","operations":[\
            {"op":"replace","blockId":"技能-1","text":"Java（三年生产经验）"}]}""";

        runner.run(1L, turn());

        assertThat(transcript.messages).singleElement()
            .isEqualTo("按量化口径改写技能块");
        assertThat(transcript.proposals).hasSize(1);
        assertThat(transcript.runStatus).isEqualTo(ResumeAgentRun.Status.PROPOSAL_READY);
    }

    @Test
    @DisplayName("校验不过时只重试一次，简历不动，并说明原因")
    void anInvalidCandidateIsRepairedOnceAndThenRefused() {
        llm.callsTools();
        llm.answer = """
            {"reason":"改一个不存在的块","operations":[\
            {"op":"replace","blockId":"不存在的块","text":"随便"}]}""";

        runner.run(1L, turn());

        assertThat(llm.completions).as("one answer plus one repair, never a retry loop")
            .isEqualTo(2);
        assertThat(transcript.proposals).isEmpty();
        assertThat(transcript.runStatus).isEqualTo(ResumeAgentRun.Status.INVALID);
        assertThat(transcript.messages.get(0)).contains("没有通过校验", "简历未被改动");
    }

    @Test
    @DisplayName("材料里没有的数字被点名为事实风险，但提案仍然交给人判断")
    void unsupportedNumbersAreNamedRatherThanDropped() {
        llm.callsTools();
        llm.answer = """
            {"reason":"补充性能结果","operations":[\
            {"op":"replace","blockId":"项目-1-描述","text":"接口 P99 从 480ms 降到 90ms"}]}""";

        runner.run(1L, turn());

        ResumePatchProposal proposal = transcript.proposals.get(0);
        assertThat(proposal.factRisk()).extracting(ResumePatchProposal.FactRisk::statement)
            .contains("90ms")
            .doesNotContain("480ms");
        assertThat(transcript.runStatus).isEqualTo(ResumeAgentRun.Status.PROPOSAL_READY);
    }

    @Test
    @DisplayName("提案步骤带着实测的行差，工作区据此渲染文件芯片")
    void theProposalStepCarriesMeasuredLineCounts() {
        llm.callsTools();
        llm.answer = """
            {"reason":"重写两条技能","operations":[\
            {"op":"replace","blockId":"技能-1","text":"Java（三年）\\nSpring（两年）\\nMySQL"},\
            {"op":"replace","blockId":"项目-1-描述","text":"支撑日均 200 万单，可用性 99.99%"}]}""";

        runner.run(1L, turn());

        ResumeAgentStep proposalStep = transcript.steps.stream()
            .filter(step -> step.kind() == ResumeAgentStep.Kind.PROPOSAL)
            .findFirst().orElseThrow();
        assertThat(proposalStep.files()).singleElement().satisfies(file -> {
            assertThat(file.name()).isEqualTo("resume.md");
            // Three new lines in and one out for the skill block, plus one swapped line in the
            // project block: the chip reports what the edit measured, not what it approximated.
            assertThat(file.add()).isEqualTo(4);
            assertThat(file.del()).isEqualTo(2);
        });
    }

    private ResumeTurn turn() {
        return new ResumeTurn(7L, 3L, 1L, "把技能写成量化导向", List.of(), List.of(),
            ResumeTurn.Status.RUNNING, 1, LocalDateTime.now(), LocalDateTime.now(), null, null);
    }

    private ResumeWorkspaceRepository workspace() {
        ResumeWorkspaceRepository workspace = Mockito.mock(ResumeWorkspaceRepository.class);
        Mockito.when(workspace.findConversation(Mockito.anyLong(), Mockito.anyLong()))
            .thenReturn(Optional.of(new ResumeConversation(
                3L, 1L, 11L, "新的简历工作", null, LocalDateTime.now(), LocalDateTime.now())));
        return workspace;
    }

    private ResumeRepository resumes() {
        ResumeRepository resumes = Mockito.mock(ResumeRepository.class);
        Mockito.when(resumes.findById(Mockito.anyLong())).thenReturn(Optional.of(
            new ResumeRepository.StoredResume(11L, 1L, "r.pdf",
                "后端开发工程师\n接口 P99 从 480ms 降到 210ms",
                List.of("Java"), List.of(), LocalDateTime.now())));
        return resumes;
    }

    private PromptRegistry prompts() {
        return promptId -> "你是简历制作助手。";
    }

    /** A model that really invokes the bindings it is offered, the way a tool-calling one would. */
    private final class FakeLlm implements LlmPort {

        private String answer = "{}";
        private List<String> toolsToCall = List.of();
        private List<String> requestedTools = List.of();
        private int completions;

        private void callsTools(String... names) {
            toolsToCall = List.of(names);
        }

        @Override
        public CompletionResult complete(ModelExecutionRequest request) {
            completions++;
            requestedTools = request.tools().stream().map(ToolBinding::name).toList();
            for (String name : toolsToCall) {
                request.tools().stream()
                    .filter(binding -> binding.name().equals(name))
                    .findFirst()
                    .orElseThrow()
                    .handler()
                    .call(argumentsFor(name));
            }
            return new CompletionResult(answer, null);
        }

        @Override
        public ModelExecutionSnapshotRef freezeSnapshot(FreezeSnapshotCommand command) {
            return new ModelExecutionSnapshotRef(99L);
        }

        @Override
        public void stream(ModelExecutionRequest request, StreamSink sink) {
            throw new UnsupportedOperationException("本测试不走流式路径");
        }

        @Override
        public FrozenModelConfiguration frozenConfiguration(Long accountId, Long snapshotId) {
            throw new UnsupportedOperationException("本测试不读冻结配置");
        }

        @Override
        public ModelConfigurationView currentConfiguration(Long accountId) {
            throw new UnsupportedOperationException("本测试不读模型配置");
        }

        @Override
        public ModelConfigurationView saveConfiguration(
            Long accountId, SaveConfigurationCommand command) {
            throw new UnsupportedOperationException("本测试不写模型配置");
        }

        @Override
        public List<ProviderDescriptorView> listModels() {
            throw new UnsupportedOperationException("本测试不列模型");
        }

        @Override
        public DiscoveredModelsView discoverCustomModels(
            Long accountId, DiscoverModelsCommand command) {
            throw new UnsupportedOperationException("本测试不发现模型");
        }

        @Override
        public ModelCapabilityResponse discoverCustomModelCapability(
            Long accountId, DiscoverModelCapabilityCommand command) {
            throw new UnsupportedOperationException("本测试不探测模型能力");
        }

        private String argumentsFor(String name) {
            return switch (name) {
                case "read_block" -> "{\"blockId\":\"技能-1\"}";
                case "search_material" -> "{\"query\":\"P99\"}";
                default -> "{}";
            };
        }
    }

    private final class RecordingDocuments implements ResumeDocumentRepository {

        private final ResumeRevision base = new ResumeRevision(
            1L, 11L, 4,
            new ResumeDocument(List.of(
                new ResumeDocument.Block("概览-1", "概览", ResumeDocument.Block.SECTION, "概览"),
                new ResumeDocument.Block("技能-1", "技能", ResumeDocument.Block.BULLET, "Java"),
                new ResumeDocument.Block("项目-1-描述", "项目", ResumeDocument.Block.BULLET,
                    "支撑日均 200 万单"))),
            "seed", ResumeRevision.Origin.IMPORTED, null, LocalDateTime.now());

        @Override
        public Optional<ResumeRevision> latest(Long resumeId) {
            return Optional.of(base);
        }

        @Override
        public Optional<ResumeRevision> find(Long resumeId, int revisionNumber) {
            return revisionNumber == base.revisionNumber() ? Optional.of(base) : Optional.empty();
        }

        @Override
        public Optional<ResumeRevision> findByProposal(Long proposalId) {
            return Optional.empty();
        }

        @Override
        public List<ResumeRevision> list(Long resumeId) {
            return List.of(base);
        }

        @Override
        public ResumeRevision append(ResumeRevision revision) {
            return revision;
        }
    }

    private final class RecordingTranscript implements ResumeTranscriptRepository {

        private final List<ResumeAgentStep> steps = new ArrayList<>();
        private final List<String> messages = new ArrayList<>();
        private final List<ResumePatchProposal> proposals = new ArrayList<>();
        private ResumeAgentRun.Status runStatus;

        @Override
        public Long appendMessage(Long turnId, Long conversationId, String content, LocalDateTime now) {
            messages.add(content);
            return 1L;
        }

        @Override
        public List<AssistantMessage> listMessages(Collection<Long> turnIds) {
            return List.of();
        }

        @Override
        public Long openRun(ResumeAgentRun run) {
            return 1L;
        }

        @Override
        public Optional<ResumeAgentRun> findRun(Long runId) {
            return Optional.empty();
        }

        @Override
        public Optional<ResumeAgentRun> findRunByTurn(Long turnId) {
            return Optional.empty();
        }

        @Override
        public List<ResumeAgentRun> listRuns(Collection<Long> turnIds) {
            return List.of();
        }

        @Override
        public void finishRun(Long runId, ResumeAgentRun.Status status, String failureReason,
                              LocalDateTime now) {
            runStatus = status;
        }

        @Override
        public void appendStep(ResumeAgentStep step) {
            steps.add(step);
        }

        @Override
        public List<ResumeAgentStep> listSteps(Collection<Long> runIds) {
            return steps;
        }

        @Override
        public int nextStepOrder(Long runId) {
            return steps.size();
        }

        @Override
        public Long recordProposal(ResumePatchProposal proposal) {
            proposals.add(proposal);
            return 1L;
        }

        @Override
        public Optional<ResumePatchProposal> findProposal(Long proposalId) {
            return Optional.empty();
        }

        @Override
        public List<ResumePatchProposal> listProposals(Collection<Long> conversationIds) {
            return proposals;
        }

        @Override
        public boolean decide(Long proposalId, ResumePatchProposal.Status status, String note,
                              Long accountId, LocalDateTime now) {
            return true;
        }

        @Override
        public List<ResumePatchProposal> listPendingProposalsBehindRevision(Long resumeId, int revision) {
            return List.of();
        }
    }
}
