document.head.insertAdjacentHTML(
    'beforeend',
    '<link rel="stylesheet" href="enhancements.css"><link rel="stylesheet" href="account.css"><link rel="stylesheet" href="team-builder.css">'
);

const $ = id => document.getElementById(id);

function ageFromDob(v) {
    const d = new Date(v);
    const n = new Date();

    let a = n.getFullYear() - d.getFullYear();

    if (
        n.getMonth() < d.getMonth() ||
        (n.getMonth() === d.getMonth() && n.getDate() < d.getDate())
    ) {
        a--;
    }

    return a;
}

function tone() {
    if (localStorage.getItem('mitraaSound') === 'off') return;

    const C = window.AudioContext || window.webkitAudioContext;
    if (!C) return;

    const c = new C();

    [523, 659, 784].forEach((f, i) => {
        const o = c.createOscillator();
        const g = c.createGain();

        o.frequency.value = f;

        g.gain.setValueAtTime(
            .001,
            c.currentTime + i * .08
        );

        g.gain.exponentialRampToValueAtTime(
            .06,
            c.currentTime + i * .08 + .01
        );

        g.gain.exponentialRampToValueAtTime(
            .001,
            c.currentTime + i * .08 + .15
        );

        o.connect(g).connect(c.destination);

        o.start(c.currentTime + i * .08);
        o.stop(c.currentTime + i * .08 + .16);
    });
}

function csrf() {
    const t = document.cookie
        .split('; ')
        .find(v => v.startsWith('XSRF-TOKEN='))
        ?.split('=')[1];

    return t
        ? { 'X-XSRF-TOKEN': decodeURIComponent(t) }
        : {};
}

async function api(url, opt = {}) {
    const r = await fetch(url, {
        ...opt,
        headers: {
            ...(opt.body
                ? { 'Content-Type': 'application/json' }
                : {}),
            ...csrf(),
            ...(opt.headers || {})
        }
    });

    let d = {};

    try {
        d = await r.json();
    } catch {}

    if (!r.ok) {
        throw new Error(
            d.message ||
            d.status ||
            `Request failed (${r.status})`
        );
    }

    return d;
}

function isIndia(v) {
    return [
        'india',
        'in',
        'ind',
        'bharat'
    ].includes(
        (v || '').trim().toLowerCase()
    );
}


/* =========================================================
   REGISTRATION PRICING
   ========================================================= */

function updatePricing() {
    if (!$('country') || !$('ptype')) return;

    const i = isIndia($('country').value);

    $('ptype').options[0].textContent =
        i
            ? 'Individual — ₹14.01'
            : 'Individual — $2.70';

    $('ptype').options[1].textContent =
        i
            ? 'Team — ₹19.93/team (min. 3)'
            : 'Team — $5.80/team (min. 3)';

    if ($('entryPriceNote')) {
        $('entryPriceNote').textContent =
            i
                ? 'India pricing selected • Payment currency: INR'
                : 'International pricing selected • Payment currency: USD';
    }

    document
        .querySelectorAll('[data-market-card]')
        .forEach(card => {
            card.classList.toggle(
                'active',
                card.dataset.marketCard ===
                (i ? 'india' : 'global')
            );
        });
}

if ($('country')) {
    ['input', 'change', 'blur'].forEach(eventName => {
        $('country').addEventListener(
            eventName,
            updatePricing
        );
    });

    updatePricing();
}


/* =========================================================
   PASSWORD TOGGLE
   ========================================================= */

document
    .querySelectorAll('[data-password-toggle]')
    .forEach(button => {
        button.addEventListener('click', () => {
            const input = $(button.dataset.passwordToggle);

            if (!input) return;

            const visible = input.type === 'text';

            input.type = visible
                ? 'password'
                : 'text';

            button.textContent = visible
                ? 'Show'
                : 'Hide';

            button.setAttribute(
                'aria-label',
                visible
                    ? 'Show password'
                    : 'Hide password'
            );
        });
    });


/* =========================================================
   OTP BOXES
   ========================================================= */

document
    .querySelectorAll('.otp-boxes')
    .forEach(group => {

        const boxes = [
            ...group.querySelectorAll('input')
        ];

        const target = $(
            group.dataset.otpTarget
        );

        const sync = () => {
            if (target) {
                target.value = boxes
                    .map(box => box.value)
                    .join('');
            }
        };

        boxes.forEach((box, index) => {

            box.addEventListener('input', () => {

                box.value = box.value
                    .replace(/\D/g, '')
                    .slice(-1);

                sync();

                if (
                    box.value &&
                    boxes[index + 1]
                ) {
                    boxes[index + 1].focus();
                }
            });

            box.addEventListener(
                'keydown',
                event => {

                    if (
                        event.key === 'Backspace' &&
                        !box.value &&
                        boxes[index - 1]
                    ) {
                        boxes[index - 1].focus();
                    }

                    if (
                        event.key === 'ArrowLeft' &&
                        boxes[index - 1]
                    ) {
                        boxes[index - 1].focus();
                    }

                    if (
                        event.key === 'ArrowRight' &&
                        boxes[index + 1]
                    ) {
                        boxes[index + 1].focus();
                    }
                }
            );

            box.addEventListener('paste', event => {

                const digits =
                    event.clipboardData
                        .getData('text')
                        .replace(/\D/g, '')
                        .slice(0, 6);

                if (!digits) return;

                event.preventDefault();

                digits
                    .split('')
                    .forEach((digit, i) => {
                        if (boxes[i]) {
                            boxes[i].value = digit;
                        }
                    });

                sync();

                boxes[
                    Math.min(digits.length, 6) - 1
                ].focus();
            });
        });
    });


