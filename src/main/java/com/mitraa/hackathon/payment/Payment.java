package com.mitraa.hackathon.payment;
import com.mitraa.hackathon.registration.Registration;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="payments",uniqueConstraints=@UniqueConstraint(name="uk_payment_order",columnNames="gatewayOrderId"))
public class Payment {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="registration_id") private Registration registration;
 @Column(nullable=false) private long amountMinor;
 @Column(nullable=false,length=3) private String currency="USD";
 @Column(nullable=false,length=80,unique=true) private String gatewayOrderId;
 @Column(length=80,unique=true) private String gatewayPaymentId;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private PaymentStatus status=PaymentStatus.CREATED;
 @Column(nullable=false,updatable=false) private Instant createdAt=Instant.now();
 private Instant paidAt;
 public Long getId(){return id;} public Registration getRegistration(){return registration;} public void setRegistration(Registration v){registration=v;}
 public long getAmountMinor(){return amountMinor;} public void setAmountMinor(long v){amountMinor=v;} public String getCurrency(){return currency;} public void setCurrency(String v){currency=v;}
 public String getGatewayOrderId(){return gatewayOrderId;} public void setGatewayOrderId(String v){gatewayOrderId=v;} public String getGatewayPaymentId(){return gatewayPaymentId;} public void setGatewayPaymentId(String v){gatewayPaymentId=v;}
 public PaymentStatus getStatus(){return status;} public void setStatus(PaymentStatus v){status=v;} public Instant getCreatedAt(){return createdAt;} public Instant getPaidAt(){return paidAt;} public void setPaidAt(Instant v){paidAt=v;}
}
