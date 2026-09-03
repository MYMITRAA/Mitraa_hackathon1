(() => {
  "use strict";

  const byId = id => document.getElementById(id);

  document
    .querySelectorAll("[data-password-toggle]")
    .forEach(button => {
      button.addEventListener("click", () => {
        const input = byId(button.dataset.passwordToggle);

        if (!input) {
          return;
        }

        const isVisible = input.type === "text";

        input.type = isVisible ? "password" : "text";
        button.textContent = isVisible ? "Show" : "Hide";

        button.setAttribute(
          "aria-label",
          `${isVisible ? "Show" : "Hide"} password`
        );
      });
    });

  const form = byId("changePasswordForm");
  const status = byId("changePasswordStatus");

  if (!form || !status) {
    return;
  }

  form.addEventListener("submit", async event => {
    event.preventDefault();

    const oldPassword = byId("oldPassword").value;
    const newPassword = byId("newPassword").value;
    const confirmPassword = byId("confirmPassword").value;

    status.classList.remove("success", "error");

    if (newPassword.length < 8 || newPassword.length > 12) {
      status.textContent =
        "New password must contain 8–12 characters.";
      status.classList.add("error");
      return;
    }

    if (newPassword !== newPassword.trim()
        || confirmPassword !== confirmPassword.trim()) {
      status.textContent =
        "Password must not start or end with a space.";
      status.classList.add("error");
      return;
    }

    if (oldPassword === newPassword) {
      status.textContent =
        "New password must be different from the current password.";
      status.classList.add("error");
      return;
    }

    if (newPassword !== confirmPassword) {
      status.textContent =
        "New password and confirm password do not match.";
      status.classList.add("error");
      return;
    }

    const submitButton = form.querySelector(
      "button[type='submit']"
    );

    submitButton.disabled = true;
    status.textContent = "Updating password…";

    try {
      const result = await api("/api/auth/change-password", {
        method: "POST",
        body: JSON.stringify({
          oldPassword,
          newPassword,
          confirmPassword
        })
      });

      form.reset();

      document
        .querySelectorAll("[data-password-toggle]")
        .forEach(button => {
          const input = byId(button.dataset.passwordToggle);

          if (input) {
            input.type = "password";
          }

          button.textContent = "Show";
        });

      status.textContent =
        result.message ||
        "Password changed successfully. Redirecting to login...";

      status.classList.add("success");

      setTimeout(() => {
        window.location.replace("/login.html?passwordChanged=true");
      }, 1500);

    } catch (error) {
      status.textContent =
        error.message || "Unable to change password.";

      status.classList.add("error");
      submitButton.disabled = false;
    }
  });

  api("/api/auth/me")
    .then(user => {
      if (["ADMIN", "SUPER_ADMIN"].includes(user.role)) {
        const backLink = byId("changePasswordBack");

        if (backLink) {
          backLink.href = "/admin.html";
          backLink.textContent = "← Back to command center";
        }
      }
    })
    .catch(() => {
      window.location.replace("/login.html");
    });
})();
