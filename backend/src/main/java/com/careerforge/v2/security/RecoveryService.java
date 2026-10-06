package com.careerforge.v2.security;

import com.careerforge.model.User;
import com.careerforge.repository.UserRepository;
import com.careerforge.service.CareerService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.time.Instant;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class RecoveryService {
    private final UserRepository users;
    private final RecoveryTokenRepository tokens;
    private final MailDelivery mail;
    private final String baseUrl;
    private final SecureRandom random=new SecureRandom();
    public RecoveryService(UserRepository users,RecoveryTokenRepository tokens,MailDelivery mail,
                           @Value("${careerforge.base-url:http://localhost:8090}") String baseUrl) {
        this.users=users; this.tokens=tokens; this.mail=mail; this.baseUrl=baseUrl.replaceAll("/+$","");
    }
    public Map<String,Object> config() { return mail.status(); }
    public static Map<String,String> neutral() { return Map.of("message","If the address is eligible and email delivery is available, an email will arrive shortly."); }
    @Transactional
    public void request(String email,String purpose) {
        if(!mail.available()) return;
        users.findByEmail(email.trim().toLowerCase(Locale.ROOT)).ifPresent(user-> {
            if("VERIFY".equals(purpose) && user.emailVerified) return;
            tokens.findByUserIdAndPurposeAndUsedFalse(user.id,purpose).forEach(old->{old.used=true; tokens.save(old);});
            byte[] bytes=new byte[32]; random.nextBytes(bytes);
            String raw=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            RecoveryToken token=new RecoveryToken(); token.user=user; token.tokenHash=digest(raw);
            token.purpose=purpose; token.createdAt=Instant.now(); token.expiresAt=token.createdAt.plusSeconds("RESET".equals(purpose) ? 1800 : 86400);
            tokens.save(token);
            String route="RESET".equals(purpose) ? "reset-password" : "verify-email";
            String subject="RESET".equals(purpose) ? "Reset your CareerForge password" : "Verify your CareerForge email";
            String body="Hello "+user.name+",\n\n"+subject+":\n"+baseUrl+"/#"+route+"?token="+raw+
                    "\n\nThis link is single-use and expires "+("RESET".equals(purpose) ? "in 30 minutes." : "in 24 hours.")+
                    "\nIf you did not request this email, you can ignore it.";
            Runnable deliver=()->mail.deliver(user.email,subject,body);
            if(TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCommit() { deliver.run(); }
                });
            } else deliver.run();
        });
    }
    public static String digest(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private RecoveryToken consume(String raw,String purpose) {
        RecoveryToken token=tokens.findByTokenHash(digest(raw)).orElseThrow(()->CareerService.bad("This link is invalid or has expired."));
        if(token.used || !purpose.equals(token.purpose) || !token.expiresAt.isAfter(Instant.now()))
            throw CareerService.bad("This link is invalid or has expired.");
        token.used=true; tokens.save(token); return token;
    }
    @Transactional
    public void reset(String raw,String password) {
        if(password.getBytes(StandardCharsets.UTF_8).length>72) throw CareerService.bad("Password must be at most 72 UTF-8 bytes.");
        RecoveryToken token=consume(raw,"RESET");
        User user=token.user; user.passwordHash=new BCryptPasswordEncoder().encode(password); user.sessionVersion++;
        users.save(user);
        tokens.findByUserIdAndPurposeAndUsedFalse(user.id,"RESET").forEach(other->{other.used=true; tokens.save(other);});
    }
    @Transactional
    public void verify(String raw) {
        RecoveryToken token=consume(raw,"VERIFY"); User user=token.user; user.emailVerified=true; users.save(user);
    }
}
