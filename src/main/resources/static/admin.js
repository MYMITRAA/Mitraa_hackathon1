const byId = id => document.getElementById(id);

const esc = value => {
    const node = document.createElement("div");
    node.textContent = value ?? "";
    return node.innerHTML;
};

const when = value => {
    return value
        ? new Date(value).toLocaleString()
        : "—";
};

const link = value => {
    if (!value) {
        return "—";
    }

    try {
        const url = new URL(value);

        if (!["http:", "https:"].includes(url.protocol)) {
            return "Invalid URL";
        }

        return `
            <a href="${esc(url.href)}"
               target="_blank"
               rel="noopener">
                Open
            </a>
        `;
    } catch {
        return "Invalid URL";
    }
};

const pill = value => {
    const text = String(value || "—");
    let type = "warn";

    if (
        /CONFIRMED|PAID|SENT|SUBMITTED|CONSENTED|ACTIVE|VERIFIED/.test(text)
    ) {
        type = "ok";
    } else if (
        /FAILED|REJECTED|DISQUALIFIED|EXPIRED/.test(text)
    ) {
        type = "bad";
    }

    return `
        <span class="status-pill ${type}">
            ${esc(text)}
        </span>
    `;
};

const empty = (columns, message = "No records found.") => {
    return `
        <tr>
            <td class="empty-row" colspan="${columns}">
                ${esc(message)}
            </td>
        </tr>
    `;
};

let adminUsers = [];
let adminLoadErrors = 0;
let adminRefreshing = false;
let currentAdminRole = "";

function showError(id, columns, error) {
    adminLoadErrors += 1;

    const tableBody = byId(id);

    if (!tableBody) {
        return;
    }

    const message = String(
        error.message || "Unable to load records."
    );

    tableBody.innerHTML = empty(
        columns,
        message === "404" || /\(404\)/.test(message)
            ? "Admin API route is missing. Rebuild the backend with the current AdminController."
            : message
    );
}

async function loadAdminContext() {
    const account = await api("/api/auth/me");

    currentAdminRole = account.role || "";

    const isSuperAdmin =
        currentAdminRole === "SUPER_ADMIN";

    const administratorsPanel =
        byId("administrators");

    const administratorsNav =
        byId("administratorsNav");

    if (administratorsPanel) {
        administratorsPanel.hidden = !isSuperAdmin;
    }

    if (administratorsNav) {
        administratorsNav.hidden = !isSuperAdmin;
    }
}

async function loadMetrics() {
    const data = await api("/api/admin/metrics");

    Object.entries(data).forEach(([key, value]) => {
        document
            .querySelectorAll(`[data-metric="${key}"]`)
            .forEach(node => {
                node.textContent = value;
            });
    });
}

async function loadUsers() {
    try {
        adminUsers = await api("/api/admin/users");

        const tableBody = byId("userRows");

        if (!tableBody) {
            return;
        }

        tableBody.innerHTML = adminUsers.length
            ? adminUsers.map(user => `
                <tr>
                    <td>${user.id}</td>

                    <td>
                        ${esc(user.fullName)}
                        <small>${esc(user.email)}</small>
                    </td>

                    <td>${pill(user.role)}</td>

                    <td>
                        ${
                            user.emailVerified
                                ? pill("VERIFIED")
                                : pill("PENDING")
                        }
                    </td>

                    <td>
                        ${
                            user.enabled
                                ? pill("ACTIVE")
                                : pill("DISABLED")
                        }
                    </td>

                    <td>${when(user.createdAt)}</td>

                    <td>
                        <button
                            type="button"
                            class="btn btn-ghost"
                            data-edit-user="${user.id}">
                            Edit
                        </button>

                        <button
                            type="button"
                            class="btn btn-danger"
                            data-disable-user="${user.id}">
                            Disable
                        </button>
                    </td>
                </tr>
            `).join("")
            : empty(7);

    } catch (error) {
        showError("userRows", 7, error);
    }
}