/* =========================================================
   REGISTRATION
   ========================================================= */

if ($('dob')) {
    $('dob').addEventListener('change', () => {

        const a = ageFromDob(
            $('dob').value
        );

        const minor =
            a >= 10 && a < 18;

        $('guardianFields').hidden = !minor;
        $('guardianCommitment').hidden = !minor;

        [
            'guardianName',
            'guardianEmail',
            'guardianRelationship',
            'guardianPhone',
            'guardianCountry'
        ].forEach(id => {
            $(id).required = minor;
        });
    });
}

if ($('registerForm')) {

    $('registerForm').addEventListener(
        'submit',
        async e => {

            e.preventDefault();

            const s = $('registerStatus');

            const a = ageFromDob(
                $('dob').value
            );

            const password =
                $('password').value;

            if (password !== password.trim()) {
                s.textContent =
                    'Password must not start or end with a space.';
                return;
            }

            if (a < 10 || a > 35) {
                s.textContent =
                    'Eligibility failed: participants must be aged 10–35.';
                return;
            }

            if (
                a < 18 &&
                !$('guardian').checked
            ) {
                s.textContent =
                    'Confirm that the guardian will complete consent.';
                return;
            }

            if (
                a < 18 &&
                [
                    'guardianName',
                    'guardianEmail',
                    'guardianRelationship',
                    'guardianPhone',
                    'guardianCountry'
                ].some(
                    id => !$(id).value.trim()
                )
            ) {
                s.textContent =
                    'All guardian contact fields are required.';
                return;
            }

            if (
                password !==
                $('confirmPassword').value
            ) {
                s.textContent =
                    'Passwords do not match.';
                return;
            }

            const raw =
                $('phone').value
                    .replace(/[^0-9]/g, '');

            const code =
                $('country')
                    .selectedOptions[0]
                    ?.dataset.code || '';

            const india =
                isIndia(
                    $('country').value
                );

            if (
                raw.length < 6 ||
                raw.length > (india ? 10 : 15)
            ) {
                s.textContent =
                    india
                        ? 'Indian mobile number cannot exceed 10 digits.'
                        : 'Enter a valid mobile number.';
                return;
            }

            try {

                const d = await api(
                    '/api/auth/register',
                    {
                        method: 'POST',

                        body: JSON.stringify({

                            fullName:
                                $('name').value,

                            email:
                                $('email').value,

                            password,

                            dateOfBirth:
                                $('dob').value,

                            phone:
                                code + raw,

                            country:
                                $('country').value,

                            city:
                                $('city').value,

                            participationType:
                                $('ptype').value,

                            domain:
                                $('domain').value,

                            termsAccepted:
                                $('terms').checked,

                            privacyAccepted:
                                $('privacy').checked,

                            rulesAccepted:
                                $('rulesAccepted').checked,

                            guardianConsent:
                                a < 18
                                    ? $('guardian').checked
                                    : false,

                            guardianName:
                                a < 18
                                    ? $('guardianName').value
                                    : null,

                            guardianEmail:
                                a < 18
                                    ? $('guardianEmail').value
                                    : null,

                            guardianRelationship:
                                a < 18
                                    ? $('guardianRelationship').value
                                    : null,

                            guardianPhone:
                                a < 18
                                    ? $('guardianPhone').value
                                    : null,

                            guardianCountry:
                                a < 18
                                    ? $('guardianCountry').value
                                    : null,

                            marketingConsent:
                                $('marketing').checked
                        })
                    }
                );

                tone();

                s.textContent =
                    `Player ID ${d.registrationId} created. Redirecting to email verification…`;

                sessionStorage.setItem(
                    'pendingVerificationEmail',
                    d.email
                );

                setTimeout(
                    () => {
                        location.href =
                            '/verify-email.html';
                    },
                    900
                );

            } catch (x) {
                s.textContent = x.message;
            }
        }
    );
}


/* =========================================================
   LOGIN ERROR
   ========================================================= */

if (
    $('loginForm') &&
    new URLSearchParams(location.search)
        .has('error')
) {
    $('loginStatus').textContent =
        'Invalid credentials, disabled account, or unverified email.';
}


/* =========================================================
   TEAM BUILDER
   ========================================================= */

