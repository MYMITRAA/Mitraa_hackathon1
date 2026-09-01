(() => {
  "use strict";
  const panel = document.getElementById("ssoPanel");
  const status = document.getElementById("ssoStatus");
  if (!panel) return;

  const params = new URLSearchParams(window.location.search);
  if (params.get("ssoError") === "true" && status) {
    status.textContent = "Social sign-in could not be completed. Use the same verified email as your existing MiTRAA account.";
    status.classList.add("error");
  }

  fetch("/api/auth/sso/providers", { credentials: "same-origin" })
    .then(response => response.ok ? response.json() : Promise.reject(new Error("Unavailable")))
    .then(config => {
      if (!config.enabled) return;
      const enabled = new Set(config.providers || []);
      panel.querySelectorAll("[data-sso-provider]").forEach(button => {
        button.hidden = !enabled.has(button.dataset.ssoProvider);
      });
      panel.hidden = enabled.size === 0;
    })
    .catch(() => { panel.hidden = true; });
})();