async function loadAdministrators() {
    if (currentAdminRole !== "SUPER_ADMIN") {
        return;
    }

    try {
        const administrators = await api(
            "/api/admin/administrators"
        );

        const tableBody = byId("administratorRows");

        if (!tableBody) {
            return;
        }

        tableBody.innerHTML = administrators.length
            ? administrators.map(administrator => `
                <tr>
                    <td>${administrator.id}</td>

                    <td>
                        ${esc(administrator.fullName)}
                        <small>
                            ${esc(administrator.email)}
                        </small>
                    </td>

                    <td>
                        ${pill(administrator.role)}
                    </td>

                    <td>
                        ${
                            administrator.emailVerified
                                ? pill("VERIFIED")
                                : pill("PENDING")
                        }
                    </td>

                    <td>
                        ${
                            administrator.enabled
                                ? pill("ACTIVE")
                                : pill("DISABLED")
                        }
                    </td>

                    <td>
                        ${when(administrator.createdAt)}
                    </td>

                    <td>
                        ${
                            administrator.role === "SUPER_ADMIN"
                                ? `
                                    <span class="protected-admin">
                                        Protected
                                    </span>
                                `
                                : `
                                    <button
                                        type="button"
                                        class="btn btn-danger"
                                        data-remove-administrator="${administrator.id}">
                                        Remove admin
                                    </button>
                                `
                        }
                    </td>
                </tr>
            `).join("")
            : empty(
                7,
                "No administrators found."
            );

    } catch (error) {
        showError(
            "administratorRows",
            7,
            error
        );
    }
}

async function loadRegistrations() {
    try {
        const rows = await api(
            "/api/admin/registrations"
        );

        const tableBody =
            byId("registrationRows");

        if (!tableBody) {
            return;
        }

        tableBody.innerHTML = rows.length
            ? rows.map(registration => `
                <tr>
                    <td>
                        ${esc(registration.registrationId)}
                    </td>

                    <td>
                        ${esc(registration.name)}

                        <small>
                            ${esc(registration.email)}
                            • email
                            ${
                                registration.emailVerified
                                    ? "verified"
                                    : "pending"
                            }
                        </small>
                    </td>

                    <td>
                        ${esc(registration.dateOfBirth)}

                        <small>
                            Age ${registration.age}
                        </small>
                    </td>

                    <td>
                        ${esc(registration.phone)}
                    </td>

                    <td>
                        ${esc(registration.city)}

                        <small>
                            ${esc(registration.country)}
                        </small>
                    </td>

                    <td>
                        ${esc(registration.type)}

                        <small>
                            ${esc(registration.domain)}
                        </small>
                    </td>

                    <td>
                        ${pill(registration.eligibility)}
                    </td>

                    <td>
                        ${
                            registration.guardianRequired
                                ? `
                                    ${
                                        registration.guardianReceived
                                            ? pill("CONSENTED")
                                            : pill("PENDING")
                                    }

                                    <small>
                                        ${esc(registration.guardianName)}
                                        •
                                        ${esc(registration.guardianRelationship)}
                                        <br>

                                        ${esc(registration.guardianEmail)}
                                        •
                                        ${esc(registration.guardianPhone)}
                                        •
                                        ${esc(registration.guardianCountry)}
                                    </small>
                                `
                                : "Not required"
                        }
                    </td>

                    <td>
                        ${pill(registration.status)}
                    </td>

                    <td>
                        ${when(registration.createdAt)}

                        <small>
                            Active:
                            ${when(registration.activatedAt)}
                        </small>
                    </td>
                </tr>
            `).join("")
            : empty(10);

    } catch (error) {
        showError(
            "registrationRows",
            10,
            error
        );
    }
}

async function loadTeams() {
    try {
        const teams = await api(
            "/api/admin/teams"
        );

        const rows = [];

        teams.forEach(team => {
            rows.push(`
                <tr>
                    <td>
                        ${esc(team.teamName)}

                        <small>
                            ${esc(team.teamCode)}
                            • ${team.memberCount} people
                        </small>
                    </td>

                    <td>
                        ${esc(team.registrationId)}
                    </td>

                    <td>Leader</td>
                    <td>${esc(team.leader)}</td>
                    <td>${esc(team.leaderEmail)}</td>
                    <td>See registration</td>
                    <td>—</td>
                    <td>See registration</td>
                    <td>${pill(team.status)}</td>
                </tr>
            `);

            const members = Array.isArray(team.members)
                ? team.members
                : [];

            members.forEach(member => {
                rows.push(`
                    <tr>
                        <td>
                            ${esc(team.teamName)}

                            <small>
                                ${esc(team.teamCode)}
                            </small>
                        </td>

                        <td>
                            ${esc(team.registrationId)}
                        </td>

                        <td>Member</td>

                        <td>
                            ${esc(member.name)}
                        </td>

                        <td>
                            ${esc(member.email)}
                        </td>

                        <td>
                            ${esc(member.dateOfBirth)}

                            <small>
                                Age ${member.age}
                            </small>
                        </td>

                        <td>
                            ${esc(member.country)}
                        </td>

                        <td>
                            ${
                                member.age < 18
                                    ? (
                                        member.guardianConsent
                                            ? "Declared"
                                            : "Missing"
                                    )
                                    : "Not required"
                            }
                        </td>

                        <td>
                            ${pill(team.status)}
                        </td>
                    </tr>
                `);
            });
        });

        const tableBody = byId("teamRows");

        if (tableBody) {
            tableBody.innerHTML =
                rows.join("") || empty(9);
        }

    } catch (error) {
        showError("teamRows", 9, error);
    }
}

