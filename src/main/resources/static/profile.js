(() => {
  "use strict";

  const form = document.getElementById("profileForm");
  if (!form) return;

  const nameInput = document.getElementById("profileName");
  const emailInput = document.getElementById("profileEmail");
  const phoneInput = document.getElementById("profilePhone");
  const phoneHelp = document.getElementById("profilePhoneHelp");
  const emailBadge = document.getElementById("emailBadge");
  const emailHelp = document.getElementById("emailHelp");
  const profileStatus = document.getElementById("profileStatus");
  const verificationPanel = document.getElementById("emailVerificationPanel");
  const pendingEmailText = document.getElementById("pendingEmailText");
  const otpInputs = [...document.querySelectorAll(".profile-otp-digit")];
  const otpStatus = document.getElementById("otpStatus");
  const verifyButton = document.getElementById("verifyProfileEmail");
  const resendButton = document.getElementById("resendProfileOtp");
  const saveButton = form.querySelector("button[type='submit']");
  let savedProfileState = "";

  function profilePayload() {
    return {
      fullName: nameInput.value.trim(),
      email: emailInput.value.trim().toLowerCase(),
      phone: phoneInput.value.trim()
    };
  }

  function profileState() {
    return JSON.stringify(profilePayload());
  }

  function syncSaveButton() {
    if (saveButton) {
      saveButton.disabled = !savedProfileState
        || profileState() === savedProfileState;
    }
  }

  function setStatus(element, message, error = false) {
    element.textContent = message || "";
    element.classList.toggle("error", error);
    element.classList.toggle("success", !error && Boolean(message));
  }

  function setNeutralStatus(element, message) {
    element.textContent = message || "";
    element.classList.remove("error", "success");
  }

  function render(profile) {
    nameInput.value = profile.fullName || "";
    emailInput.value = profile.pendingEmail || profile.email || "";
    phoneInput.value = profile.phone || "";
    phoneInput.dataset.country = profile.country || "";
    const indiaProfile = (profile.country || "").trim().toLowerCase() === "india";
    phoneInput.maxLength = indiaProfile ? 13 : 16;
    if (phoneHelp) {
      phoneHelp.textContent = indiaProfile
        ? "+91 is fixed automatically. Enter a maximum of 10 mobile digits."
        : "Use 6–15 digits. International + prefix is allowed.";
    }

    if (profile.emailVerificationPending) {
      emailBadge.textContent = "Verification pending";
      emailBadge.className = "verification-badge pending";
      emailHelp.textContent = `Current verified email: ${profile.email}`;
      pendingEmailText.textContent = profile.pendingEmail;
      verificationPanel.hidden = false;
    } else {
      emailBadge.textContent = profile.emailVerified ? "✓ Verified" : "Not verified";
      emailBadge.className = profile.emailVerified ? "verification-badge verified" : "verification-badge unverified";
      emailHelp.textContent = profile.emailVerified ? "This is your verified login email." : "This email has not been verified.";
      pendingEmailText.textContent = "";
      verificationPanel.hidden = true;
    }

    savedProfileState = profileState();
    syncSaveButton();
  }

  function otpValue() {
    return otpInputs.map(input => input.value).join("");
  }

  function clearOtp() {
    otpInputs.forEach(input => input.value = "");
  }

  async function loadProfile() {
    try {
      render(await api("/api/profile"));
    } catch (error) {
      setStatus(profileStatus, error.message, true);
      if (error.message.includes("session expired")) setTimeout(() => location.href = "/login.html", 800);
    }
  }

  phoneInput.addEventListener("input", () => {
    let value = phoneInput.value.replace(/[^0-9+]/g, "");
    if (value.includes("+")) value = (value.startsWith("+") ? "+" : "") + value.replace(/\+/g, "");
    const india = (phoneInput.dataset.country || "").toLowerCase() === "india";
    if (india) {
      const digits = value.replace(/\D/g, "");
      const national = value.startsWith("+91")
        ? digits.slice(2)
        : (digits.startsWith("91") && digits.length > 10 ? digits.slice(2) : digits);
      phoneInput.value = `+91${national.slice(0, 10)}`;
      phoneInput.setSelectionRange(phoneInput.value.length, phoneInput.value.length);
    } else {
      phoneInput.value = value.slice(0, 16);
    }
  });

  form.addEventListener("input", () => {
    syncSaveButton();
    if (profileState() !== savedProfileState) {
      setNeutralStatus(profileStatus, "You have unsaved changes.");
    }
  });

  otpInputs.forEach((input, index) => {
    input.addEventListener("input", event => {
      event.target.value = event.target.value.replace(/\D/g, "").slice(-1);
      if (event.target.value && index < otpInputs.length - 1) otpInputs[index + 1].focus();
    });

    input.addEventListener("keydown", event => {
      if (event.key === "Backspace" && !input.value && index > 0) otpInputs[index - 1].focus();
      if (event.key === "ArrowLeft" && index > 0) otpInputs[index - 1].focus();
      if (event.key === "ArrowRight" && index < otpInputs.length - 1) otpInputs[index + 1].focus();
    });

    input.addEventListener("paste", event => {
      event.preventDefault();
      const digits = event.clipboardData.getData("text").replace(/\D/g, "").slice(0, 6);
      digits.split("").forEach((digit, digitIndex) => {
        if (otpInputs[digitIndex]) otpInputs[digitIndex].value = digit;
      });
      if (digits.length) otpInputs[Math.min(digits.length, 6) - 1].focus();
    });
  });

  form.addEventListener("submit", async event => {
    event.preventDefault();

    if (!savedProfileState || profileState() === savedProfileState) {
      setNeutralStatus(profileStatus, "No profile changes to save.");
      syncSaveButton();
      return;
    }

    setStatus(profileStatus, "Saving profile…");
    if (saveButton) saveButton.disabled = true;
    try {
      const profile = await api("/api/profile", {
        method: "PUT",
        body: JSON.stringify(profilePayload())
      });
      render(profile);
      setStatus(profileStatus, profile.emailVerificationPending
        ? "Name and phone saved. Verify the OTP sent to your new email."
        : "Profile updated successfully. Redirecting to dashboard…");

      if (profile.emailVerificationPending) {
        otpInputs[0]?.focus();
      } else {
        setTimeout(() => {
          window.location.replace("/dashboard.html");
        }, 1000);
      }
    } catch (error) {
      setStatus(profileStatus, error.message, true);
      syncSaveButton();
    }
  });

  verifyButton.addEventListener("click", async () => {
    const otp = otpValue();
    if (!/^\d{6}$/.test(otp)) {
      setStatus(otpStatus, "Enter the complete six-digit OTP.", true);
      return;
    }
    verifyButton.disabled = true;
    setStatus(otpStatus, "Verifying email…");
    try {
      const result = await api("/api/profile/email/verify", {method: "POST", body: JSON.stringify({otp})});
      emailBadge.textContent = "✓ Verified";
      emailBadge.className = "verification-badge verified";
      verificationPanel.hidden = true;
      setStatus(profileStatus, result.message);
      setTimeout(() => location.href = "/login.html?emailChanged=true", 1800);
    } catch (error) {
      setStatus(otpStatus, error.message, true);
      verifyButton.disabled = false;
    }
  });

  resendButton.addEventListener("click", async () => {
    resendButton.disabled = true;
    setStatus(otpStatus, "Sending a new OTP…");
    try {
      const profile = await api("/api/profile/email/resend", {method: "POST"});
      render(profile);
      clearOtp();
      otpInputs[0]?.focus();
      setStatus(otpStatus, "A new OTP was sent to your pending email address.");
    } catch (error) {
      setStatus(otpStatus, error.message, true);
    } finally {
      resendButton.disabled = false;
    }
  });

  loadProfile();
})();