function memberRow(n) {

    return `
<fieldset
    class="member-card"
    data-member-number="${n}">

    <legend>Member ${n}</legend>

    <button
        type="button"
        class="remove-member"
        data-remove-member
        aria-label="Remove Member ${n}">
        Remove member
    </button>

    <div class="split">

        <div class="field">
            <label>Full name</label>

            <input
                data-member="fullName"
                required
                maxlength="120">
        </div>

        <div class="field">
            <label>Email</label>

            <input
                data-member="email"
                type="email"
                required>
        </div>

        <div class="field">
            <label>Date of birth</label>

            <input
                data-member="dateOfBirth"
                type="date"
                required>
        </div>

        <div class="field">
            <label>Country</label>

            <input
                data-member="country"
                value="India"
                required>
        </div>

    </div>

    <label>
        <input
            data-member="guardianConsent"
            type="checkbox">

        Guardian consent
        (required if under 18)
    </label>

</fieldset>`;
}

const MIN_ADDITIONAL_MEMBERS = 2;
const MAX_ADDITIONAL_MEMBERS = 4;

function syncTeamMemberControls() {

    const rows = [
        ...document.querySelectorAll(
            '#memberRows .member-card'
        )
    ];

    const add = $('addTeamMember');
    const count = $('teamMemberCount');

    rows.forEach((row, index) => {

        const number = index + 2;

        row.dataset.memberNumber =
            number;

        row.querySelector(
            'legend'
        ).textContent =
            `Member ${number}`;

        const remove =
            row.querySelector(
                '[data-remove-member]'
            );

        if (remove) {

            remove.hidden =
                rows.length <=
                MIN_ADDITIONAL_MEMBERS;

            remove.setAttribute(
                'aria-label',
                `Remove Member ${number}`
            );
        }
    });

    if (count) {
        count.textContent =
            `${rows.length + 1} of 5 members`;
    }

    if (add) {
        add.hidden =
            rows.length >=
            MAX_ADDITIONAL_MEMBERS;
    }
}

function initializeTeamRows() {

    const rows = $('memberRows');

    if (
        rows &&
        !rows.children.length
    ) {
        rows.insertAdjacentHTML(
            'beforeend',
            memberRow(2) +
            memberRow(3)
        );
    }

    syncTeamMemberControls();
}

function teamPayload() {

    return {

        teamName:
            $('teamName').value,

        members:
            [
                ...document.querySelectorAll(
                    '.member-card'
                )
            ].map(c => ({

                fullName:
                    c.querySelector(
                        '[data-member="fullName"]'
                    ).value,

                email:
                    c.querySelector(
                        '[data-member="email"]'
                    ).value,

                dateOfBirth:
                    c.querySelector(
                        '[data-member="dateOfBirth"]'
                    ).value,

                country:
                    c.querySelector(
                        '[data-member="country"]'
                    ).value,

                guardianConsent:
                    c.querySelector(
                        '[data-member="guardianConsent"]'
                    ).checked

            }))
    };
}

async function loadTeam(type) {

    if (!$('teamForm')) return;

    const teamSection =
        $('team');

    const teamNav =
        $('teamNav');

    const teamStep =
        document.querySelector(
            '[data-step="team"]'
        );

    /*
     * INDIVIDUAL REGISTRATION
     */
    if (type !== 'TEAM') {

        teamSection.hidden = true;

        if (teamNav) {
            teamNav.hidden = true;
        }

        if (teamStep) {
            teamStep.textContent =
                'INDIVIDUAL ✓';
        }

        return;
    }

    /*
     * TEAM REGISTRATION
     */
    teamSection.hidden = false;

    if (teamNav) {
        teamNav.hidden = false;
    }

    if (teamStep) {
        teamStep.textContent =
            'TEAM';
    }

    initializeTeamRows();

    try {

        const t =
            await api('/api/teams');

        if (t.configured) {

            $('teamForm').hidden = true;

            $('teamState').textContent =
                'SQUAD LOCKED';

            if (teamStep) {
                teamStep.textContent =
                    'TEAM ✓';
            }

            $('teamResult').innerHTML = `
<div class="success-card">

    <b>${t.teamName}</b>

    <span>
        Code ${t.teamCode}
        • ${t.memberCount} members
    </span>

</div>`;

        } else {

            $('teamState').textContent =
                'ACTION REQUIRED';
        }

    } catch (e) {

        $('teamStatus').textContent =
            e.message;
    }
}

if ($('teamForm')) {

    $('teamForm').addEventListener(
        'submit',
        async e => {

            e.preventDefault();

            try {

                const t =
                    await api(
                        '/api/teams',
                        {
                            method: 'POST',

                            body:
                                JSON.stringify(
                                    teamPayload()
                                )
                        }
                    );

                tone();

                $('teamStatus').textContent =
                    `Squad locked: ${t.teamCode}`;

                loadTeam('TEAM');

            } catch (x) {

                $('teamStatus').textContent =
                    x.message;
            }
        }
    );
}

if ($('addTeamMember')) {

    $('addTeamMember').addEventListener(
        'click',
        () => {

            const rows =
                $('memberRows');

            const current =
                rows.querySelectorAll(
                    '.member-card'
                ).length;

            if (
                current >=
                MAX_ADDITIONAL_MEMBERS
            ) {
                return;
            }

            rows.insertAdjacentHTML(
                'beforeend',
                memberRow(current + 2)
            );

            syncTeamMemberControls();

            rows.lastElementChild
                .querySelector(
                    '[data-member="fullName"]'
                )
                .focus();
        }
    );
}

