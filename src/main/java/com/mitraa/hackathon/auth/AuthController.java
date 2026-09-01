package com.mitraa.hackathon.auth;

import com.mitraa.hackathon.registration.Registration;
import com.mitraa.hackathon.registration.RegistrationRepository;
import com.mitraa.hackathon.user.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
 private final AuthService auth; private final UserRepository users; private final RegistrationRepository registrations; private final AccountSecurityService security;
 public AuthController(AuthService a,UserRepository u,RegistrationRepository r,AccountSecurityService s){auth=a;users=u;registrations=r;security=s;}
 @PostMapping("/register")
 public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req){
   Registration r=auth.register(req);
   return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("registrationId",r.getRegistrationCode(),"email",r.getUser().getEmail(),"status",r.getStatus().name(),"message","Account created. Enter the OTP sent to your email to activate it."));
 }
 @PostMapping("/verify-email") public Map<String,String> verify(@RequestBody Map<String,String> body){security.verifyEmail(body.get("email"),body.get("otp"));return Map.of("message","Email verified. You can now sign in.");}
 @PostMapping("/resend-otp") public Map<String,String> resend(@RequestBody Map<String,String> body){security.resendVerification(body.get("email"));return generic();}
 @PostMapping("/forgot-password") public Map<String,String> forgot(@RequestBody Map<String,String> body){security.requestPasswordReset(body.get("email"));return generic();}
 @PostMapping("/reset-password") public Map<String,String> reset(@RequestBody Map<String,String> body){security.resetPassword(body.get("email"),body.get("otp"),body.get("password"));return Map.of("message","Password changed securely. You can now sign in.");}
 @PostMapping("/change-password")
 public Map<String,String> changePassword(Authentication authentication,@Valid @RequestBody ChangePasswordRequest body){
   if(authentication==null) throw new IllegalArgumentException("Sign in before changing your password.");
   auth.changePassword(authentication.getName(),body);
   return Map.of("message","Password changed successfully.");
 }
 private Map<String,String> generic(){return Map.of("message","If the account is eligible, a secure code has been sent by email.");}
 @GetMapping("/me")
 public ResponseEntity<?> me(Authentication authentication){
   if(authentication==null) return ResponseEntity.status(401).build();
   var u=users.findByEmailIgnoreCase(authentication.getName()).orElseThrow();
   var reg=registrations.findByUser(u).orElse(null);
   Map<String,Object> result=new java.util.LinkedHashMap<>();result.put("name",u.getFullName());result.put("email",u.getEmail());result.put("role",u.getRole().name());result.put("registrationId",reg==null?"":reg.getRegistrationCode());result.put("registrationStatus",reg==null?"":reg.getStatus().name());result.put("verificationStatus",reg==null?"":reg.getAgeVerificationStatus());result.put("guardianRequired",reg!=null&&reg.isGuardianConsentRequired());result.put("guardianReceived",reg!=null&&reg.isGuardianConsentReceived());result.put("guardianEmailMasked",reg==null?"":mask(reg.getGuardianEmail()));result.put("domain",reg==null?"":reg.getDomain());result.put("country",reg==null?"":reg.getCountry());result.put("participationType",reg==null?"":reg.getParticipationType().name());return ResponseEntity.ok(result);
 }
 private String mask(String email){if(email==null||!email.contains("@"))return "";int at=email.indexOf('@');return email.substring(0,Math.min(2,at))+"***"+email.substring(at);}
 @GetMapping("/csrf") public Map<String,String> csrf(CsrfToken token){return Map.of("headerName",token.getHeaderName(),"token",token.getToken());}
 @ExceptionHandler(IllegalArgumentException.class)
 public ResponseEntity<?> badRequest(IllegalArgumentException e){return ResponseEntity.badRequest().body(Map.of("message",e.getMessage()));}
}
