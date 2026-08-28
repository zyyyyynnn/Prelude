package com.prelude.resume.application;

import com.prelude.resume.application.port.ResumeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListResumes {

    private final ResumeRepository repository;

    public List<ResumeRepository.ResumeListItem> execute(Long userId) {
        return repository.listByOwner(userId);
    }
}