if ($('memberRows')) {

    $('memberRows').addEventListener(
        'click',
        event => {

            const remove =
                event.target.closest(
                    '[data-remove-member]'
                );

            if (!remove) return;

            const rows =
                $('memberRows');

            if (
                rows.querySelectorAll(
                    '.member-card'
                ).length <=
                MIN_ADDITIONAL_MEMBERS
            ) {
                return;
            }

            remove
                .closest('.member-card')
                .remove();

            syncTeamMemberControls();
        }
    );
}


/* =========================================================
   POC
   ========================================================= */

const pocFields = [
    'projectName',
    'problemStatement',
    'solutionSummary',
    'technologyStack',
    'repositoryUrl',
    'demoUrl',
    'responsibleAi'
];

async function loadPoc() {

    if (!$('pocForm')) return;

    try {

        const p =
            await api('/api/submissions');

        $('pocState').textContent =
            p.status;

        const submitted =
            p.status !== 'DRAFT' &&
            p.status !== 'NOT_STARTED';

        const buildStep =
            document.querySelector(
                '[data-step="build"]'
            );

        if (buildStep) {
            buildStep.textContent =
                submitted
                    ? 'BUILD ✓'
                    : 'BUILD';
        }

        if (submitted) {

            $('pocForm').reset();

            pocFields.forEach(k => {
                $(k).value = '';
            });

            [
                ...$('pocForm').elements
            ].forEach(x => {
                x.disabled = true;
            });

        } else if (p.exists) {

            pocFields.forEach(k => {
                $(k).value =
                    p[k] || '';
            });
        }

    } catch (e) {

        $('pocStatus').textContent =
            e.message;
    }
}

function pocPayload() {

    return Object.fromEntries(
        pocFields.map(k => [
            k,
            $(k).value.trim()
        ])
    );
}

if ($('pocForm')) {

    $('pocForm').addEventListener(
        'submit',
        async e => {

            e.preventDefault();

            try {

                const p =
                    await api(
                        '/api/submissions',
                        {
                            method: 'PUT',

                            body:
                                JSON.stringify(
                                    pocPayload()
                                )
                        }
                    );

                tone();

                $('pocState').textContent =
                    p.status;

                $('pocStatus').textContent =
                    'Draft saved securely.';

            } catch (x) {

                $('pocStatus').textContent =
                    x.message;
            }
        }
    );
}

if ($('submitPoc')) {

    $('submitPoc').addEventListener(
        'click',
        async () => {

            const form =
                $('pocForm');

            if (!form.reportValidity()) {

                form.querySelector(
                    ':invalid'
                )?.focus();

                $('pocStatus').textContent =
                    'Complete all required POC fields before final submission.';

                return;
            }

            if (
                !$('repositoryUrl')
                    .value
                    .trim() ||
                !$('demoUrl')
                    .value
                    .trim()
            ) {

                $('pocStatus').textContent =
                    'Repository URL and Demo URL are required for final submission.';

                return;
            }

            if (
                !confirm(
                    'Final submission cannot be edited. Continue?'
                )
            ) {
                return;
            }

            const button =
                $('submitPoc');

            button.disabled = true;

            $('pocStatus').textContent =
                'Submitting POC securely…';

            try {

                const result =
                    await api(
                        '/api/submissions/submit',
                        {
                            method: 'POST',

                            body:
                                JSON.stringify(
                                    pocPayload()
                                )
                        }
                    );

                tone();

                $('pocState').textContent =
                    result.status;

                /*
                 * IMPORTANT:
                 * POC submission NO LONGER unlocks payment.
                 */
                $('pocStatus').textContent =
                    `POC submitted successfully at ${new Date(
                        result.submittedAt
                    ).toLocaleString()}.`;

                form.reset();

                pocFields.forEach(k => {
                    $(k).value = '';
                });

                await loadPoc();

                await refreshFlow();

            } catch (x) {

                button.disabled = false;

                $('pocStatus').textContent =
                    x.message;
            }
        }
    );
}


/* =========================================================
   PARTICIPANT FLOW
   ========================================================= */

