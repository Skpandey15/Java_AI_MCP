package com.onlineinterview.session.application;

import com.onlineinterview.interview.domain.InterviewStatus;
import com.onlineinterview.interview.infrastructure.InterviewDefinitionRepository;
import com.onlineinterview.session.domain.ManualQuestion;
import com.onlineinterview.session.infrastructure.ManualQuestionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class QuestionService {
    private final InterviewDefinitionRepository definitions;
    private final ManualQuestionRepository questions;

    public QuestionService(InterviewDefinitionRepository definitions, ManualQuestionRepository questions) {
        this.definitions = definitions;
        this.questions = questions;
    }

    @Transactional
    public ManualQuestion add(String ownerSubject, UUID interviewId, int order, String prompt, int maxScore) {
        var definition = ownedDefinition(ownerSubject, interviewId);
        if (definition.getStatus() != InterviewStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Questions can only be changed on a draft");
        }
        return questions.save(ManualQuestion.create(definition, order, prompt, maxScore));
    }

    @Transactional(readOnly = true)
    public List<ManualQuestion> list(String ownerSubject, UUID interviewId) {
        ownedDefinition(ownerSubject, interviewId);
        return questions.findByInterviewDefinitionIdOrderByOrderAsc(interviewId);
    }

    private com.onlineinterview.interview.domain.InterviewDefinition ownedDefinition(
            String ownerSubject, UUID interviewId) {
        var definition = definitions.findById(interviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Interview not found"));
        if (!definition.getOwnerSubject().equals(ownerSubject)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Interview not found");
        }
        return definition;
    }
}
