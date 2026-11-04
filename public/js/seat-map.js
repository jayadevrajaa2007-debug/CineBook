/**
 * CineBook - Vintage Picture Palace Seat Map Controller
 *
 * NOTE: Seat prices are strictly read from the DOM data-price attributes
 * injected by the server-side SeatType enum. Prices are NEVER hard-coded in JS.
 */
document.addEventListener("DOMContentLoaded", function () {
    const seatGrid = document.getElementById("seatGrid");
    const hiddenSeatCodes = document.getElementById("seatCodesHidden");
    const manualSeatInput = document.getElementById("seatCodesInput");
    const liveSelectedSeatsDisplay = document.getElementById("liveSelectedSeats");
    const liveRunningTotalDisplay = document.getElementById("liveRunningTotal");
    const liveRunningTotalFinal = document.getElementById("liveRunningTotalFinal");
    const bookSubmitButton = document.getElementById("bookSubmitBtn");

    if (!seatGrid) {
        return;
    }

    const selectedSeats = new Map(); // seatCode -> price (number)

    // Sync from manual input on page load (if user pre-filled or reloaded)
    if (manualSeatInput && manualSeatInput.value.trim() !== "") {
        syncFromTextInput(manualSeatInput.value);
    }

    // Handle seat clicks on the grid
    seatGrid.addEventListener("click", function (e) {
        const seatElement = e.target.closest(".seat");
        if (!seatElement) return;

        const isBooked = seatElement.getAttribute("data-booked") === "true";
        if (isBooked) {
            return; // Booked seats are disabled and cannot be selected
        }

        const seatCode = seatElement.getAttribute("data-code");
        const seatPrice = parseFloat(seatElement.getAttribute("data-price") || "0");
        const icon = seatElement.querySelector(".seat-icon");

        if (selectedSeats.has(seatCode)) {
            // Deselect
            selectedSeats.delete(seatCode);
            seatElement.classList.remove("selected");
            seatElement.setAttribute("aria-selected", "false");
            if (icon) icon.textContent = "O";
        } else {
            // Select
            selectedSeats.set(seatCode, seatPrice);
            seatElement.classList.add("selected");
            seatElement.setAttribute("aria-selected", "true");
            if (icon) icon.textContent = "✓";
        }

        updateUI();
    });

    // Handle clicks inside chips container (e.g. clicking x to remove seat)
    if (liveSelectedSeatsDisplay) {
        liveSelectedSeatsDisplay.addEventListener("click", function (e) {
            const removeBtn = e.target.closest(".chip-remove");
            if (!removeBtn) return;
            const code = removeBtn.getAttribute("data-code");
            if (code && selectedSeats.has(code)) {
                selectedSeats.delete(code);
                const el = document.querySelector(`.seat[data-code="${code}"]`);
                if (el) {
                    el.classList.remove("selected");
                    el.setAttribute("aria-selected", "false");
                    const icon = el.querySelector(".seat-icon");
                    if (icon) icon.textContent = "O";
                }
                updateUI();
            }
        });
    }

    // Listen for manual text field edits to support fallback entry
    if (manualSeatInput) {
        manualSeatInput.addEventListener("input", function () {
            syncFromTextInput(manualSeatInput.value);
        });
    }

    function syncFromTextInput(text) {
        // Clear current selections
        selectedSeats.clear();
        document.querySelectorAll(".seat.selected").forEach(function (el) {
            el.classList.remove("selected");
            el.setAttribute("aria-selected", "false");
            const icon = el.querySelector(".seat-icon");
            if (icon) icon.textContent = "O";
        });

        if (!text) {
            updateUI(false);
            return;
        }

        const tokens = text.split(/[,;\s]+/).map(function (t) {
            return t.trim().toUpperCase();
        }).filter(Boolean);

        tokens.forEach(function (code) {
            const el = document.querySelector(`.seat[data-code="${code}"]`);
            if (el && el.getAttribute("data-booked") !== "true") {
                const price = parseFloat(el.getAttribute("data-price") || "0");
                selectedSeats.set(code, price);
                el.classList.add("selected");
                el.setAttribute("aria-selected", "true");
                const icon = el.querySelector(".seat-icon");
                if (icon) icon.textContent = "✓";
            }
        });

        updateUI(false);
    }

    function updateUI(updateInput = true) {
        const codes = Array.from(selectedSeats.keys()).sort();
        const codeString = codes.join(", ");

        if (hiddenSeatCodes) {
            hiddenSeatCodes.value = codeString;
        }
        if (updateInput && manualSeatInput) {
            manualSeatInput.value = codeString;
        }

        // Calculate running total
        let total = 0;
        selectedSeats.forEach(function (price) {
            total += price;
        });

        const formattedTotal = "Rs. " + total.toFixed(2);

        if (liveSelectedSeatsDisplay) {
            if (codes.length === 0) {
                liveSelectedSeatsDisplay.innerHTML = '<span class="empty-chips-msg">No seats selected yet. Click any available seat in the auditorium.</span>';
            } else {
                liveSelectedSeatsDisplay.innerHTML = codes.map(function (c) {
                    const price = selectedSeats.get(c);
                    return `<span class="seat-chip">
                        <span>${c}</span>
                        <span style="color: var(--brass-gold-dark); font-size: 0.78rem;">(Rs. ${price})</span>
                        <span class="chip-remove" data-code="${c}" title="Deselect seat ${c}" style="cursor: pointer; margin-left: 4px; font-weight: bold; color: var(--velvet-red);">×</span>
                    </span>`;
                }).join("");
            }
        }

        if (liveRunningTotalDisplay) {
            liveRunningTotalDisplay.textContent = formattedTotal;
        }
        if (liveRunningTotalFinal) {
            liveRunningTotalFinal.textContent = formattedTotal;
        }

        if (bookSubmitButton) {
            bookSubmitButton.disabled = codes.length === 0;
            if (codes.length === 0) {
                bookSubmitButton.classList.add("disabled");
            } else {
                bookSubmitButton.classList.remove("disabled");
            }
        }
    }
});