async function refreshFlow() {

    const p =
        await api('/api/auth/me');

    const labels = {

        PENDING_VERIFICATION:
            'AGE ELIGIBILITY REQUIRED',

        PENDING_GUARDIAN:
            'GUARDIAN CONSENT REQUIRED',

        PENDING_SUBMISSION:
            'POC SUBMISSION REQUIRED',

        PENDING_PAYMENT:
            'PAYMENT REQUIRED',

        CONFIRMED:
            'REGISTRATION ACTIVE',

        CANCELLED:
            'CANCELLED',

        DISQUALIFIED:
            'NOT ELIGIBLE'
    };

    const userName =
        document.querySelector(
            '[data-user-name]'
        );

    if (userName) {
        userName.textContent =
            p.name;
    }

    const registrationId =
        document.querySelector(
            '[data-registration-id]'
        );

    if (registrationId) {
        registrationId.textContent =
            p.registrationId ||
            'Pending';
    }

    const registrationStatus =
        document.querySelector(
            '[data-registration-status]'
        );

    if (registrationStatus) {
        registrationStatus.textContent =
            labels[p.registrationStatus] ||
            p.registrationStatus ||
            'Pending';
    }

    const verificationStatus =
        document.querySelector(
            '[data-verification-status]'
        );

    if (verificationStatus) {
        verificationStatus.textContent =
            p.verificationStatus ===
            'DOB_VERIFIED'
                ? 'ELIGIBLE'
                : p.verificationStatus ||
                  'Not checked';
    }

    const entryType =
        document.querySelector(
            '[data-entry-type]'
        );

    if (entryType) {
        entryType.textContent =
            p.participationType ||
            '—';
    }


    /* AGE */

    const ageOk =
        p.verificationStatus ===
        'DOB_VERIFIED';

    $('ageState').textContent =
        ageOk
            ? 'ELIGIBLE ✓'
            : p.verificationStatus;


    /* PRICE */

    if ($('dashboardPrice')) {

        $('dashboardPrice').textContent =
            isIndia(p.country)
                ? `Your India entry: ${
                    p.participationType === 'TEAM'
                        ? '₹19.93/team'
                        : '₹14.01/individual'
                  } • INR payment`
                : `Your international entry: ${
                    p.participationType === 'TEAM'
                        ? '$5.80/team'
                        : '$2.70/individual'
                  } • USD payment`;
    }


    /*
     * FLOW STATES
     */

    const guardianOk =
        !p.guardianRequired ||
        p.guardianReceived;

    const paymentReady =
        p.registrationStatus ===
        'PENDING_PAYMENT';

    const active =
        p.registrationStatus ===
        'CONFIRMED';


    /* =====================================================
       GUARDIAN
       ===================================================== */

    const guardianSection =
        $('guardian');

    const guardianNav =
        $('guardianNav');

    const guardianStep =
        document.querySelector(
            '[data-step="guardian"]'
        );

    guardianSection.hidden =
        !p.guardianRequired;

    if (guardianNav) {
        guardianNav.hidden =
            !p.guardianRequired;
    }

    if (guardianStep) {

        guardianStep.hidden =
            !p.guardianRequired;

        guardianStep.textContent =
            p.guardianReceived
                ? 'GUARDIAN ✓'
                : 'GUARDIAN';
    }

    $('guardianState').textContent =
        p.guardianReceived
            ? 'VERIFIED'
            : ageOk
                ? 'ACTION REQUIRED'
                : 'LOCKED';

    $('guardianMasked').textContent =
        p.guardianEmailMasked ||
        'the protected address';

    $('requestGuardian').disabled =
        !ageOk ||
        p.guardianReceived;

    $('verifyGuardian').disabled =
        !ageOk ||
        p.guardianReceived;


    /* =====================================================
       PAYMENT
       ===================================================== */

    /*
     * Payment is now BEFORE Team / POC.
     *
     * PENDING_PAYMENT = payment can be completed.
     * CONFIRMED       = payment already completed.
     */

    if ($('payNow')) {

        $('payNow').disabled =
            !paymentReady || active;
    }

    if ($('paymentState')) {

        $('paymentState').textContent =
            active
                ? 'ACTIVE'
                : paymentReady
                    ? 'READY'
                    : 'LOCKED';
    }

    if ($('paymentStatus')) {

        if (active) {

            $('paymentStatus').textContent =
                'Payment confirmed. Registration is active.';

        } else if (paymentReady) {

            $('paymentStatus').textContent =
                'Complete your payment to activate your registration.';

        } else {

            $('paymentStatus').textContent =
                'Payment is not currently available for this registration.';
        }
    }


    /* =====================================================
       TOP FLOW STATUS
       ===================================================== */

    const ageStep =
        document.querySelector(
            '[data-step="age"]'
        );

    if (ageStep) {
        ageStep.textContent =
            ageOk
                ? 'AGE ✓'
                : 'AGE';
    }


    const paymentStep =
        document.querySelector(
            '[data-step="payment"]'
        );

    if (paymentStep) {
        paymentStep.textContent =
            active
                ? 'PAYMENT ✓'
                : 'PAYMENT';
    }


    const activeStep =
        document.querySelector(
            '[data-step="active"]'
        );

    if (activeStep) {
        activeStep.textContent =
            active
                ? 'ACTIVE ✓'
                : 'ACTIVE';
    }


    return p;
}


/* =========================================================
   PARTICIPANT HYDRATION
   ========================================================= */

async function hydrateParticipant() {

    if (
        !document.body.dataset
            .participantDashboard
    ) {
        return;
    }

    try {

        await api('/api/auth/csrf');

        const p =
            await refreshFlow();

        await loadTeam(
            p.participationType
        );

        await loadPoc();

    } catch (e) {
        console.error(
            'Participant dashboard hydration failed:',
            e
        );
    }
}

