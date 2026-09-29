package com.prelude.resume.infrastructure;

import com.prelude.resume.domain.BlockOperation;
import com.prelude.resume.domain.ResumeAgentStep;
import com.prelude.resume.domain.ResumeDocument;
import com.prelude.resume.domain.ResumePatchProposal;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * The one place resume documents cross between their typed form and the JSON the tables
 * store, so a column's shape is decided once instead of at every adapter.
 */
@Component
public class ResumeCodec {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<Long>> LONG_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<OperationDto>> OPERATION_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<FactRiskDto>> FACT_RISK_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<FileDiffDto>> FILE_LIST = new TypeReference<>() {
    };

    private final ObjectMapper mapper;

    public ResumeCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    List<String> readStringList(String json) {
        return readList(json, STRING_LIST);
    }

    String writeList(List<?> values) {
        return values == null || values.isEmpty() ? null : write(values);
    }

    List<Long> readLongList(String json) {
        return readList(json, LONG_LIST);
    }

    ResumeDocument readDocument(String json) {
        if (json == null || json.isBlank()) {
            return new ResumeDocument(List.of());
        }
        DocumentDto dto = read(json, DocumentDto.class);
        return new ResumeDocument(dto.blocks().stream()
            .map(block -> new ResumeDocument.Block(block.id(), block.section(), block.kind(), block.text()))
            .toList());
    }

    String writeDocument(ResumeDocument document) {
        return write(new DocumentDto(document.blocks().stream()
            .map(block -> new BlockDto(block.id(), block.section(), block.kind(), block.text()))
            .toList()));
    }

    List<BlockOperation> readOperations(String json) {
        return readList(json, OPERATION_LIST).stream()
            .map(dto -> new BlockOperation(
                BlockOperation.Kind.valueOf(dto.kind().toUpperCase()),
                dto.blockId(),
                dto.section(),
                dto.text()))
            .toList();
    }

    String writeOperations(List<BlockOperation> operations) {
        return write(operations.stream()
            .map(operation -> new OperationDto(
                operation.kind().name().toLowerCase(),
                operation.blockId(),
                operation.section(),
                operation.text()))
            .toList());
    }

    List<ResumePatchProposal.FactRisk> readFactRisk(String json) {
        return readList(json, FACT_RISK_LIST).stream()
            .map(dto -> new ResumePatchProposal.FactRisk(dto.blockId(), dto.statement()))
            .toList();
    }

    String writeFactRisk(List<ResumePatchProposal.FactRisk> risks) {
        return risks == null || risks.isEmpty() ? null : write(risks.stream()
            .map(risk -> new FactRiskDto(risk.blockId(), risk.statement()))
            .toList());
    }

    List<ResumeAgentStep.FileDiff> readFiles(String json) {
        return readList(json, FILE_LIST).stream()
            .map(dto -> new ResumeAgentStep.FileDiff(dto.name(), dto.add(), dto.del()))
            .toList();
    }

    String writeFiles(List<ResumeAgentStep.FileDiff> files) {
        return files == null || files.isEmpty() ? null : write(files.stream()
            .map(file -> new FileDiffDto(file.name(), file.add(), file.del()))
            .toList());
    }

    List<String> readDetail(String json) {
        return readList(json, STRING_LIST);
    }

    private <T> List<T> readList(String json, TypeReference<List<T>> type) {
        return json == null || json.isBlank() ? List.of() : read(json, type);
    }

    private <T> T read(String json, TypeReference<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (Exception exception) {
            throw new IllegalStateException("简历工作区数据读取失败", exception);
        }
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (Exception exception) {
            throw new IllegalStateException("简历工作区数据读取失败", exception);
        }
    }

    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("简历工作区数据写入失败", exception);
        }
    }

    record DocumentDto(List<BlockDto> blocks) {
    }

    record BlockDto(String id, String section, String kind, String text) {
    }

    record OperationDto(String kind, String blockId, String section, String text) {
    }

    record FactRiskDto(String blockId, String statement) {
    }

    record FileDiffDto(String name, int add, int del) {
    }
}
