package com.onlineinterview.interview.domain;

import com.onlineinterview.session.domain.QuestionType;
import java.util.Map;

public record QuestionComposition(
        int mcqSingle, int mcqMultiple, int shortText, int longText) {

    public QuestionComposition {
        if (mcqSingle < 0 || mcqMultiple < 0 || shortText < 0 || longText < 0) {
            throw new IllegalArgumentException("Question-type counts cannot be negative");
        }
    }

    public int total() {
        return mcqSingle + mcqMultiple + shortText + longText;
    }

    public int count(QuestionType type) {
        return switch (type) {
            case MCQ_SINGLE -> mcqSingle;
            case MCQ_MULTIPLE -> mcqMultiple;
            case SHORT_TEXT -> shortText;
            case LONG_TEXT -> longText;
        };
    }

    public Map<QuestionType, Integer> asMap() {
        return Map.of(
                QuestionType.MCQ_SINGLE, mcqSingle,
                QuestionType.MCQ_MULTIPLE, mcqMultiple,
                QuestionType.SHORT_TEXT, shortText,
                QuestionType.LONG_TEXT, longText);
    }

    public static QuestionComposition allLongText(int questionCount) {
        return new QuestionComposition(0, 0, 0, questionCount);
    }
}