hydrateParticipant();


/* =========================================================
   GUARDIAN
   ========================================================= */

if ($('requestGuardian')) {

    $('requestGuardian').addEventListener(
        'click',
        async () => {

            try {

                const d =
                    await api(
                        '/api/guardian/request',
                        {
                            method: 'POST'
                        }
                    );

                $('guardianStatus')
                    .textContent =
                    d.message;

            } catch (e) {

                $('guardianStatus')
                    .textContent =
                    e.message;
            }
        }
    );
}

if ($('verifyGuardian')) {

    $('verifyGuardian').addEventListener(
        'click',
        async () => {

            try {

                const d =
                    await api(
                        '/api/guardian/verify',
                        {
                            method: 'POST',

                            body:
                                JSON.stringify({

                                    otp:
                                        $('guardianOtp')
                                            .value,

                                    guardianAuthorityConfirmed:
                                        $('guardianAuthority')
                                            .checked,

                                    participantDetailsConfirmed:
                                        $('guardianDetails')
                                            .checked,

                                    participationApproved:
                                        $('guardianParticipation')
                                            .checked,

                                    termsAccepted:
                                        $('guardianTerms')
                                            .checked,

                                    privacyAccepted:
                                        $('guardianPrivacy')
                                            .checked,

                                    childSafetyAccepted:
                                        $('guardianSafety')
                                            .checked,

                                    rulesAccepted:
                                        $('guardianRules')
                                            .checked,

                                    refundAccepted:
                                        $('guardianRefund')
                                            .checked,

                                    dataProcessingAccepted:
                                        $('guardianData')
                                            .checked
                                })
                        }
                    );

                tone();

                $('guardianStatus')
                    .textContent =
                    d.message;

                await refreshFlow();

            } catch (e) {

                $('guardianStatus')
                    .textContent =
                    e.message;
            }
        }
    );
}


/* =========================================================
   PAYMENT
   ========================================================= */

async function pollPayment() {

    for (
        let i = 0;
        i < 15;
        i++
    ) {

        await new Promise(
            r => setTimeout(r, 2000)
        );

        const s =
            await api(
                '/api/payments/status'
            );

        if (s.paid) {

            await refreshFlow();

            return;
        }
    }

    $('paymentStatus').textContent =
        'Payment is still awaiting secure webhook confirmation. Refresh shortly.';
}

if ($('payNow')) {

    $('payNow').addEventListener(
        'click',
        async () => {

            try {

                const d =
                    await api(
                        '/api/payments/create-order',
                        {
                            method: 'POST'
                        }
                    );

                if (!window.Razorpay) {
                    throw new Error(
                        'Checkout library did not load'
                    );
                }

                new Razorpay({

                    key:
                        d.keyId,

                    amount:
                        d.amount,

                    currency:
                        d.currency,

                    name:
                        'MiTRAA Hackathons 2026',

                    description:
                        `Entry ${d.registrationId}`,

                    order_id:
                        d.orderId,

                    handler: () => {

                        $('paymentStatus')
                            .textContent =
                            'Payment received. Waiting for signed server confirmation…';

                        pollPayment();
                    },

                    modal: {

                        ondismiss: () => {

                            $('paymentStatus')
                                .textContent =
                                'Payment window closed. Registration is not yet active.';
                        }
                    },

                    theme: {
                        color: '#7b2cff'
                    }

                }).open();

            } catch (e) {

                $('paymentStatus')
                    .textContent =
                    e.message;
            }
        }
    );
}


/* =========================================================
   ADMIN PARTICIPANTS
   ========================================================= */

async function loadParticipants() {

    if (!$('participantRows')) return;

    try {

        const a =
            await api(
                '/api/admin/participants'
            );

        $('participantRows')
            .innerHTML =
            a.length

                ? a.map(p => `
<tr>

<td>${p.registrationId}</td>

<td>${p.name}</td>

<td>${p.country}</td>

<td>${p.type}</td>

<td>${p.verification}</td>

<td>${p.status}</td>

</tr>
`).join('')

                : `
<tr>
<td colspan="6">
    No registrations yet.
</td>
</tr>
`;

    } catch (e) {

        $('participantRows')
            .innerHTML = `
<tr>
<td colspan="6">
    ${e.message}
</td>
</tr>
`;
    }
}

async function hydrateAdmin() {

    if (
        !document.body.dataset
            .adminDashboard
    ) {
        return;
    }

    try {

        const m =
            await api(
                '/api/admin/metrics'
            );

        Object.entries(m)
            .forEach(([k, v]) => {

                const e =
                    document.querySelector(
                        `[data-metric="${k}"]`
                    );

                if (e) {
                    e.textContent = v;
                }
            });

    } catch {}

    loadParticipants();
}

hydrateAdmin();

if ($('refreshParticipants')) {

    $('refreshParticipants')
        .addEventListener(
            'click',
            loadParticipants
        );
}


/* =========================================================
   DIAL CODE
   ========================================================= */

