package com.careerforge.v2.learning;

import com.careerforge.model.User;
import com.careerforge.v2.extras.ProgressService;
import com.careerforge.service.CareerService;
import com.careerforge.v2.learning.LearningDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Transactional(readOnly=true)
public class AssessmentService {
    private final CareerService data;
    private final AssessmentBank bank;
    private final QuizSessionRepository sessions;
    private final AssessmentAttemptRepository attempts;
    private final ProgressService progress;
    public AssessmentService(CareerService data,AssessmentBank bank,QuizSessionRepository sessions,AssessmentAttemptRepository attempts,ProgressService progress) {
        this.data=data; this.bank=bank; this.sessions=sessions; this.attempts=attempts; this.progress=progress;
    }
    public Assessments overview(User user) {
        var history=attempts.findByUserIdOrderByCompletedAtDescIdDesc(user.id);
        var skills=data.skills().findAll().stream().sorted(Comparator.comparing(s->s.id)).map(s-> {
            var matching=history.stream().filter(a->a.skill.id.equals(s.id)).toList();
            Double best=matching.stream().mapToDouble(a->a.score).max().stream().boxed().findFirst().orElse(null);
            return new SkillSummary(s.id,s.name,bank.questions(s.name).size(),best,matching.size());
        }).toList();
        return new Assessments(skills,history.stream().map(this::view).toList());
    }
    @Transactional
    public Started start(User user,Long skillId) {
        var skill=data.skill(skillId);
        var questions=bank.snapshot(skill.name);
        if(questions.isEmpty()) throw CareerService.bad("No assessment is available for this skill");
        QuizSession session=new QuizSession();
        session.id=UUID.randomUUID().toString(); session.user=user; session.skill=skill;
        session.questionSet=bank.encode(questions); session.expiresAt=Instant.now().plus(60,ChronoUnit.MINUTES);
        sessions.save(session);
        record(user,"ASSESSMENT_STARTED","Started assessment: "+skill.name);
        return new Started(session.id,skill.id,skill.name,session.expiresAt,
                questions.stream().map(q->new Question(q.id(),q.prompt(),q.options())).toList());
    }
    @Transactional
    public Submitted submit(User user,String sessionId,Submission input) {
        var session=sessions.lockOwned(sessionId,user.id).orElseThrow(()->CareerService.missing("Quiz session not found"));
        if(session.submitted) throw new ResponseStatusException(HttpStatus.CONFLICT,"Quiz session has already been submitted");
        if(!Instant.now().isBefore(session.expiresAt)) throw new ResponseStatusException(HttpStatus.GONE,"Quiz session has expired");
        var questions=bank.decode(session.questionSet);
        if(input==null || input.answers()==null || input.answers().size()!=questions.size())
            throw CareerService.bad("Answer every question exactly once");
        Map<String,Integer> answers=new HashMap<>();
        for(Answer answer:input.answers()) {
            if(answer==null || answer.questionId()==null || answer.optionIndex()==null
                    || answers.putIfAbsent(answer.questionId(),answer.optionIndex())!=null)
                throw CareerService.bad("Answer IDs must be unique and complete");
        }
        for(var q:questions) {
            Integer option=answers.get(q.id());
            if(option==null || option<0 || option>=q.options().size()) throw CareerService.bad("Invalid question ID or option index");
        }
        var feedback=questions.stream().map(q->new Feedback(q.id(),answers.get(q.id())==q.correctOptionIndex(),q.correctOptionIndex(),q.explanation())).toList();
        AssessmentAttempt attempt=new AssessmentAttempt();
        attempt.user=user; attempt.skill=session.skill; attempt.sessionId=session.id;
        attempt.correct=(int)feedback.stream().filter(Feedback::correct).count(); attempt.total=questions.size();
        attempt.score=Math.round(attempt.correct*1000.0/attempt.total)/10.0;
        attempt.completedAt=Instant.now().truncatedTo(ChronoUnit.MICROS);
        session.submitted=true; sessions.save(session); attempts.save(attempt);
        record(user,"ASSESSMENT_COMPLETED","Assessment: "+session.skill.name+" scored "+attempt.score+"% (self-reported skills unchanged)");
        return new Submitted(view(attempt),feedback);
    }
    private void record(User user,String type,String title) {
        progress.record(user,type,title.substring(0,Math.min(title.length(),255)),((Number)data.gap(user,user.careerId).get("readiness")).doubleValue());
    }
    private Attempt view(AssessmentAttempt a) {
        return new Attempt(a.id,a.skill.id,a.skill.name,a.score,a.correct,a.total,a.completedAt);
    }
}
