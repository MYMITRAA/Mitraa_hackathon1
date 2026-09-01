package com.mitraa.hackathon.guardian;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
@RestController @RequestMapping("/api/guardian")
public class GuardianConsentController {
 private final GuardianConsentService service;
 public GuardianConsentController(GuardianConsentService s){service=s;}
 @PostMapping("/request") public Map<String,String> request(Authentication a){service.request(a.getName());return Map.of("message","A consent code was sent to the registered guardian email.");}
 @PostMapping("/verify") public Map<String,String> verify(Authentication a,@RequestBody GuardianVerifyRequest body,HttpServletRequest request){service.verify(a.getName(),body,request.getRemoteAddr(),request.getHeader("User-Agent"));return Map.of("message","Guardian consent verified. Payment is now unlocked.");}
 @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST) public Map<String,String> bad(IllegalArgumentException e){return Map.of("message",e.getMessage());}
}