function syncDialCode() {

    if (
        !$('country') ||
        !$('dialCode')
    ) {
        return;
    }

    $('dialCode').textContent =
        $('country')
            .selectedOptions[0]
            ?.dataset.code || '';
}

if ($('country')) {

    $('country').addEventListener(
        'change',
        syncDialCode
    );

    syncDialCode();
}


/* =========================================================
   DOMAIN / ARENA
   ========================================================= */

if ($('domain')) {

    const selectedArena =
        new URLSearchParams(
            location.search
        ).get('arena') ||
        sessionStorage.getItem(
            'selectedArena'
        );

    if (selectedArena) {

        const option =
            [
                ...$('domain').options
            ].find(
                item =>
                    item.value ===
                    selectedArena ||
                    item.textContent.trim() ===
                    selectedArena
            );

        if (option) {

            $('domain').value =
                option.value;

            sessionStorage.setItem(
                'selectedArena',
                option.value
            );
        }
    }
}


/* =========================================================
   EMAIL VERIFICATION
   ========================================================= */

if ($('verifyEmail')) {

    $('verifyEmail').value =
        sessionStorage.getItem(
            'pendingVerificationEmail'
        ) ||
        new URLSearchParams(
            location.search
        ).get('email') ||
        '';
}

if ($('verifyEmailForm')) {

    $('verifyEmailForm')
        .addEventListener(
            'submit',
            async e => {

                e.preventDefault();

                try {

                    const d =
                        await api(
                            '/api/auth/verify-email',
                            {
                                method: 'POST',

                                body:
                                    JSON.stringify({

                                        email:
                                            $('verifyEmail')
                                                .value,

                                        otp:
                                            $('verifyOtp')
                                                .value
                                    })
                            }
                        );

                    tone();

                    $('verifyEmailStatus')
                        .textContent =
                        d.message;

                    sessionStorage.removeItem(
                        'pendingVerificationEmail'
                    );

                    setTimeout(
                        () => {
                            location.href =
                                d.paymentUrl ||
                                '/payment.html';
                        },
                        700
                    );

                } catch (x) {

                    $('verifyEmailStatus')
                        .textContent =
                        x.message;
                }
            }
        );
}


/* =========================================================
   RESEND OTP
   ========================================================= */

if ($('resendOtp')) {

    $('resendOtp').addEventListener(
        'click',
        async () => {

            try {

                const d =
                    await api(
                        '/api/auth/resend-otp',
                        {
                            method: 'POST',

                            body:
                                JSON.stringify({
                                    email:
                                        $('verifyEmail')
                                            .value
                                })
                        }
                    );

                $('verifyEmailStatus')
                    .textContent =
                    d.message;

            } catch (x) {

                $('verifyEmailStatus')
                    .textContent =
                    x.message;
            }
        }
    );
}


/* =========================================================
   FORGOT PASSWORD
   ========================================================= */

if ($('forgotForm')) {

    $('forgotForm').addEventListener(
        'submit',
        async e => {

            e.preventDefault();

            try {

                const d =
                    await api(
                        '/api/auth/forgot-password',
                        {
                            method: 'POST',

                            body:
                                JSON.stringify({
                                    email:
                                        $('forgotEmail')
                                            .value
                                })
                        }
                    );

                $('forgotStatus')
                    .textContent =
                    d.message;

                $('resetForm').hidden =
                    false;

            } catch (x) {

                $('forgotStatus')
                    .textContent =
                    x.message;
            }
        }
    );
}


/* =========================================================
   RESET PASSWORD
   ========================================================= */

if ($('resetForm')) {

    $('resetForm').addEventListener(
        'submit',
        async e => {

            e.preventDefault();

            const password =
                $('newPassword').value;

            if (
                password !==
                password.trim() ||
                $('confirmNewPassword').value !==
                $('confirmNewPassword')
                    .value
                    .trim()
            ) {

                $('resetStatus')
                    .textContent =
                    'Password must not start or end with a space.';

                return;
            }

            if (
                password !==
                $('confirmNewPassword').value
            ) {

                $('resetStatus')
                    .textContent =
                    'Passwords do not match.';

                return;
            }

            try {

                const d =
                    await api(
                        '/api/auth/reset-password',
                        {
                            method: 'POST',

                            body:
                                JSON.stringify({

                                    email:
                                        $('forgotEmail')
                                            .value,

                                    otp:
                                        $('resetOtp')
                                            .value,

                                    password
                                })
                        }
                    );

                tone();

                $('resetStatus')
                    .textContent =
                    d.message;

                setTimeout(
                    () => {
                        location.href =
                            '/login.html';
                    },
                    1000
                );

            } catch (x) {

                $('resetStatus')
                    .textContent =
                    x.message;
            }
        }
    );
}


/* =========================================================
   ADMIN USERS
   ========================================================= */

let adminUsers = [];

function esc(v) {

    const d =
        document.createElement('div');

    d.textContent =
        v ?? '';

    return d.innerHTML;
}

