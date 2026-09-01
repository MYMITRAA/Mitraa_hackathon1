package com.mitraa.hackathon.guardian;

public record GuardianVerifyRequest(
 String otp,
 boolean guardianAuthorityConfirmed,
 boolean participantDetailsConfirmed,
 boolean participationApproved,
 boolean termsAccepted,
 boolean privacyAccepted,
 boolean childSafetyAccepted,
 boolean rulesAccepted,
 boolean refundAccepted,
 boolean dataProcessingAccepted
) {
 public boolean allAccepted(){return guardianAuthorityConfirmed&&participantDetailsConfirmed&&participationApproved&&termsAccepted&&privacyAccepted&&childSafetyAccepted&&rulesAccepted&&refundAccepted&&dataProcessingAccepted;}
}
