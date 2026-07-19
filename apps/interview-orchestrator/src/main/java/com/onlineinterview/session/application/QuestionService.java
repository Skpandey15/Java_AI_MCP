package com.onlineinterview.session.application;

import com.onlineinterview.interview.domain.InterviewStatus;
import com.onlineinterview.interview.infrastructure.InterviewDefinitionRepository;
import com.onlineinterview.session.domain.ManualQuestion;
import com.onlineinterview.session.domain.QuestionType;
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
    public ManualQuestion add(String ownerSubject, UUID interviewId, int order, String prompt,
            int maxScore, QuestionType type, List<String> options, List<String> correctAnswers) {
        var definition = editableDefinition(ownerSubject, interviewId);
        try {
            return questions.save(ManualQuestion.create(definition, order, prompt, maxScore,
                    type, options, correctAnswers));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @Transactional
    public ManualQuestion update(String ownerSubject, UUID interviewId, UUID questionId,
            int order, String prompt, int maxScore, QuestionType type,
            List<String> options, List<String> correctAnswers) {
        editableDefinition(ownerSubject, interviewId);
        var question = ownedQuestion(interviewId, questionId);
        try {
            question.update(order, prompt, maxScore, type, options, correctAnswers);
            return question;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @Transactional
    public void delete(String ownerSubject, UUID interviewId, UUID questionId) {
        editableDefinition(ownerSubject, interviewId);
        questions.delete(ownedQuestion(interviewId, questionId));
    }

    @Transactional(readOnly = true)
    public List<ManualQuestion> list(String ownerSubject, UUID interviewId) {
        ownedDefinition(ownerSubject, interviewId);
        return questions.findByInterviewDefinitionIdOrderByOrderAsc(interviewId);
    }

    private com.onlineinterview.interview.domain.InterviewDefinition editableDefinition(
            String ownerSubject, UUID interviewId) {
        var definition = ownedDefinition(ownerSubject, interviewId);
        if (definition.getStatus() != InterviewStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Questions can only be changed on a draft");
        }
        return definition;
    }

    private ManualQuestion ownedQuestion(UUID interviewId, UUID questionId) {
        return questions.findById(questionId)
                .filter(question -> question.getInterviewDefinition().getId().equals(interviewId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Question not found"));
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
