package com.mitraa.hackathon.notification;
import com.mitraa.hackathon.invoice.InvoiceService;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
@Component
public class NotificationDispatcher {
 private final NotificationOutboxRepository outbox; private final JavaMailSender mailSender; private final InvoiceService invoices;
 @Value("${app.notifications.from-email}") private String fromEmail;
 public NotificationDispatcher(NotificationOutboxRepository o,JavaMailSender m,InvoiceService i){outbox=o;mailSender=m;invoices=i;}
 @Scheduled(fixedDelayString="${app.notifications.dispatch-ms:30000}") public void dispatch(){for(var n:outbox.findTop50ByStatusAndNextAttemptAtBeforeOrderByCreatedAt(NotificationStatus.PENDING,Instant.now())){try{if(n.getChannel()!=NotificationChannel.EMAIL)throw new IllegalStateException("Only email delivery is enabled.");sendEmail(n);n.setStatus(NotificationStatus.SENT);n.setLastError(null);outbox.save(n);}catch(Exception e){n.setAttempts(n.getAttempts()+1);String message=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();n.setLastError(message.substring(0,Math.min(490,message.length())));if(n.getAttempts()>=8)n.setStatus(NotificationStatus.FAILED);else n.setNextAttemptAt(Instant.now().plusSeconds(Math.min(3600,30L*(1L<<Math.min(6,n.getAttempts())))));outbox.save(n);}}}
 private void sendEmail(NotificationOutbox n)throws Exception{if(fromEmail==null||fromEmail.isBlank())throw new IllegalStateException("Email sender is not configured.");MimeMessage m=mailSender.createMimeMessage();MimeMessageHelper h=new MimeMessageHelper(m,true,StandardCharsets.UTF_8.name());h.setFrom(fromEmail);h.setTo(n.getRecipient());h.setSubject(n.getSubject());String body=n.getBody()==null?"":n.getBody();boolean html=body.stripLeading().toLowerCase().startsWith("<!doctype html");if(html)h.setText("Open this email in an HTML-capable client to view your MiTRAA message.",body);else h.setText(body,false);if("PAYMENT_CONFIRMED".equals(n.getEventType())){byte[] pdf=invoices.generatePdf(n.getReferenceId());h.addAttachment("MiTRAA-Invoice-"+n.getReferenceId()+".pdf",new ByteArrayResource(pdf));}mailSender.send(m);}
}
