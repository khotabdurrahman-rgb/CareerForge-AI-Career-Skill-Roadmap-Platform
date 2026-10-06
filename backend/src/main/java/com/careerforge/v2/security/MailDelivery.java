package com.careerforge.v2.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.Map;

@Component
@EnableAsync
public class MailDelivery {
    private static final Logger LOG=LoggerFactory.getLogger(MailDelivery.class);
    private final ObjectProvider<JavaMailSender> sender;
    private final String mode,host,from,directory;
    public MailDelivery(ObjectProvider<JavaMailSender> sender,@Value("${careerforge.mail.mode:smtp}") String mode,
            @Value("${spring.mail.host:}") String host,@Value("${careerforge.mail.from:careerforge@localhost}") String from,
            @Value("${careerforge.mail.directory:./data/mail}") String directory) {
        this.sender=sender; this.mode=mode; this.host=host; this.from=from; this.directory=directory;
    }
    public boolean available() { return "file".equals(mode) || (!host.isBlank() && sender.getIfAvailable()!=null); }
    public Map<String,Object> status() {
        return Map.of("emailAvailable",available(),"localEmail","file".equals(mode),
                "message",available() ? ("file".equals(mode) ? "Local test mailbox enabled." : "Email delivery configured.")
                        : "Email delivery is unavailable until SMTP is configured.");
    }
    @Async
    public void deliver(String recipient,String subject,String body) {
        try {
            if("file".equals(mode)) {
                Path folder=Path.of(directory); Files.createDirectories(folder);
                String message="From: "+from+"\nTo: "+recipient+"\nSubject: "+subject+"\nContent-Type: text/plain; charset=UTF-8\n\n"+body;
                Files.writeString(folder.resolve(UUID.randomUUID()+".eml"),message,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            } else if(available()) {
                SimpleMailMessage message=new SimpleMailMessage();
                message.setFrom(from); message.setTo(recipient); message.setSubject(subject); message.setText(body);
                sender.getObject().send(message);
            }
        } catch(Exception error) {
            LOG.warn("An account email could not be delivered. Check the mail configuration.");
        }
    }
}
