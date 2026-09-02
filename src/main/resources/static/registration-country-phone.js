(() => {
  "use strict";

  const countrySelect = document.getElementById("country");
  const phoneInput = document.getElementById("phone");
  const dialCodeElement = document.getElementById("dialCode");

  if (!countrySelect || !phoneInput || !dialCodeElement) return;

  function updateSelectedCountry() {
    const selected = countrySelect.selectedOptions[0];
    dialCodeElement.textContent = selected?.dataset.code || "+91";
    phoneInput.placeholder = selected?.dataset.iso === "in" ? "9938330784" : "Enter mobile number";
  }

  function populateCountries() {
    const countries = Array.isArray(window.MITRAA_COUNTRIES)
      ? [...window.MITRAA_COUNTRIES]
      : [];

    if (!countries.length) {
      updateSelectedCountry();
      return;
    }

    countries.sort((a, b) => a.name.localeCompare(b.name, "en", { sensitivity: "base" }));
    const fragment = document.createDocumentFragment();

    countries.forEach(country => {
      const option = document.createElement("option");
      const dialCode = String(country.code).replace(/\D/g, "");
      option.value = country.country;
      option.dataset.code = `+${dialCode}`;
      option.dataset.iso = country.iso.toLowerCase();
      option.textContent = `${country.country} (+${dialCode})`;
      option.selected = country.iso.toLowerCase() === "in";
      fragment.appendChild(option);
    });

    countrySelect.replaceChildren(fragment);
    updateSelectedCountry();
  }

  function sanitizePhone() {
    phoneInput.value = phoneInput.value.replace(/\D/g, "").slice(0, 15);
    phoneInput.setCustomValidity("");

    if (phoneInput.value && phoneInput.value.length < 6) {
      phoneInput.setCustomValidity("Mobile number must contain at least 6 digits.");
    }
  }

  countrySelect.addEventListener("change", updateSelectedCountry);
  phoneInput.addEventListener("input", sanitizePhone);
  phoneInput.addEventListener("paste", event => {
    event.preventDefault();
    phoneInput.value = event.clipboardData.getData("text").replace(/\D/g, "").slice(0, 15);
    sanitizePhone();
  });

  populateCountries();
})();
