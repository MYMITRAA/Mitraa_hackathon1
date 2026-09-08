(() => {
    "use strict";

    // ============================================================
    // ELEMENTS
    // ============================================================

    const payButton = document.getElementById("payNow");
    const paymentStatus = document.getElementById("paymentStatus");
    const paymentState = document.getElementById("paymentState");

    const registrationId =
        document.getElementById("registrationId");

    const entryType =
        document.getElementById("entryType");

    const paymentPrice =
        document.getElementById("paymentPrice");

    let paymentConfig = null;


    // ============================================================
    // HELPERS
    // ============================================================

    function setStatus(message, type = "") {
        if (!paymentStatus) return;

        paymentStatus.textContent = message;
        paymentStatus.className = "form-status";

        if (type) {
            paymentStatus.classList.add(type);
        }
    }


    function setState(message) {
        if (!paymentState) return;

        paymentState.textContent = message;
    }


    function setButton(enabled, text = "Pay and activate") {
        if (!payButton) return;

        payButton.disabled = !enabled;
        payButton.textContent = text;
    }


    async function getJson(url, options = {}) {

        const response = await fetch(url, {
            credentials: "same-origin",
            cache: "no-store",
            ...options
        });

        const contentType =
            response.headers.get("content-type") || "";

        if (!contentType.includes("application/json")) {

            await response.text();

            throw new Error(
                `Server returned non-JSON response (${response.status}).`
            );
        }

        const data = await response.json();

        if (!response.ok) {

            throw new Error(
                data.message ||
                `Request failed (${response.status})`
            );
        }

        return data;
    }


    // ============================================================
    // CSRF
    // ============================================================

    async function csrfHeaders() {

        const response =
            await fetch("/api/auth/csrf", {
                credentials: "same-origin",
                cache: "no-store"
            });

        const contentType =
            response.headers.get("content-type") || "";

        if (!contentType.includes("application/json")) {
            throw new Error(
                "Unable to obtain CSRF token."
            );
        }

        const data = await response.json();

        const token =
            data.token ||
            data.csrfToken ||
            "";

        const headerName =
            data.headerName ||
            "X-XSRF-TOKEN";

        if (!token) {
            throw new Error(
                "CSRF token was not returned by the server."
            );
        }

        return {
            [headerName]: token
        };
    }


    // ============================================================
    // LOAD PAYMENT SESSION
    // ============================================================

    async function loadPaymentSession() {

        setState("CHECKING");
        setStatus("Checking payment session...");
        setButton(false, "Pay and activate");

        try {

            const data =
                await getJson(
                    "/api/payments/verification-status"
                );


            // ----------------------------------------------------
            // ALREADY PAID
            // ----------------------------------------------------

            if (data.paid === true) {

                setState("PAID");

                setStatus(
                    "Payment already confirmed. Redirecting to login..."
                );

                setTimeout(() => {
                    window.location.href = "/login";
                }, 500);

                return;
            }


            // ----------------------------------------------------
            // PARTICIPANT INFORMATION
            // ----------------------------------------------------

            if (registrationId) {

                registrationId.textContent =
                    data.registrationId || "-";
            }


            if (entryType) {

                entryType.textContent =
                    data.participationType || "-";
            }


            // ----------------------------------------------------
            // READY
            // ----------------------------------------------------

            setState("READY");

            setStatus(
                "Ready. Click Pay and activate."
            );

            setButton(true, "Pay and activate");

        } catch (error) {

            console.error(
                "Payment session error:",
                error
            );

            setState("ERROR");

            setStatus(
                error.message ||
                "Payment session expired. Please verify your email again.",
                "error"
            );

            setButton(
                false,
                "Pay and activate"
            );
        }
    }


    // ============================================================
    // CREATE RAZORPAY ORDER
    // ============================================================

    async function createOrder() {

        setButton(
            false,
            "Preparing payment..."
        );

        setState("CREATING");

        setStatus(
            "Creating secure Razorpay order..."
        );

        try {

            const csrf =
                await csrfHeaders();


            const data =
                await getJson(
                    "/api/payments/create-order-after-verification",
                    {
                        method: "POST",

                        headers: {
                            "Content-Type": "application/json",
                            ...csrf
                        },

                        body: JSON.stringify({})
                    }
                );


            // ----------------------------------------------------
            // ALREADY PAID
            // ----------------------------------------------------

            if (data.paid === true) {

                setState("PAID");

                setStatus(
                    "Payment already confirmed. Redirecting to login..."
                );

                window.location.href =
                    "/login";

                return;
            }


            paymentConfig = data;


            // ----------------------------------------------------
            // DISPLAY PRICE
            // ----------------------------------------------------

            if (paymentPrice) {

                paymentPrice.textContent =
                    data.displayAmount ||
                    `${data.currency} ${(data.amount / 100).toFixed(2)}`;
            }


            setState("PAYMENT READY");

            openRazorpay(data);

        } catch (error) {

            console.error(
                "Create order error:",
                error
            );

            setState("ERROR");

            setStatus(
                error.message ||
                "Unable to create payment order.",
                "error"
            );

            setButton(
                true,
                "Pay and activate"
            );
        }
    }


    // ============================================================
    // RAZORPAY CHECKOUT
    // ============================================================

    function openRazorpay(data) {

        if (typeof Razorpay === "undefined") {

            setState("ERROR");

            setStatus(
                "Razorpay checkout could not be loaded. Please refresh the page.",
                "error"
            );

            setButton(
                true,
                "Pay and activate"
            );

            return;
        }


        const options = {

            key: data.keyId,

            amount: data.amount,

            currency: data.currency,

            name: "MiTRAA Hackathons",

            description:
                "Hackathon Registration Fee",

            order_id:
                data.orderId,


            handler: async function (response) {

                await handlePaymentSuccess(
                    response
                );
            },


            modal: {

                ondismiss: function () {

                    setState("READY");

                    setStatus(
                        "Payment window closed. You can try again."
                    );

                    setButton(
                        true,
                        "Pay and activate"
                    );
                }
            },


            theme: {
                color: "#7130db"
            }
        };


        const razorpay =
            new Razorpay(options);


        razorpay.on(
            "payment.failed",
            function (response) {

                console.error(
                    "Razorpay payment failed:",
                    response
                );

                setState("PAYMENT FAILED");

                setStatus(
                    "Payment failed. Please try again.",
                    "error"
                );

                setButton(
                    true,
                    "Pay and activate"
                );
            }
        );


        razorpay.open();
    }


    // ============================================================
    // PAYMENT SUCCESS
    // ============================================================

    async function handlePaymentSuccess(response) {

        setButton(
            false,
            "Confirming payment..."
        );

        setState("VERIFYING");

        setStatus(
            "Payment successful. Confirming securely with server..."
        );

        try {

            const csrf =
                await csrfHeaders();


            const data =
                await getJson(
                    "/api/payments/verify-after-checkout",
                    {
                        method: "POST",

                        headers: {
                            "Content-Type": "application/json",
                            ...csrf
                        },

                        body: JSON.stringify({

                            razorpay_payment_id:
                                response.razorpay_payment_id,

                            razorpay_order_id:
                                response.razorpay_order_id,

                            razorpay_signature:
                                response.razorpay_signature
                        })
                    }
                );


            // ----------------------------------------------------
            // SERVER CONFIRMED PAYMENT
            // ----------------------------------------------------

            if (data.paid === true) {

                setState("PAID");

                setStatus(
                    "Payment confirmed. Invoice is being emailed. Redirecting to login..."
                );


                setTimeout(() => {

                    window.location.href =
                        data.redirectUrl ||
                        "/login";

                }, 1000);

                return;
            }


            throw new Error(
                data.message ||
                "Payment could not be confirmed."
            );

        } catch (error) {

            console.error(
                "Payment verification error:",
                error
            );

            setState("VERIFICATION FAILED");

            setStatus(
                error.message ||
                "Payment succeeded but server confirmation failed. Please contact support.",
                "error"
            );

            setButton(
                true,
                "Retry confirmation"
            );
        }
    }


    // ============================================================
    // BUTTON
    // ============================================================

    if (payButton) {

        payButton.addEventListener(
            "click",
            function () {
                createOrder();
            }
        );

    } else {

        console.error(
            "Payment button #payNow was not found."
        );
    }


    // ============================================================
    // START
    // ============================================================

    loadPaymentSession();

})();