async function loadAdminUsers() {

    if (!$('userRows')) return;

    try {

        adminUsers =
            await api(
                '/api/admin/users'
            );

        $('userRows')
            .innerHTML =
            adminUsers
                .map(u => `
<tr>

<td>${u.id}</td>

<td>
    ${esc(u.fullName)}
    <br>
    <small>
        ${esc(u.email)}
    </small>
</td>

<td>${u.role}</td>

<td>
    ${u.emailVerified
        ? 'Verified'
        : 'Pending'}
</td>

<td>
    ${u.enabled
        ? 'Active'
        : 'Disabled'}
</td>

<td>

<button
    class="btn btn-ghost"
    data-edit-user="${u.id}">
    Edit
</button>

<button
    class="btn btn-danger"
    data-delete-user="${u.id}">
    Deactivate
</button>

</td>

</tr>
`)
                .join('')

            ||
            `
<tr>
<td colspan="6">
    No users.
</td>
</tr>
`;

    } catch (e) {

        $('userRows')
            .innerHTML = `
<tr>
<td colspan="6">
    ${esc(e.message)}
</td>
</tr>
`;
    }
}


/* =========================================================
   ADMIN PAYMENTS
   ========================================================= */

async function loadAdminPayments() {

    if (!$('paymentRows')) return;

    try {

        const rows =
            await api(
                '/api/admin/payments'
            );

        $('paymentRows')
            .innerHTML =
            rows
                .map(p => `
<tr>

<td>
    ${esc(p.registrationId)}
    <br>
    <small>
        ${esc(p.email)}
    </small>
</td>

<td>
    ${p.currency}
    ${Number(p.amount).toFixed(2)}
</td>

<td>
    ${p.status}
</td>

<td>
    ${esc(p.orderId)}
</td>

<td>
    ${esc(p.paymentId || '—')}
</td>

<td>
    ${new Date(
        p.createdAt
    ).toLocaleString()}
</td>

</tr>
`)
                .join('')

            ||
            `
<tr>
<td colspan="6">
    No payment records.
</td>
</tr>
`;

    } catch (e) {

        $('paymentRows')
            .innerHTML = `
<tr>
<td colspan="6">
    ${esc(e.message)}
</td>
</tr>
`;
    }
}

if (
    document.body.dataset
        .adminDashboard
) {

    loadAdminUsers();
    loadAdminPayments();
}


/* =========================================================
   ADMIN USER ACTIONS
   ========================================================= */

if ($('userRows')) {

    $('userRows').addEventListener(
        'click',
        async e => {

            const edit =
                e.target.dataset.editUser;

            const del =
                e.target.dataset.deleteUser;

            if (edit) {

                const u =
                    adminUsers.find(
                        x =>
                            String(x.id) ===
                            edit
                    );

                if (!u) return;

                $('userId').value =
                    u.id;

                $('userName').value =
                    u.fullName;

                $('userEmail').value =
                    u.email;

                $('userRole').value =
                    u.role;

                $('userEnabled').checked =
                    u.enabled;

                $('userVerified').checked =
                    u.emailVerified;

                $('userPassword').value =
                    '';

                $('userDialog').showModal();
            }

            if (
                del &&
                confirm(
                    'Deactivate this user? Their audit and payment records will be retained.'
                )
            ) {

                try {

                    await api(
                        '/api/admin/users/' +
                        del,
                        {
                            method: 'DELETE'
                        }
                    );

                    loadAdminUsers();

                } catch (x) {

                    alert(x.message);
                }
            }
        }
    );
}


/* =========================================================
   ADD ADMIN USER
   ========================================================= */

if ($('addUser')) {

    $('addUser').addEventListener(
        'click',
        () => {

            $('userForm').reset();

            $('userId').value =
                '';

            $('userEnabled').checked =
                true;

            $('userVerified').checked =
                true;

            $('userDialog').showModal();
        }
    );
}


/* =========================================================
   CANCEL ADMIN USER
   ========================================================= */

if ($('cancelUser')) {

    $('cancelUser').addEventListener(
        'click',
        () => {
            $('userDialog').close();
        }
    );
}


/* =========================================================
   ADMIN USER FORM
   ========================================================= */

if ($('userForm')) {

    $('userForm').addEventListener(
        'submit',
        async e => {

            e.preventDefault();

            const id =
                $('userId').value;

            const body = {

                fullName:
                    $('userName').value,

                email:
                    $('userEmail').value,

                role:
                    $('userRole').value,

                enabled:
                    $('userEnabled').checked,

                emailVerified:
                    $('userVerified').checked
            };

            if (id) {

                body.newPassword =
                    $('userPassword')
                        .value ||
                    null;

            } else {

                body.password =
                    $('userPassword')
                        .value;
            }

            try {

                await api(
                    '/api/admin/users' +
                    (id ? '/' + id : ''),
                    {
                        method:
                            id
                                ? 'PUT'
                                : 'POST',

                        body:
                            JSON.stringify(body)
                    }
                );

                $('userDialog').close();

                loadAdminUsers();

            } catch (x) {

                $('userFormStatus')
                    .textContent =
                    x.message;
            }
        }
    );
}