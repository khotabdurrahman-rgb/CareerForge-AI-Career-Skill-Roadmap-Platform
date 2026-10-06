package com.careerforge.v2.extras;

import com.careerforge.model.User;
import com.careerforge.service.CareerService;
import com.careerforge.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static com.careerforge.v2.extras.ExtrasDtos.*;

@Service
public class ProgressService {
    private final ProgressEventRepository events;
    private final UserRepository users;
    private final EntityManager em;
    public ProgressService(ProgressEventRepository events,UserRepository users,EntityManager em) {
        this.events=events; this.users=users; this.em=em;
    }

    @Transactional
    public void record(User user,String type,String title,double readiness) {
        if(type==null || type.isBlank() || type.length()>64 || title==null || title.isBlank() || title.length()>255
            || !Double.isFinite(readiness) || readiness<0 || readiness>100) throw CareerService.bad("Invalid progress event");
        User owner=(User)Hibernate.unproxy(user);
        ProgressEvent event=new ProgressEvent();
        event.userId=(Long)em.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(owner);
        event.type=type; event.title=title; event.readiness=readiness;
        event.occurredAt=Instant.now(); event.careerId=owner.careerId;
        events.save(event);
    }

    @Transactional(readOnly=true)
    public Progress history(User user) {
        User owner=owner(user);
        ZoneId zone;
        try { zone=ZoneId.of(owner.timezone==null ? "Asia/Kolkata" : owner.timezone); }
        catch(DateTimeException ex) { zone=ZoneId.of("Asia/Kolkata"); }
        List<ProgressEvent> latest=events.findTop500ByUserIdOrderByOccurredAtDescIdDesc(owner.id);
        List<EventView> views=latest.stream().map(e->new EventView(e.id,e.type,e.title,e.readiness,e.occurredAt,e.careerId)).toList();
        Set<LocalDate> days=new HashSet<>();
        Map<String,TrendPoint> daily=new LinkedHashMap<>();
        for(ProgressEvent e:latest) {
            LocalDate date=e.occurredAt.atZone(zone).toLocalDate();
            days.add(date);
            daily.putIfAbsent(date+"/"+e.careerId,new TrendPoint(date.toString(),e.readiness,e.careerId));
        }
        LocalDate cursor=LocalDate.now(zone);
        if(!days.contains(cursor)) cursor=cursor.minusDays(1);
        int streak=0;
        while(days.contains(cursor)) { streak++; cursor=cursor.minusDays(1); }
        List<TrendPoint> trend=new ArrayList<>(daily.values());
        trend.sort(Comparator.comparing(TrendPoint::date).thenComparing(TrendPoint::careerId,Comparator.nullsFirst(Long::compareTo)));
        return new Progress(views,trend,streak,zone.getId(),
            "Consecutive local dates with recorded activity, ending today or yesterday; derived from the latest 500 events. "
            +"Readiness reflects declared learning, not assessed proficiency. Capstone learning activity counts toward the streak but does not change skill readiness.");
    }
    private User owner(User user) {
        Long id=(Long)em.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(user);
        return (User)Hibernate.unproxy(users.findById(id).orElseThrow(()->CareerService.missing("User not found")));
    }
}
