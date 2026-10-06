package com.careerforge.v2.learning;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.*;

@Component
public class AssessmentBank {
    public record Item(String prompt,List<String> options,int correctOptionIndex,String explanation) {}
    public record StoredQuestion(String id,String prompt,List<String> options,int correctOptionIndex,String explanation) {}
    private final Map<String,List<Item>> bank;
    private final ObjectMapper mapper;
    public AssessmentBank(ObjectMapper mapper) throws IOException {
        this.mapper=mapper;
        try(var input=new ClassPathResource("assessments.json").getInputStream()) {
            bank=mapper.readValue(input,new TypeReference<Map<String,List<Item>>>() {});
        }
        bank.forEach((name,items)-> {
            if(items.size()<3 || items.size()>5) throw new IllegalStateException("Invalid quiz size: "+name);
            for(Item q:items) {
                if(q.prompt()==null || q.prompt().isBlank() || q.explanation()==null || q.explanation().isBlank()
                        || q.options()==null || q.options().size()<2 || q.options().stream().anyMatch(s->s==null || s.isBlank())
                        || new HashSet<>(q.options()).size()!=q.options().size()
                        || q.correctOptionIndex()<0 || q.correctOptionIndex()>=q.options().size())
                    throw new IllegalStateException("Invalid quiz question: "+name);
            }
        });
    }
    public List<Item> questions(String name) { return bank.getOrDefault(name,List.of()); }
    public List<StoredQuestion> snapshot(String name) {
        var result=new ArrayList<StoredQuestion>();
        for(Item q:questions(name)) result.add(new StoredQuestion(UUID.randomUUID().toString(),q.prompt(),q.options(),q.correctOptionIndex(),q.explanation()));
        Collections.shuffle(result);
        return result;
    }
    public String encode(List<StoredQuestion> questions) {
        try { return mapper.writeValueAsString(questions); }
        catch(IOException e) { throw new IllegalStateException("Cannot persist quiz",e); }
    }
    public List<StoredQuestion> decode(String json) {
        try { return mapper.readValue(json,new TypeReference<List<StoredQuestion>>() {}); }
        catch(IOException e) { throw new IllegalStateException("Cannot read quiz",e); }
    }
}
