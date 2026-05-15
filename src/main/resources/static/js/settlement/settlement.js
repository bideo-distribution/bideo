document.addEventListener("DOMContentLoaded", function () {
    let accountInput = document.getElementById("settlement-account");
    let accountField = accountInput ? accountInput.closest(".st-field") : null;
    let accountRow = accountField ? accountField.querySelector(".st-account-row") : null;
    let bankInput = accountRow ? accountRow.querySelector("input:not(#settlement-account)") : null;
    let passwordInput = document.getElementById("settlement-password");
    let submitButton = document.querySelector(".st-form .st-btn-main");
    let memoryCheckbox = document.getElementById("settlement-memory-checkbox");
    let MAIN_COLOR = "#d0b86f";
    let ACCOUNT_STORAGE_KEY = "settlementRememberedAccount";

    if (!bankInput || !accountInput || !passwordInput || !submitButton || !memoryCheckbox) {
        return;
    }

    function loadRememberedAccount() {
        let savedAccount = localStorage.getItem(ACCOUNT_STORAGE_KEY);

        if (!savedAccount) {
            memoryCheckbox.checked = false;
            return;
        }

        try {
            let parsedAccount = JSON.parse(savedAccount);
            bankInput.value = parsedAccount.bankName || "";
            accountInput.value = parsedAccount.accountNumber || "";
            memoryCheckbox.checked = Boolean(parsedAccount.bankName || parsedAccount.accountNumber);
        } catch (error) {
            localStorage.removeItem(ACCOUNT_STORAGE_KEY);
            memoryCheckbox.checked = false;
        }
    }

    // 입력칸 바로 아래에 유효성 메시지를 붙이기 위한 안내 영역을 만든다.
    function createMessageElement(target) {
        let message = document.createElement("p");
        message.style.margin = "4px 0 0";
        message.style.fontSize = "12px";
        message.style.lineHeight = "1.5";
        target.insertAdjacentElement("afterend", message);
        return message;
    }

    let accountMessage = createMessageElement(accountRow);
    let passwordMessage = createMessageElement(passwordInput);

    // 입력 상태에 따라 메시지만 바꾼다.
    function setFieldState(input, messageEl, isValid, messageText) {
        messageEl.textContent = messageText;
        messageEl.style.color = MAIN_COLOR;
    }

    function resetFieldState(input, messageEl) {
        messageEl.textContent = "";
    }

    // 은행명은 한글/영문 2자 이상 입력되었는지 검사한다.
    function validateBank() {
        let bankValue = bankInput.value.trim();
        let hasBankName = /[A-Za-z가-힣]/.test(bankValue);
        let isValid = hasBankName && bankValue.length >= 2;

        if (!bankValue) {
            setFieldState(bankInput, accountMessage, false, "은행명을 입력해 주세요.");
            return false;
        }

        if (!isValid) {
            setFieldState(bankInput, accountMessage, false, "은행명을 올바르게 입력해 주세요.");
            return false;
        }

        resetFieldState(bankInput, accountMessage);
        return true;
    }

    // 계좌번호는 숫자만 기준으로 10~14자리인지 검사한다.
    function validateAccount() {
        let accountValue = accountInput.value.trim();
        let digitOnly = accountValue.replace(/\D/g, "");
        let isValid = digitOnly.length >= 10 && digitOnly.length <= 14;

        if (!accountValue) {
            setFieldState(accountInput, accountMessage, false, "계좌번호를 입력해 주세요.");
            return false;
        }

        if (!isValid) {
            setFieldState(accountInput, accountMessage, false, "계좌번호는 숫자 10~14자리여야 합니다.");
            return false;
        }

        resetFieldState(accountInput, accountMessage);
        return true;
    }

    // 비밀번호는 4~20자이며 영문, 숫자를 각각 하나 이상 포함하도록 검사한다.
    function validatePassword() {
        let rawValue = passwordInput.value.trim();
        let hasLetter = /[A-Za-z]/.test(rawValue);
        let hasNumber = /\d/.test(rawValue);
        let isValid = rawValue.length >= 4 && rawValue.length <= 20 && hasLetter && hasNumber && !/\s/.test(rawValue);

        if (!rawValue) {
            setFieldState(passwordInput, passwordMessage, false, "비밀번호를 입력해 주세요.");
            return false;
        }

        if (!isValid) {
            setFieldState(passwordInput, passwordMessage, false, "비밀번호는 영문, 숫자를 포함한 4~20자여야 합니다.");
            return false;
        }

        resetFieldState(passwordInput, passwordMessage);
        return true;
    }

    bankInput.addEventListener("blur", validateBank);
    accountInput.addEventListener("blur", validateAccount);
    passwordInput.addEventListener("blur", validatePassword);

    bankInput.addEventListener("input", function () {
        resetFieldState(bankInput, accountMessage);
    });

    accountInput.addEventListener("input", function () {
        resetFieldState(accountInput, accountMessage);
    });

    passwordInput.addEventListener("input", function () {
        resetFieldState(passwordInput, passwordMessage);
    });

    // 체크박스를 선택하면 현재 계좌 정보를 저장하고, 해제하면 저장 상태를 지운다.
    memoryCheckbox.addEventListener("change", function () {
        let bankValue = bankInput.value.trim();
        let accountValue = accountInput.value.trim();

        if (!memoryCheckbox.checked) {
            localStorage.removeItem(ACCOUNT_STORAGE_KEY);
            return;
        }

        if (!bankValue || !accountValue) {
            memoryCheckbox.checked = false;
            setFieldState(accountInput, accountMessage, false, "은행명과 계좌번호를 먼저 입력해 주세요.");
            return;
        }

        localStorage.setItem(ACCOUNT_STORAGE_KEY, JSON.stringify({
            bankName: bankValue,
            accountNumber: accountValue
        }));
        resetFieldState(accountInput, accountMessage);
    });

    // 버튼 클릭 시 두 입력값을 검사하고, 하나라도 틀리면 진행을 막는다.
    submitButton.addEventListener("click", function () {
        let isBankValid = validateBank();
        let isAccountValid = validateAccount();
        let isPasswordValid = validatePassword();

        if (!isBankValid || !isAccountValid || !isPasswordValid) {
            return;
        }

        if (typeof BideoSnackbar !== "undefined") {
            BideoSnackbar.show("정산 신청이 완료되었습니다.", "settlement", 3000, {
                label: "내역보기",
                url: "/settlement/list"
            });
        }
    });

    loadRememberedAccount();
});

document.addEventListener("DOMContentLoaded", function () {
    let confirmButton = document.querySelector(".sd-btn");

    if (!confirmButton) {
        return;
    }

    // 버튼 클릭 이벤트만 연결한다.
    confirmButton.addEventListener("click", function () {
        confirmButton.blur();
    });
});
