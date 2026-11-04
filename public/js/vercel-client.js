/**
 * CineBook Vercel Client-Side Controller
 * Powers live reservations, localStorage persistence, coupon calculation,
 * and 80% refund cancellation when hosted on Vercel's serverless edge.
 */
document.addEventListener("DOMContentLoaded", function () {
    const isVercel = window.location.hostname.includes("vercel.app") || window.location.protocol === "file:" || !window.location.port;

    // Helper: get bookings store
    function getStore() {
        try {
            const raw = localStorage.getItem("cinebook_live_bookings");
            return raw ? JSON.parse(raw) : {};
        } catch (e) {
            return {};
        }
    }

    function saveStore(store) {
        try {
            localStorage.setItem("cinebook_live_bookings", JSON.stringify(store));
        } catch (e) {}
    }

    // 1. Show Page: Intercept Booking Form Submission on Vercel
    const bookingForm = document.getElementById("bookingForm");
    if (bookingForm) {
        bookingForm.addEventListener("submit", function (e) {
            // Check if server POST fails or if on Vercel
            if (isVercel) {
                e.preventDefault();
                const seatInput = document.getElementById("seatCodesInput");
                const hiddenInput = document.getElementById("seatCodesHidden");
                const couponInput = document.getElementById("couponCode");
                const seatCodesRaw = (seatInput ? seatInput.value : "") || (hiddenInput ? hiddenInput.value : "");
                const couponRaw = couponInput ? couponInput.value.trim().toUpperCase() : "";

                if (!seatCodesRaw.trim()) {
                    alert("Please select at least one seat before booking.");
                    return;
                }

                const codes = seatCodesRaw.split(/[,;\s]+/).map(s => s.trim().toUpperCase()).filter(Boolean);
                if (codes.length === 0) {
                    alert("Please select at least one seat before booking.");
                    return;
                }

                // Determine movie from page
                const movieTitleEl = document.querySelector(".section-title");
                const movieTitle = movieTitleEl ? movieTitleEl.textContent.trim() : "Interstellar";

                // Calculate pricing strictly based on row tier
                let subtotal = 0;
                const seatBreakdown = [];
                codes.forEach(code => {
                    const row = code.charAt(0);
                    let price = 150;
                    let tier = "REGULAR";
                    if (row === 'C' || row === 'D') {
                        price = 250;
                        tier = "PREMIUM";
                    } else if (row === 'E') {
                        price = 400;
                        tier = "VIP";
                    }
                    subtotal += price;
                    seatBreakdown.push(`${code} (${tier})`);
                });

                let discount = 0;
                let appliedCoupon = null;
                if (couponRaw === "CINE10") {
                    discount = Math.round(subtotal * 0.10 * 100) / 100;
                    appliedCoupon = "CINE10";
                }

                const total = subtotal - discount;

                // Save booking
                const store = getStore();
                const nextId = Object.keys(store).length ? Math.max(...Object.keys(store).map(Number)) + 1 : 1001;
                const now = new Date();
                const formattedDate = now.toLocaleDateString("en-IN", { day: '2-digit', month: 'short', year: 'numeric' }) + ", " +
                                      now.toLocaleTimeString("en-IN", { hour: '2-digit', minute: '2-digit', hour12: true });

                const newBooking = {
                    id: nextId,
                    formattedId: "BK" + nextId,
                    movieName: movieTitle,
                    showTime: "7:30 PM",
                    seatCodes: codes.join(", "),
                    seatsSummary: seatBreakdown.join(", "),
                    subtotal: subtotal.toFixed(2),
                    couponCode: appliedCoupon,
                    discount: discount.toFixed(2),
                    totalAmount: total.toFixed(2),
                    refundAmount: "0.00",
                    status: "CONFIRMED",
                    createdAt: formattedDate,
                    cancelledAt: null
                };

                store[nextId] = newBooking;
                saveStore(store);

                // Redirect to summary
                window.location.href = `/bookings/summary.html?id=${nextId}`;
            }
        });
    }

    // 2. Summary Page: Render Dynamic Booking Data from Store if on Vercel
    const ticketArticle = document.querySelector(".admission-ticket");
    if (ticketArticle) {
        const urlParams = new URLSearchParams(window.location.search);
        const bookingIdParam = urlParams.get("id");
        const store = getStore();
        let targetId = bookingIdParam;

        if (!targetId && Object.keys(store).length) {
            targetId = Math.max(...Object.keys(store).map(Number));
        }

        if (targetId && store[targetId]) {
            const b = store[targetId];

            // Update DOM elements
            const stampNo = document.querySelector(".ticket-number-stamp");
            if (stampNo) stampNo.textContent = "No. " + b.formattedId;

            const movieEl = document.querySelector(".ticket-main-body h1");
            if (movieEl) movieEl.textContent = b.movieName;

            // Reserved seats
            const seatsEl = document.querySelector(".ticket-receipt-table strong");
            if (seatsEl) seatsEl.textContent = b.seatsSummary;

            // Subtotal
            const rows = document.querySelectorAll(".ticket-receipt-table tr");
            rows.forEach(row => {
                const th = row.querySelector("th");
                const td = row.querySelector("td");
                if (!th || !td) return;
                const text = th.textContent.toLowerCase();
                if (text.includes("subtotal")) {
                    td.textContent = "Rs. " + b.subtotal;
                } else if (text.includes("voucher") || text.includes("coupon")) {
                    if (b.couponCode) {
                        td.textContent = "- Rs. " + b.discount;
                    } else {
                        row.style.display = "none";
                    }
                } else if (text.includes("total amount")) {
                    td.textContent = "Rs. " + b.totalAmount;
                } else if (text.includes("refund")) {
                    if (b.status === "CANCELLED") {
                        td.textContent = "Rs. " + b.refundAmount;
                        row.style.display = "";
                    } else {
                        row.style.display = "none";
                    }
                }
            });

            // Stamps
            const admitStamp = document.querySelector(".stamp-admit");
            const cancelStamp = document.querySelector(".stamp-cancelled");
            if (b.status === "CANCELLED") {
                if (admitStamp) admitStamp.style.display = "none";
                if (cancelStamp) cancelStamp.style.display = "block";
            } else {
                if (admitStamp) admitStamp.style.display = "block";
                if (cancelStamp) cancelStamp.style.display = "none";
            }

            // Cancel Form Handler
            const cancelForm = document.querySelector("form[action*='/cancel']");
            if (cancelForm) {
                if (b.status === "CANCELLED") {
                    cancelForm.style.display = "none";
                } else {
                    cancelForm.addEventListener("submit", function (e) {
                        if (isVercel) {
                            e.preventDefault();
                            const refund = (parseFloat(b.totalAmount) * 0.80).toFixed(2);
                            if (confirm(`Are you sure you wish to cancel booking ${b.formattedId}? You will receive an 80% refund (Rs. ${refund}).`)) {
                                b.status = "CANCELLED";
                                b.refundAmount = refund;
                                b.cancelledAt = new Date().toLocaleDateString("en-IN") + " " + new Date().toLocaleTimeString("en-IN");
                                store[targetId] = b;
                                saveStore(store);
                                window.location.reload();
                            }
                        }
                    });
                }
            }
        }
    }

    // 3. Lookup Page: Search in Store if on Vercel
    const lookupForm = document.querySelector("form[action*='/lookup']");
    if (lookupForm) {
        lookupForm.addEventListener("submit", function (e) {
            if (isVercel) {
                e.preventDefault();
                const codeInput = document.getElementById("code");
                const code = codeInput ? codeInput.value.trim().toUpperCase() : "";
                const num = code.replace(/\D/g, "");
                const store = getStore();

                if (num && store[num]) {
                    window.location.href = `/bookings/summary.html?id=${num}`;
                } else if (Object.keys(store).length) {
                    const firstKey = Object.keys(store)[0];
                    window.location.href = `/bookings/summary.html?id=${firstKey}`;
                } else {
                    alert(`Booking with ID ${code} not found. Please verify the booking reference number.`);
                }
            }
        });
    }
});
