document.addEventListener("DOMContentLoaded", function () {

    /* ───── 태그 입력 ───── */
    let tagInput = document.getElementById("tagInput");
    let tagWrap = document.getElementById("tagWrap");
    let tags = [];

    tagInput.addEventListener("keydown", function (e) {
        if (e.key !== "Enter") return;
        e.preventDefault();

        let value = tagInput.value.trim();
        if (!value) return;

        if (value.charAt(0) !== "#") value = "#" + value;

        if (tags.indexOf(value) !== -1) {
            tagInput.value = "";
            return;
        }

        tags.push(value);
        let chip = document.createElement("span");
        chip.className = "Register-Tag-Chip";
        chip.innerHTML = value + '<button class="Register-Tag-Remove" type="button">&times;</button>';

        chip.querySelector(".Register-Tag-Remove").addEventListener("click", function () {
            let idx = tags.indexOf(value);
            if (idx !== -1) tags.splice(idx, 1);
            chip.remove();
        });

        tagWrap.insertBefore(chip, tagInput);
        tagInput.value = "";
    });

    tagWrap.addEventListener("click", function () {
        tagInput.focus();
    });

    /* ───── 배너 이미지 업로드 ───── */
    let bannerFileInput = document.getElementById("bannerFileInput");
    let bannerUploadBtn = document.getElementById("bannerUploadBtn");
    let bannerChangeBtn = document.getElementById("bannerChangeBtn");
    let bannerImage = document.getElementById("bannerImage");
    let bannerPlaceholder = document.getElementById("bannerPlaceholder");

    bannerUploadBtn.addEventListener("click", function () {
        bannerFileInput.click();
    });

    bannerChangeBtn.addEventListener("click", function () {
        bannerFileInput.click();
    });

    bannerFileInput.addEventListener("change", function () {
        let file = bannerFileInput.files[0];
        if (!file) return;

        let reader = new FileReader();
        reader.onload = function (e) {
            bannerImage.src = e.target.result;
            bannerImage.style.display = "block";
            bannerPlaceholder.style.display = "none";
            bannerUploadBtn.style.display = "none";
            bannerChangeBtn.style.display = "inline-flex";
        };
        reader.readAsDataURL(file);
    });

    /* ───── 날짜 유효성 검사 ───── */
    let startDate = document.getElementById("startDate");
    let endDate = document.getElementById("endDate");
    let announceDate = document.getElementById("announceDate");

    endDate.addEventListener("change", function () {
        if (startDate.value && endDate.value && endDate.value < startDate.value) {
            alert("마감일은 시작일 이후여야 합니다.");
            endDate.value = "";
        }
    });

    startDate.addEventListener("change", function () {
        if (endDate.value && endDate.value < startDate.value) {
            alert("마감일은 시작일 이후여야 합니다.");
            endDate.value = "";
        }
    });

    announceDate.addEventListener("change", function () {
        if (endDate.value && announceDate.value && announceDate.value < endDate.value) {
            alert("결과 발표일은 마감일 이후여야 합니다.");
            announceDate.value = "";
        }
    });

    /* ───── 등록 / 수정 버튼 ───── */
    let registerBtn = document.getElementById("registerContestBtn");
    let editBtn = document.getElementById("editContestBtn");
    let isSubmitting = false;

    registerBtn.addEventListener("click", function () {
        submitContest(false);
    });

    editBtn.addEventListener("click", function () {
        submitContest(true);
    });

    function submitContest(isEdit) {
        if (isSubmitting) return;
        isSubmitting = true;
        let title = document.getElementById("titleInput").value.trim();
        let organizer = document.getElementById("organizerInput").value.trim();
        let category = document.getElementById("categoryInput").value;
        let entryStart = startDate.value;
        let entryEnd = endDate.value;
        let resultDate = announceDate.value;
        let prizeInfo = document.getElementById("prizeInfoInput").value.trim();
        let price = document.getElementById("priceInput").value;
        let description = document.getElementById("descriptionInput").value.trim();

        // 유효성 검사
        if (!title) { alert("공모전 제목을 입력하세요."); isSubmitting = false; return; }
        if (!organizer) { alert("주최자를 입력하세요."); isSubmitting = false; return; }
        if (!entryStart || !entryEnd) { alert("접수 기간을 설정하세요."); isSubmitting = false; return; }

        let formData = new FormData();
        formData.append("title", title);
        formData.append("organizer", organizer);
        if (category) formData.append("category", category);
        formData.append("entryStart", entryStart);
        formData.append("entryEnd", entryEnd);
        if (resultDate) formData.append("resultDate", resultDate);
        if (prizeInfo) formData.append("prizeInfo", prizeInfo);
        if (price) formData.append("price", price);
        if (description) formData.append("description", description);

        // 태그
        tags.forEach(function (tag) {
            formData.append("tagNames", tag);
        });

        // 배너 이미지
        let coverFile = bannerFileInput.files[0];
        if (coverFile) {
            formData.append("coverFile", coverFile);
        }

        // 수정 모드
        let contestIdMeta = document.querySelector("meta[name='contestId']");
        let contestId = contestIdMeta ? contestIdMeta.getAttribute("content") : null;

        let url = isEdit && contestId
            ? "/contest/api/" + contestId + "/edit"
            : "/contest/api/register";

        fetch(url, {
            method: "POST",
            credentials: "same-origin",
            body: formData
        })
        .then(function (res) {
            if (!res.ok) throw new Error("요청 실패: " + res.status);
            return res.json();
        })
        .then(function (data) {
            alert(isEdit ? "공모전이 수정되었습니다." : "공모전이 등록되었습니다.");
            location.href = "/contest/list";
        })
        .catch(function (err) {
            console.error(err);
            alert("처리 중 오류가 발생했습니다.");
            isSubmitting = false;
        });
    }

    /* ───── 수정 모드: 기존 데이터 바인딩 ───── */
    let contestIdMeta = document.querySelector("meta[name='contestId']");
    if (contestIdMeta) {
        editBtn.style.display = "inline-flex";
        registerBtn.textContent = "수정";
    } else {
        editBtn.style.display = "none";
    }

});
