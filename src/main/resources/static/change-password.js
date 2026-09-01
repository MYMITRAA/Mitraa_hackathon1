(() => {
  "use strict";
  const byId = id => document.getElementById(id);
  document.querySelectorAll("[data-password-toggle]").forEach(button => {
    button.addEventListener("click", () => {
      const input = byId(button.dataset.passwordToggle);
      const showing = input.type === "text";
      input.type = showing ? "password" : "text";
      button.textContent = showing ? "Show" : "Hide";
      button.setAttribute("aria-label", `${showing ? "Show" : "Hide"} ${input.id.replace(/([A-Z])/g, " $1").toLowerCase()}`);
    });
  });

  const form = byId("changePasswordForm");
  const status = byId("changePasswordStatus");
  form.addEventListener("submit", async event => {
    event.preventDefault();
    const oldPassword = byId("oldPassword").value;
    const newPassword = byId("newPassword").value;
    const confirmPassword = byId("confirmPassword").value;
    if (oldPassword === newPassword) {
      status.textContent = "New password must be different from the current password.";
      return;
    }
    if (newPassword !== confirmPassword) {
      status.textContent = "New password and confirm password do not match.";
      return;
    }
    const submit = form.querySelector("button[type=submit]");
    submit.disabled = true;
    status.textContent = "Updating password…";
    try {
      const result = await api("/api/auth/change-password", {
        method: "POST",
        body: JSON.stringify({ oldPassword, newPassword, confirmPassword })
      });
      form.reset();
      document.querySelectorAll("[data-password-toggle]").forEach(button => button.textContent = "Show");
      status.textContent = result.message;
    } catch (error) {
      status.textContent = error.message;
    } finally {
      submit.disabled = false;
    }
  });

  api("/api/auth/me").then(user => {
    if (["ADMIN", "SUPER_ADMIN"].includes(user.role)) {
      const back = byId("changePasswordBack");
      back.href = "/admin.html";
      back.textContent = "← Back to command center";
    }
  }).catch(() => { window.location.href = "/login.html"; });
})();
