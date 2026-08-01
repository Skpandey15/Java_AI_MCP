package com.onlineinterview.notification;

import com.onlineinterview.review.infrastructure.CoachingFeedbackStore;
import com.onlineinterview.session.domain.InterviewSession;
import com.onlineinterview.session.domain.ResultOutcome;
import com.onlineinterview.session.domain.ReviewStatus;
import com.onlineinterview.session.domain.SessionState;
import com.onlineinterview.session.infrastructure.InterviewSessionRepository;
import com.onlineinterview.session.infrastructure.ManualQuestionRepository;
import com.onlineinterview.profile.infrastructure.UserProfileRepository;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Emails the finalized interview result to the candidate: outcome, score, the approved
 *  coaching plan as improvement areas, and a pass -> next-round / fail -> graceful message. */
@Service
public class CandidateResultMailService {
    private static final Logger log = LoggerFactory.getLogger(CandidateResultMailService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final InterviewSessionRepository sessions;
    private final UserProfileRepository profiles;
    private final ManualQuestionRepository questions;
    private final CoachingFeedbackStore coaching;
    private final boolean enabled;
    private final String from;
    private final String fromName;
    private final String username;

    public CandidateResultMailService(ObjectProvider<JavaMailSender> mailSender,
            InterviewSessionRepository sessions, UserProfileRepository profiles,
            ManualQuestionRepository questions, CoachingFeedbackStore coaching,
            @Value("${app.mail.enabled:false}") boolean enabled,
            @Value("${app.mail.from:}") String from,
            @Value("${app.mail.from-name:Interview Team}") String fromName,
            @Value("${spring.mail.username:}") String username) {
        this.mailSender = mailSender;
        this.sessions = sessions;
        this.profiles = profiles;
        this.questions = questions;
        this.coaching = coaching;
        this.enabled = enabled;
        this.from = from;
        this.fromName = fromName;
        this.username = username;
    }

    /** Sends the result email and returns the recipient address. */
    @Transactional(readOnly = true)
    public String sendResult(String ownerSubject, UUID sessionId) {
        var sender = mailSender.getIfAvailable();
        String fromAddress = from == null || from.isBlank() ? username : from;
        if (!enabled || sender == null || fromAddress == null || fromAddress.isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Result email is not configured. Set MAIL_USERNAME, MAIL_PASSWORD, MAIL_FROM "
                    + "and MAIL_ENABLED=true on the orchestrator.");
        }
        var session = sessions.findByIdAndAssignment_InterviewDefinition_OwnerSubject(
                        sessionId, ownerSubject)
                .filter(item -> item.getState() == SessionState.SUBMITTED)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Submission not found"));
        if (session.getReviewStatus() != ReviewStatus.REVIEWED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Finalize the review before emailing the result.");
        }
        var candidate = profiles.findById(session.getCandidateId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Candidate profile not found"));
        var coachingContent = coaching.find(sessionId)
                .filter(CoachingFeedbackStore.Coaching::approved)
                .map(CoachingFeedbackStore.Coaching::content)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Generate and approve the candidate coaching feedback first — it is used "
                        + "as the improvement plan in the email."));

        var definition = session.getAssignment().getInterviewDefinition();
        int maxScore = questions.findByInterviewDefinitionIdOrderByOrderAsc(definition.getId())
                .stream().mapToInt(q -> q.getMaxScore()).sum();
        Integer total = session.getTotalScore();
        int percentage = maxScore <= 0 || total == null ? 0 : (int) ((long) total * 100 / maxScore);
        boolean passed = session.getResultOutcome() == ResultOutcome.PASSED;

        String subject = (passed ? "Congratulations — " : "Your result — ")
                + definition.getTitle();
        String body = buildHtml(candidate.getDisplayName(), definition.getTitle(), passed,
                total == null ? 0 : total, maxScore, percentage,
                definition.getPassingPercentage(), coachingContent);

        try {
            MimeMessage message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(candidate.getEmail());
            helper.setSubject(subject);
            helper.setText(body, true);
            sender.send(message);
        } catch (MailException | jakarta.mail.MessagingException
                | UnsupportedEncodingException exception) {
            log.atError().addKeyValue("event", "result.email_failed")
                    .addKeyValue("sessionId", sessionId)
                    .setCause(exception).log("Result email failed");
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Could not send the email: " + exception.getMessage(), exception);
        }
        log.atInfo().addKeyValue("event", "result.emailed")
                .addKeyValue("sessionId", sessionId)
                .addKeyValue("outcome", passed ? "PASSED" : "NOT_SELECTED")
                .log("Result emailed to candidate");
        return candidate.getEmail();
    }

    private String buildHtml(String name, String title, boolean passed, int score, int maxScore,
            int percentage, int passingPercentage, String coachingMarkdown) {
        String outcomeColor = passed ? "#17643a" : "#8b2c2c";
        String outcomeLabel = passed ? "Selected — you passed" : "Not selected this time";
        String nextSteps = passed
                ? "<p><strong>Next round:</strong> Congratulations on clearing this round! "
                  + "You've advanced to the next stage of the process. Our team will reach out "
                  + "shortly with the details and scheduling for your next interview.</p>"
                : "<p>While you won't be moving forward on this occasion, please don't be "
                  + "discouraged. Your effort is genuinely appreciated, and the development plan "
                  + "below is meant to help you grow. We'd be glad to see you apply again in the "
                  + "future.</p>";
        return "<div style=\"font-family:Arial,Helvetica,sans-serif;color:#2b3a52;line-height:1.6;"
                + "max-width:640px;margin:0 auto\">"
                + "<p>Dear " + MarkdownHtml.escape(name) + ",</p>"
                + "<p>Thank you for taking the time to complete the <strong>"
                + MarkdownHtml.escape(title) + "</strong> interview. Here is your result.</p>"
                + "<div style=\"padding:16px 20px;border-radius:12px;color:#fff;background:"
                + outcomeColor + "\">"
                + "<div style=\"font-size:13px;text-transform:uppercase;letter-spacing:.05em;"
                + "opacity:.85\">Outcome</div>"
                + "<div style=\"font-size:22px;font-weight:700;margin:4px 0\">" + outcomeLabel
                + "</div>"
                + "<div>Score: " + score + " / " + maxScore + " (" + percentage + "%) · "
                + "Passing requirement " + passingPercentage + "%</div></div>"
                + nextSteps
                + "<h2 style=\"color:#10233f;border-bottom:2px solid #e6eefb;padding-bottom:6px\">"
                + "Your development plan</h2>"
                + "<div>" + MarkdownHtml.render(coachingMarkdown) + "</div>"
                + "<p style=\"margin-top:24px\">Warm regards,<br>" + MarkdownHtml.escape(fromName)
                + "</p></div>";
    }
}