async function loadPayments() {
    try {
        const rows = await api(
            "/api/admin/payments"
        );

        const tableBody = byId("paymentRows");

        if (!tableBody) {
            return;
        }

        tableBody.innerHTML = rows.length
            ? rows.map(payment => `
                <tr>
                    <td>
                        ${esc(payment.registrationId)}

                        <small>
                            ${esc(payment.email)}
                        </small>
                    </td>

                    <td>
                        ${esc(payment.participant)}
                    </td>

                    <td>
                        ${esc(payment.currency)}
                        ${Number(payment.amount || 0).toFixed(2)}
                    </td>

                    <td>
                        ${pill(payment.status)}
                    </td>

                    <td>
                        ${esc(payment.orderId)}
                    </td>

                    <td>
                        ${esc(payment.paymentId || "—")}
                    </td>

                    <td>
                        ${when(payment.createdAt)}

                        <small>
                            Paid:
                            ${when(payment.paidAt)}
                        </small>
                    </td>
                </tr>
            `).join("")
            : empty(7);

    } catch (error) {
        showError(
            "paymentRows",
            7,
            error
        );
    }
}

async function loadSubmissions() {
    try {
        const rows = await api(
            "/api/admin/submissions"
        );

        const tableBody =
            byId("submissionRows");

        if (!tableBody) {
            return;
        }

        tableBody.innerHTML = rows.length
            ? rows.map(submission => `
                <tr>
                    <td>
                        ${esc(submission.registrationId)}
                    </td>

                    <td>
                        ${esc(submission.participant)}

                        <small>
                            ${esc(submission.email)}
                        </small>
                    </td>

                    <td>
                        ${esc(submission.entryType)}
                    </td>

                    <td>
                        ${esc(submission.projectName)}
                    </td>

                    <td>
                        ${esc(submission.technologyStack)}
                    </td>

                    <td>
                        ${link(submission.repositoryUrl)}
                    </td>

                    <td>
                        ${link(submission.demoUrl)}
                    </td>

                    <td>
                        ${pill(submission.status)}
                    </td>

                    <td>
                        ${when(submission.updatedAt)}

                        <small>
                            Submitted:
                            ${when(submission.submittedAt)}
                        </small>
                    </td>
                </tr>
            `).join("")
            : empty(9);

    } catch (error) {
        showError(
            "submissionRows",
            9,
            error
        );
    }
}

async function loadGuardians() {
    try {
        const rows = await api(
            "/api/admin/guardian-consents"
        );

        const tableBody = byId("guardianRows");

        if (!tableBody) {
            return;
        }

        tableBody.innerHTML = rows.length
            ? rows.map(guardian => `
                <tr>
                    <td>
                        ${esc(guardian.registrationId)}
                    </td>

                    <td>
                        ${esc(guardian.participant)}
                    </td>

                    <td>
                        ${esc(guardian.guardianName)}

                        <small>
                            ${esc(guardian.guardianEmail)}
                        </small>
                    </td>

                    <td>
                        ${pill(guardian.status)}
                    </td>

                    <td>
                        ${guardian.attempts}/5
                    </td>

                    <td>
                        ${when(guardian.expiresAt)}
                    </td>

                    <td>
                        ${when(guardian.consentedAt)}
                    </td>

                    <td>
                        ${esc(guardian.legalVersion || "—")}
                    </td>
                </tr>
            `).join("")
            : empty(8);

    } catch (error) {
        showError(
            "guardianRows",
            8,
            error
        );
    }
}

