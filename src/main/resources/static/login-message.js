(() => {
  "use strict";

  const params = new URLSearchParams(window.location.search);
  const loginStatus = document.getElementById("loginStatus");

  if (!loginStatus) {
    return;
  }

  if (params.get("passwordChanged") === "true") {
    loginStatus.textContent =
      "Password changed successfully. Please sign in using your new password.";

    loginStatus.classList.remove("error");
    loginStatus.classList.add("success");

    history.replaceState(
      {},
      document.title,
      "/login.html"
    );

    return;
  }

  if (params.get("sessionExpired") === "true") {
    loginStatus.textContent =
      "Your session expired. Please sign in again.";

    loginStatus.classList.remove("success");
    loginStatus.classList.add("error");

    history.replaceState(
      {},
      document.title,
      "/login.html"
    );
  }
})();