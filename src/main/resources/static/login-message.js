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

  if (params.get("paymentRequired") === "true") {
    loginStatus.textContent =
      "To login, please complete your payment first.";

    loginStatus.classList.remove("success");
    loginStatus.classList.add("error");

    const paymentLink = document.createElement("a");
    paymentLink.href = "/payment.html";
    paymentLink.className = "btn btn-primary btn-lg";
    paymentLink.textContent = "Complete Payment";
    paymentLink.style.display = "inline-block";
    paymentLink.style.marginTop = "12px";

    loginStatus.insertAdjacentElement("afterend", paymentLink);

    history.replaceState({}, document.title, "/login.html");
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