async function loadNotifications() {
    try {
        const rows = await api(
            "/api/admin/notifications"
        );

        const tableBody =
            byId("notificationRows");

        if (!tableBody) {
            return;
        }

        tableBody.innerHTML = rows.length
            ? rows.map(notification => `
                <tr>
                    <td>${notification.id}</td>

                    <td>
                        ${esc(notification.channel)}
                    </td>

                    <td>
                        ${esc(notification.recipient)}
                    </td>

                    <td>
                        ${esc(notification.eventType)}
                    </td>

                    <td>
                        ${esc(notification.subject)}
                    </td>

                    <td>
                        ${esc(notification.referenceId)}
                    </td>

                    <td>
                        ${pill(notification.status)}
                    </td>

                    <td>
                        ${notification.attempts}
                    </td>

                    <td>
                        ${esc(notification.lastError || "—")}
                    </td>

                    <td>
                        ${when(notification.createdAt)}
                    </td>
                </tr>
            `).join("")
            : empty(10);

    } catch (error) {
        showError(
            "notificationRows",
            10,
            error
        );
    }
}

async function loadAll() {
    const refreshButton =
        byId("refreshAll");

    const refreshStatus =
        byId("refreshStatus");

    if (adminRefreshing) {
        return;
    }

    adminRefreshing = true;
    adminLoadErrors = 0;

    if (refreshButton) {
        refreshButton.disabled = true;
        refreshButton.classList.add("is-loading");
        refreshButton.textContent = "Refreshing…";
    }

    if (refreshStatus) {
        refreshStatus.textContent =
            "Loading current database records…";

        refreshStatus.className =
            "refresh-status loading";
    }

    const loaders = [
        loadMetrics(),
        loadUsers(),
        loadRegistrations(),
        loadTeams(),
        loadPayments(),
        loadSubmissions(),
        loadGuardians(),
        loadNotifications()
    ];

    if (currentAdminRole === "SUPER_ADMIN") {
        loaders.push(loadAdministrators());
    }

    const results =
        await Promise.allSettled(loaders);

    const rejected = results.filter(
        result => result.status === "rejected"
    ).length;

    const failures =
        rejected + adminLoadErrors;

    const refreshedAt =
        new Date().toLocaleTimeString(
            [],
            {
                hour: "2-digit",
                minute: "2-digit",
                second: "2-digit"
            }
        );

    if (refreshButton) {
        refreshButton.disabled = false;
        refreshButton.classList.remove(
            "is-loading"
        );

        refreshButton.textContent =
            "Refresh all";
    }

    adminRefreshing = false;

    if (!refreshStatus) {
        return;
    }

    if (failures) {
        refreshStatus.textContent =
            `Refreshed at ${refreshedAt} • `
            + `${failures} section`
            + `${failures === 1 ? "" : "s"} `
            + "could not be loaded";

        refreshStatus.className =
            "refresh-status error";
    } else {
        refreshStatus.textContent =
            `All data refreshed ✓ ${refreshedAt}`;

        refreshStatus.className =
            "refresh-status success";

        const metrics =
            document.querySelector(
                ".admin-kpis"
            );

        metrics?.classList.add(
            "just-refreshed"
        );

        setTimeout(
            () => metrics?.classList.remove(
                "just-refreshed"
            ),
            700
        );
    }
}

document
    .querySelectorAll(".table-search")
    .forEach(input => {
        input.addEventListener(
            "input",
            () => {
                const query =
                    input.value
                        .trim()
                        .toLowerCase();

                document
                    .querySelectorAll(
                        `#${input.dataset.filter} tr`
                    )
                    .forEach(row => {
                        row.hidden =
                            Boolean(query)
                            && !row.textContent
                                .toLowerCase()
                                .includes(query);
                    });
            }
        );
    });

byId("refreshAll")?.addEventListener(
    "click",
    loadAll
);

byId("addAdministrator")?.addEventListener(
    "click",
    () => {
        byId("administratorForm")?.reset();

        const status =
            byId("administratorFormStatus");

        if (status) {
            status.textContent = "";
        }

        byId("administratorDialog")
            ?.showModal();
    }
);

byId("cancelAdministrator")?.addEventListener(
    "click",
    () => {
        byId("administratorDialog")?.close();
    }
);

byId("administratorForm")?.addEventListener(
    "submit",
    async event => {
        event.preventDefault();

        const status =
            byId("administratorFormStatus");

        const emailInput =
            byId("administratorEmail");

        if (!emailInput) {
            return;
        }

        if (status) {
            status.textContent =
                "Adding administrator…";
        }

        try {
            await api(
                "/api/admin/administrators",
                {
                    method: "POST",
                    body: JSON.stringify({
                        email:
                            emailInput.value.trim()
                    })
                }
            );

            byId("administratorDialog")
                ?.close();

            await Promise.all([
                loadAdministrators(),
                loadUsers()
            ]);

        } catch (error) {
            if (status) {
                status.textContent =
                    error.message;
            }
        }
    }
);

