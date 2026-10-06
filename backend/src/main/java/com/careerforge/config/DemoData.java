package com.careerforge.config;

import com.careerforge.model.*;
import com.careerforge.service.*;
import com.careerforge.dto.Requests.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Component
public class DemoData implements CommandLineRunner {
    private final CareerService data;
    private final AuthService auth;
    private final boolean demo;
    public DemoData(CareerService data,AuthService auth,@Value("${careerforge.seed-demo:true}") boolean demo) {
        this.data=data; this.auth=auth; this.demo=demo;
    }
    @Override @Transactional
    public void run(String... args) {
        if(data.skills().count()==0) {
            for(String name:List.of("Java","HTML","CSS","JavaScript","Spring Boot","MySQL","REST API","Git","Python","Statistics","Pandas","NumPy","Machine Learning","SQL","Data Visualization","React","C","Data Structures")) data.skills().save(new Skill(name));
        }
        if(data.careers().count()==0) {
            createCareer("Full Stack Java Developer","Build complete web applications, from an intuitive interface to powerful Java services.","Java","HTML","CSS","JavaScript","Spring Boot","MySQL","REST API","Git");
            createCareer("Data Scientist","Turn complex data into meaningful insights with statistical thinking and machine learning.","Python","Statistics","Pandas","NumPy","Machine Learning","SQL","Data Visualization");
            createCareer("Frontend Developer","Craft fast, accessible web experiences that people love to use.","HTML","CSS","JavaScript","React","Git");
            createCareer("Backend Developer","Design reliable APIs and services that power modern applications.","Java","Spring Boot","MySQL","REST API","Git");
        }
        if(data.resources().count()==0) {
            String[][] refs={
                {"Java","Java learning hub","https://dev.java/learn/","Documentation"},
                {"HTML","HTML essentials","https://developer.mozilla.org/en-US/docs/Learn_web_development/Core/Structuring_content","Guide"},
                {"CSS","CSS fundamentals","https://developer.mozilla.org/en-US/docs/Learn_web_development/Core/Styling_basics","Guide"},
                {"JavaScript","JavaScript guide","https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide","Guide"},
                {"Spring Boot","Building a RESTful web service","https://spring.io/guides/gs/rest-service/","Tutorial"},
                {"MySQL","MySQL reference manual","https://dev.mysql.com/doc/refman/8.4/en/","Documentation"},
                {"REST API","HTTP overview","https://developer.mozilla.org/en-US/docs/Web/HTTP/Overview","Guide"},
                {"Git","Pro Git book","https://git-scm.com/book/en/v2","Book"},
                {"Python","Python tutorial","https://docs.python.org/3/tutorial/","Tutorial"},
                {"React","Learn React","https://react.dev/learn","Guide"},
                {"Statistics","Statistics handbook","https://www.itl.nist.gov/div898/handbook/","Book"},
                {"Pandas","Getting started with pandas","https://pandas.pydata.org/docs/getting_started/","Guide"},
                {"NumPy","NumPy fundamentals","https://numpy.org/doc/stable/user/absolute_beginners.html","Guide"},
                {"Machine Learning","Scikit-learn user guide","https://scikit-learn.org/stable/user_guide.html","Guide"},
                {"SQL","SQL tutorial","https://www.postgresql.org/docs/current/tutorial-sql.html","Tutorial"},
                {"Data Visualization","Matplotlib tutorials","https://matplotlib.org/stable/tutorials/","Tutorial"},
                {"C","GNU C language manual","https://www.gnu.org/software/c-intro-and-ref/manual/","Book"},
                {"Data Structures","OpenDSA","https://opendsa.org/","Guide"}
            };
            for(String[] ref:refs) data.saveResource(null,new ResourceInput(findSkill(ref[0]).id,ref[1],ref[2],ref[3]));
        }
        if(demo && data.users().count()==0) {
            Long careerId=data.careers().findAll().get(0).id;
            User student=new User(); student.name="Abdurrahman Khot"; student.email="student@careerforge.dev";
            student.passwordHash=auth.hash("Career123!"); student.course="Artificial Intelligence & Data Science";
            student.college="Rizvi College"; student.currentYear="Second Year"; student.careerId=careerId;
            data.users().save(student);
            for(String name:List.of("Java","HTML","CSS","Git","MySQL")) {
                UserSkill item=new UserSkill(); item.user=student; item.skill=findSkill(name);
                item.level=name.equals("Java") ? "Intermediate" : "Beginner"; data.userSkills().save(item);
            }
            var steps=data.generateRoadmap(student);
            if(!steps.isEmpty()) { steps.get(0).status="IN_PROGRESS"; data.roadmap().save(steps.get(0)); }
            data.saveProject(student,null,new ProjectInput("Smart Library System","A library management application with book search, lending workflows, and a MySQL database.","Java, MySQL","https://github.com/topics/library-management-system","COMPLETED"));
            data.saveProject(student,null,new ProjectInput("Personal Portfolio","A responsive portfolio showcasing projects and skills.","HTML, CSS, JavaScript","","COMPLETED"));
            data.saveProject(student,null,new ProjectInput("CareerForge","A career readiness platform with personalized learning roadmaps.","Java, Spring Boot, MySQL","","IN_PROGRESS"));
            User admin=new User(); admin.name="CareerForge Admin"; admin.email="admin@careerforge.dev";
            admin.passwordHash=auth.hash("Career123!"); admin.role="ADMIN"; admin.careerId=careerId;
            data.users().save(admin);
        }
    }
    private Skill findSkill(String name) { return data.skills().findByName(name).orElseThrow(); }
    private void createCareer(String name,String description,String... names) {
        Career career=new Career(); career.name=name; career.description=description;
        for(String skill:names) career.skills.add(findSkill(skill));
        data.careers().save(career);
    }
}