byId("administratorRows")?.addEventListener(
    "click",
    async event => {
        const button = event.target.closest(
            "[data-remove-administrator]"
        );

        if (!button) {
            return;
        }

        const confirmed = confirm(
            "Remove this administrator? "
            + "The account will remain active "
            + "as a participant."
        );

        if (!confirmed) {
            return;
        }

        button.disabled = true;

        try {
            await api(
                `/api/admin/administrators/${button.dataset.removeAdministrator}`,
                {
                    method: "DELETE"
                }
            );

            await Promise.all([
                loadAdministrators(),
                loadUsers()
            ]);

        } catch (error) {
            alert(error.message);
            button.disabled = false;
        }
    }
);

byId("addUser")?.addEventListener(
    "click",
    () => {
        byId("userForm")?.reset();

        if (byId("userId")) {
            byId("userId").value = "";
        }

        if (byId("userEnabled")) {
            byId("userEnabled").checked = true;
        }

        if (byId("userVerified")) {
            byId("userVerified").checked = true;
        }

        if (byId("userFormStatus")) {
            byId("userFormStatus").textContent = "";
        }

        byId("userDialog")?.showModal();
    }
);

byId("cancelUser")?.addEventListener(
    "click",
    () => {
        byId("userDialog")?.close();
    }
);

byId("userRows")?.addEventListener(
    "click",
    async event => {
        const editButton = event.target.closest(
            "[data-edit-user]"
        );

        const disableButton = event.target.closest(
            "[data-disable-user]"
        );

        const editId =
            editButton?.dataset.editUser;

        const disableId =
            disableButton?.dataset.disableUser;

        if (editId) {
            const user = adminUsers.find(
                item =>
                    String(item.id) ===
                    String(editId)
            );

            if (!user) {
                return;
            }

            byId("userId").value =
                user.id;

            byId("userName").value =
                user.fullName;

            byId("userEmail").value =
                user.email;

            byId("userRole").value =
                user.role;

            byId("userEnabled").checked =
                user.enabled;

            byId("userVerified").checked =
                user.emailVerified;

            byId("userPassword").value = "";

            byId("userFormStatus").textContent =
                "";

            byId("userDialog")?.showModal();
        }

        if (
            disableId
            && confirm(
                "Disable this user? Their "
                + "registration, payment and "
                + "audit records will be retained."
            )
        ) {
            try {
                await api(
                    `/api/admin/users/${disableId}`,
                    {
                        method: "DELETE"
                    }
                );

                await loadUsers();

                if (
                    currentAdminRole ===
                    "SUPER_ADMIN"
                ) {
                    await loadAdministrators();
                }

            } catch (error) {
                alert(error.message);
            }
        }
    }
);

byId("userForm")?.addEventListener(
    "submit",
    async event => {
        event.preventDefault();

        const id =
            byId("userId").value;

        const body = {
            fullName:
                byId("userName").value.trim(),

            email:
                byId("userEmail").value.trim(),

            role:
                byId("userRole").value,

            enabled:
                byId("userEnabled").checked,

            emailVerified:
                byId("userVerified").checked
        };

        if (id) {
            body.newPassword =
                byId("userPassword").value
                || null;
        } else {
            body.password =
                byId("userPassword").value;
        }

        try {
            await api(
                `/api/admin/users${id ? "/" + id : ""}`,
                {
                    method:
                        id ? "PUT" : "POST",

                    body:
                        JSON.stringify(body)
                }
            );

            byId("userDialog")?.close();

            await loadUsers();

            if (
                currentAdminRole ===
                "SUPER_ADMIN"
            ) {
                await loadAdministrators();
            }

        } catch (error) {
            const status =
                byId("userFormStatus");

            if (status) {
                status.textContent =
                    error.message;
            }
        }
    }
);

byId("adminLogout")?.addEventListener(
    "click",
    async () => {
        try {
            await api(
                "/api/auth/logout",
                {
                    method: "POST"
                }
            );
        } catch (error) {
            console.error(
                "Logout request failed:",
                error
            );
        } finally {
            window.location.href =
                "/login.html";
        }
    }
);

async function initializeAdmin() {
    try {
        await loadAdminContext();
        await loadAll();

    } catch (error) {
        const status =
            byId("refreshStatus");

        if (status) {
            status.textContent =
                error.message;

            status.className =
                "refresh-status error";
        }
    }
}

initializeAdmin();