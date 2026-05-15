function initializeGalleryRegister() {
    let modal = document.getElementById("galleryModal");

    if (!modal || modal.dataset.initialized === "true") {
        return;
    }

    modal.dataset.initialized = "true";

    let closeBtn = document.getElementById("closeBtn");
    let titleBox = document.getElementById("titleBox");
    let descBox = document.getElementById("descBox");
    let titleCount = document.getElementById("titleCount");
    let descCount = document.getElementById("descCount");
    let titleError = document.getElementById("titleError");
    let thumbInput = document.getElementById("thumbInput");
    let thumbBtn = document.getElementById("thumbBtn");
    let thumbShell = modal.querySelector(".thumb-btn");
    let thumbPreviewImage = document.getElementById("thumbPreviewImage");
    let thumbError = document.getElementById("thumbError");
    let tagList = document.getElementById("tagList");
    let tagInput = document.getElementById("tagInput");
    let tagSuggestions = document.getElementById("tagSuggestions");
    let galleryLinkUrl = document.getElementById("galleryLinkUrl");
    let galleryLinkCopyButton = document.getElementById("galleryLinkCopyButton");
    let thumbLinkMeta = document.getElementById("thumbLinkMeta");
    let openArtworkModalBtn = document.getElementById("openArtworkModalBtn");
    let artworkDialogBackdrop = document.getElementById("artworkDialogBackdrop");
    let closeArtworkModalBtn = document.getElementById("closeArtworkModalBtn");
    let cancelArtworkModalBtn = document.getElementById("cancelArtworkModalBtn");
    let confirmArtworkModalBtn = document.getElementById("confirmArtworkModalBtn");
    let artworkList = document.getElementById("artworkList");
    let selectedArtworkList = document.getElementById("selectedArtworkList");
    let selectedArtworkLinks = document.getElementById("selectedArtworkLinks");
    let artworkSearchInput = document.getElementById("artworkSearchInput");
    let createBtn = document.getElementById("createBtn");
    let initialGalleryData = {
        id: modal.getAttribute("data-gallery-id") || null,
        title: modal.getAttribute("data-initial-title") || "",
        description: modal.getAttribute("data-initial-description") || "",
        coverImage: modal.getAttribute("data-cover-url") || "",
        workIds: (modal.getAttribute("data-initial-work-ids") || "")
            .split(",")
            .map(function (value) { return Number(value.trim()); })
            .filter(function (value) { return !Number.isNaN(value) && value > 0; }),
        tagNames: (modal.getAttribute("data-initial-tags") || "")
            .split(",")
            .map(function (value) { return value.trim(); })
            .filter(function (value) { return !!value; })
    };
    let isEditMode = modal.getAttribute("data-mode") === "edit";
    let galleryId = modal.getAttribute("data-gallery-id");
    let isSubmitting = false;
    let previewUrl = "";
    let thumbOk = false;
    let tags = Array.isArray(initialGalleryData.tagNames) ? initialGalleryData.tagNames.slice() : [];
    let selectedExistingTagNames = {};
    let tagSuggestionAbortController = null;
    let tagSuggestionRequestSeq = 0;
    let activeTagSuggestionIndex = -1;
    let committedArtworkLinks = [];

    if (!titleBox || !descBox || !createBtn || !thumbInput) {
        modal.dataset.initialized = "false";
        return;
    }

    modal.hidden = false;
    modal.style.display = "flex";

    tags.forEach(function (tagName) {
        selectedExistingTagNames[tagName] = true;
    });

    if (modal.getAttribute("data-compose-embedded") === "true") {
        modal.style.position = "relative";
        modal.style.left = "auto";
        modal.style.top = "auto";
        modal.style.transform = "none";
    } else {
        modal.style.position = "fixed";
        modal.style.left = "50%";
        modal.style.top = "50%";
        modal.style.transform = "translate(-50%, -50%)";
    }

    function closeModal() {
        if (typeof window.closeComposeModal === "function") {
            window.closeComposeModal();
            return;
        }

        if (window.parent && window.parent !== window && typeof window.parent.closeComposeModal === "function") {
            window.parent.closeComposeModal();
            return;
        }

        navigateAfterSubmit("/profile");
    }

    function openThumbPicker(event) {
        if (event) {
            event.preventDefault();
            event.stopPropagation();
        }

        if (thumbInput) {
            thumbInput.click();
        }
    }

    function navigateAfterSubmit(url) {
        let targetUrl = url || (galleryId ? "/gallery/" + galleryId : "/profile");

        if (window.top && window.top !== window) {
            window.top.location.href = targetUrl;
            return;
        }

        window.location.href = targetUrl;
    }

    function setCaretEnd(node) {
        let range;
        let sel;

        if (!node || !window.getSelection || !document.createRange) {
            return;
        }

        range = document.createRange();
        range.selectNodeContents(node);
        range.collapse(false);
        sel = window.getSelection();
        sel.removeAllRanges();
        sel.addRange(range);
    }

    function getText(node, max) {
        let text = "";

        if (!node) {
            return text;
        }

        text = node.textContent.replace(/\u00a0/g, " ").replace(/\s+\n/g, "\n");

        if (text.length > max) {
            text = text.slice(0, max);
            node.textContent = text;
            setCaretEnd(node);
        }

        return text.trim();
    }

    function setCount(node, countNode, max) {
        let text = getText(node, max);
        countNode.textContent = String(text.length) + "/" + String(max);
        return text;
    }

    function setTitleError(show) {
        if (!titleError) {
            titleBox.setAttribute("aria-invalid", show ? "true" : "false");
            return;
        }

        titleError.hidden = !show;
        titleBox.setAttribute("aria-invalid", show ? "true" : "false");
    }

    function clearPreviewUrl() {
        if (previewUrl) {
            URL.revokeObjectURL(previewUrl);
            previewUrl = "";
        }
    }

    function setThumbError(message) {
        thumbError.textContent = message || "";
        thumbError.hidden = !message;
    }

    function syncCreateBtn() {
        let hasTitle = setCount(titleBox, titleCount, 150).length > 0;
        createBtn.disabled = isSubmitting || !(hasTitle && thumbOk);
    }

    function syncGalleryLink(url) {
        if (!galleryLinkUrl) {
            return;
        }

        galleryLinkUrl.textContent = url || "";
        galleryLinkUrl.href = url || "#";
    }

    function copyGalleryLink() {
        if (!galleryLinkUrl || !navigator.clipboard || !navigator.clipboard.writeText) {
            return;
        }

        navigator.clipboard.writeText(galleryLinkUrl.textContent.trim()).catch(function () {
        });
    }

    function openArtworkModal() {
        if (!artworkDialogBackdrop) {
            return;
        }

        artworkDialogBackdrop.hidden = false;
    }

    function closeArtworkModal() {
        if (!artworkDialogBackdrop) {
            return;
        }

        artworkDialogBackdrop.hidden = true;
    }

    function getArtworkCheckboxes() {
        if (!artworkList) {
            return [];
        }

        return Array.prototype.slice.call(artworkList.querySelectorAll(".artwork-checkbox"));
    }

    function getSelectedArtworkData() {
        return getArtworkCheckboxes().filter(function (checkbox) {
            return checkbox.checked;
        }).map(function (checkbox) {
            let row = checkbox.closest(".artwork-row");
            let titleNode = row ? row.querySelector(".artwork-row-title") : null;
            let title = titleNode ? titleNode.textContent.trim() : checkbox.value;
            let link = row ? row.getAttribute("data-link") : "#";
            let thumb = row ? row.getAttribute("data-thumb") : "";

            return {
                title: title,
                link: link || "#",
                thumb: thumb || ""
            };
        });
    }

    function renderSelectedArtworks() {
        let selectedData = getSelectedArtworkData();
        let selectedItems = selectedData.map(function (item) {
            return '<div class="artwork-selection-item">' +
                '<span class="artwork-thumb artwork-thumb-selection">' +
                '<img src="' + item.thumb + '" alt="' + item.title + ' 썸네일">' +
                '</span>' +
                '<div class="artwork-selection-title">' + item.title + '</div>' +
                '</div>';
        }).join("");

        if (!selectedArtworkList) {
            return;
        }

        selectedArtworkList.innerHTML = selectedItems || '<div class="artwork-selection-empty">선택한 작품이 여기에 표시됩니다.</div>';

        if (confirmArtworkModalBtn) {
            confirmArtworkModalBtn.disabled = !selectedItems;
        }
    }

    function renderSelectedArtworkLinks() {
        let selectedData = committedArtworkLinks;
        let items;

        if (!selectedArtworkLinks) {
            return;
        }

        items = selectedData.map(function (item) {
            return '<div class="selected-artwork-link-item">' +
                '<span class="artwork-thumb artwork-thumb-link">' +
                '<img src="' + item.thumb + '" alt="' + item.title + ' 썸네일">' +
                '</span>' +
                '<div class="selected-artwork-link-content">' +
                '<div class="selected-artwork-link-title">' + item.title + '</div>' +
                '<a href="' + item.link + '">' + item.link + '</a>' +
                '</div>' +
                '</div>';
        }).join("");

        selectedArtworkLinks.innerHTML = items || '<div class="selected-artwork-empty">추가된 작품 링크가 여기에 표시됩니다.</div>';
    }

    function filterArtworks() {
        let keyword = artworkSearchInput ? artworkSearchInput.value.trim().toLowerCase() : "";

        getArtworkCheckboxes().forEach(function (checkbox) {
            let row = checkbox.closest(".artwork-row");
            let titleNode = row ? row.querySelector(".artwork-row-title") : null;
            let title = titleNode ? titleNode.textContent.toLowerCase() : "";

            if (!row) {
                return;
            }

            row.hidden = !!keyword && title.indexOf(keyword) === -1;
        });
    }

    function renderTags() {
        let chips = "";

        if (!tagList) {
            return;
        }

        chips = tags.map(function (tag, index) {
            return '<span class="tag-chip">' +
                '<span>#' + tag + '</span>' +
                '<button type="button" data-tag-index="' + index + '" aria-label="' + tag + ' 삭제">x</button>' +
                '</span>';
        }).join("");

        tagList.innerHTML = chips;
    }

    function escapeHtml(value) {
        return String(value || "")
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/\"/g, "&quot;")
            .replace(/'/g, "&#39;");
    }

    function normalizeSelectedTagName(tagName) {
        let normalized = (tagName || "").trim();

        if (normalized.indexOf("#") === 0) {
            normalized = normalized.substring(1).trim();
        }

        return normalized;
    }

    function closeTagSuggestions() {
        if (!tagSuggestions) {
            return;
        }

        activeTagSuggestionIndex = -1;
        tagSuggestions.hidden = true;
        tagSuggestions.innerHTML = "";
    }

    function getTagSuggestionButtons() {
        if (!tagSuggestions) {
            return [];
        }

        return Array.prototype.slice.call(tagSuggestions.querySelectorAll(".tag-suggestion-item"));
    }

    function highlightActiveTagSuggestion() {
        getTagSuggestionButtons().forEach(function (button, index) {
            button.classList.toggle("is-active", index === activeTagSuggestionIndex);
        });
    }

    function moveActiveTagSuggestion(direction) {
        let buttons = getTagSuggestionButtons();

        if (!buttons.length) {
            return;
        }

        activeTagSuggestionIndex += direction;
        if (activeTagSuggestionIndex < 0) {
            activeTagSuggestionIndex = buttons.length - 1;
        }
        if (activeTagSuggestionIndex >= buttons.length) {
            activeTagSuggestionIndex = 0;
        }

        highlightActiveTagSuggestion();
    }

    function renderTagSuggestions(suggestions) {
        if (!tagSuggestions) {
            return;
        }

        if (!suggestions || !suggestions.length) {
            tagSuggestions.innerHTML = '<div class="tag-suggestion-empty">일치하는 태그가 없습니다.</div>';
            tagSuggestions.hidden = false;
            activeTagSuggestionIndex = -1;
            return;
        }

        tagSuggestions.innerHTML = suggestions.map(function (tag, index) {
            let tagName = escapeHtml(tag && tag.tagName ? tag.tagName : "");
            let activeClass = index === 0 ? " is-active" : "";
            return '<button type="button" class="tag-suggestion-item' + activeClass + '" data-tag-name="' + tagName + '">#' + tagName + '</button>';
        }).join("");
        tagSuggestions.hidden = false;
        activeTagSuggestionIndex = 0;
    }

    function fetchTagSuggestions() {
        let keyword = tagInput ? normalizeSelectedTagName(tagInput.value) : "";
        let requestSeq;

        if (!tagSuggestions) {
            return;
        }

        if (tagSuggestionAbortController) {
            tagSuggestionAbortController.abort();
            tagSuggestionAbortController = null;
        }

        if (!keyword) {
            closeTagSuggestions();
            return;
        }

        requestSeq = ++tagSuggestionRequestSeq;
        tagSuggestionAbortController = new AbortController();

        fetch("/api/works/tags/suggestions?keyword=" + encodeURIComponent(keyword), {
            signal: tagSuggestionAbortController.signal
        })
            .then(function (response) {
                if (!response.ok) {
                    throw new Error("tag suggestions failed");
                }
                return response.json();
            })
            .then(function (suggestions) {
                if (requestSeq !== tagSuggestionRequestSeq) {
                    return;
                }
                renderTagSuggestions(Array.isArray(suggestions) ? suggestions : []);
            })
            .catch(function (error) {
                if (error && error.name === "AbortError") {
                    return;
                }
                closeTagSuggestions();
            });
    }

    function addTag(rawValue) {
        let value = normalizeSelectedTagName(rawValue);

        if (!value || tags.indexOf(value) > -1) {
            return;
        }

        selectedExistingTagNames[value] = true;
        tags.push(value);
        renderTags();
    }

    function applyTagSuggestion(tagName) {
        addTag(tagName);
        if (tagInput) {
            tagInput.value = "";
            tagInput.focus();
        }
        closeTagSuggestions();
    }

    function getSelectedWorkIds() {
        return getArtworkCheckboxes().filter(function (checkbox) {
            return checkbox.checked;
        }).map(function (checkbox) {
            let numericId = Number(checkbox.value);
            return Number.isNaN(numericId) ? null : numericId;
        }).filter(function (workId) {
            return workId !== null;
        });
    }

    function setSubmitting(submitting) {
        isSubmitting = submitting;
        createBtn.textContent = submitting ? (isEditMode ? "수정 중..." : "만드는 중...") : (isEditMode ? "수정하기" : "만들기");
        syncCreateBtn();
    }

    function submitGallery() {
        let formData;
        let title = setCount(titleBox, titleCount, 150);
        let description = setCount(descBox, descCount, 500);
        let coverFile = thumbInput.files && thumbInput.files[0];
        let workIds = getSelectedWorkIds();

        if (!title) {
            setTitleError(true);
            titleBox.focus();
            return;
        }

        if (!coverFile && !isEditMode) {
            setThumbError("대표 이미지를 업로드해 주세요.");
            return;
        }

        formData = new FormData();
        formData.append("title", title);
        formData.append("description", description);
        if (coverFile) {
            formData.append("coverFile", coverFile);
        }

        tags.forEach(function (tag) {
            formData.append("tagNames", tag);
        });

        workIds.forEach(function (workId) {
            formData.append("workIds", String(workId));
        });

        setSubmitting(true);

        fetch(isEditMode ? "/api/galleries/" + galleryId + "/edit" : "/api/galleries", {
            method: "POST",
            body: formData
        })
            .then(function (response) {
                if (response.redirected) {
                    throw new Error("login required");
                }

                if (!response.ok) {
                    return response.text().then(function (message) {
                        throw new Error(message || "예술관 생성에 실패했습니다.");
                    });
                }

                if (isEditMode) {
                    return { redirectUrl: "/gallery/" + galleryId };
                }

                return response.json();
            })
            .then(function (data) {
                let redirectUrl = data && data.redirectUrl;
                navigateAfterSubmit(redirectUrl || "/profile");
            })
            .catch(function (error) {
                if (error.message === "login required") {
                    window.alert("로그인이 필요합니다.");
                    navigateAfterSubmit("/");
                    return;
                }

                window.alert(error.message || (isEditMode ? "예술관 수정 중 오류가 발생했습니다." : "예술관 생성 중 오류가 발생했습니다."));
            })
            .finally(function () {
                setSubmitting(false);
            });
    }

    function resetThumb() {
        clearPreviewUrl();
        thumbOk = false;
        thumbInput.value = "";
        if (thumbPreviewImage) {
            thumbPreviewImage.removeAttribute("src");
        }
        if (thumbShell) {
            thumbShell.classList.remove("is-filled");
        }
        if (thumbLinkMeta) {
            thumbLinkMeta.hidden = true;
        }
        syncGalleryLink("");
        setThumbError("");
        syncCreateBtn();
    }

    function applyThumb(file) {
        clearPreviewUrl();
        previewUrl = URL.createObjectURL(file);
        thumbOk = true;
        if (thumbPreviewImage) {
            thumbPreviewImage.src = previewUrl;
        }
        if (thumbShell) {
            thumbShell.classList.add("is-filled");
        }
        if (thumbLinkMeta) {
            thumbLinkMeta.hidden = false;
        }
        syncGalleryLink(previewUrl);
        setThumbError("");
        syncCreateBtn();
    }

    function onThumbFile(file) {
        if (!file) {
            resetThumb();
            return;
        }

        if (!/^image\/(jpeg|png|gif)$/i.test(file.type)) {
            thumbOk = false;
            setThumbError("JPG, PNG 또는 GIF 파일만 업로드할 수 있습니다.");
            syncCreateBtn();
            return;
        }

        applyThumb(file);
    }

    closeBtn.addEventListener("click", closeModal);

    titleBox.addEventListener("input", function () {
        let hasTitle = setCount(titleBox, titleCount, 150).length > 0;
        setTitleError(!hasTitle);
        syncCreateBtn();
    });

    titleBox.addEventListener("blur", function () {
        setTitleError(setCount(titleBox, titleCount, 150).length === 0);
    });

    descBox.addEventListener("input", function () {
        setCount(descBox, descCount, 500);
    });

    if (thumbBtn) {
        thumbBtn.addEventListener("click", openThumbPicker);
    }

    if (thumbShell) {
        thumbShell.addEventListener("click", openThumbPicker);
    }

    if (galleryLinkUrl) {
        galleryLinkUrl.addEventListener("click", function (event) {
            event.preventDefault();
            event.stopPropagation();
            copyGalleryLink();
        });
    }

    if (galleryLinkCopyButton) {
        galleryLinkCopyButton.addEventListener("click", function (event) {
            event.preventDefault();
            event.stopPropagation();
            copyGalleryLink();
        });
    }

    thumbInput.addEventListener("change", function () {
        onThumbFile(thumbInput.files && thumbInput.files[0]);
    });

    if (openArtworkModalBtn) {
        openArtworkModalBtn.addEventListener("click", openArtworkModal);
    }

    if (closeArtworkModalBtn) {
        closeArtworkModalBtn.addEventListener("click", closeArtworkModal);
    }

    if (cancelArtworkModalBtn) {
        cancelArtworkModalBtn.addEventListener("click", closeArtworkModal);
    }

    if (confirmArtworkModalBtn) {
        confirmArtworkModalBtn.addEventListener("click", function () {
            committedArtworkLinks = getSelectedArtworkData();
            renderSelectedArtworkLinks();
            closeArtworkModal();
        });
    }

    if (artworkDialogBackdrop) {
        artworkDialogBackdrop.addEventListener("click", function (event) {
            if (event.target === artworkDialogBackdrop) {
                closeArtworkModal();
            }
        });
    }

    if (artworkList) {
        artworkList.addEventListener("change", function (event) {
            if (event.target && event.target.classList.contains("artwork-checkbox")) {
                renderSelectedArtworks();
            }
        });
    }

    if (artworkSearchInput) {
        artworkSearchInput.addEventListener("input", filterArtworks);
    }

    if (tagInput && tagList) {
        tagInput.addEventListener("input", fetchTagSuggestions);

        tagInput.addEventListener("keydown", function (event) {
            let buttons = getTagSuggestionButtons();
            let activeButton;

            if (tagSuggestions && !tagSuggestions.hidden && buttons.length) {
                if (event.key === "ArrowDown") {
                    event.preventDefault();
                    moveActiveTagSuggestion(1);
                    return;
                }

                if (event.key === "ArrowUp") {
                    event.preventDefault();
                    moveActiveTagSuggestion(-1);
                    return;
                }

                if ((event.key === "Enter" || event.key === ",") && activeTagSuggestionIndex >= 0) {
                    event.preventDefault();
                    activeButton = buttons[activeTagSuggestionIndex];
                    if (activeButton) {
                        applyTagSuggestion(activeButton.getAttribute("data-tag-name"));
                    }
                    return;
                }

                if (event.key === "Escape") {
                    closeTagSuggestions();
                    return;
                }
            }

            if (event.key === "Enter" || event.key === ",") {
                event.preventDefault();
                fetchTagSuggestions();
            }

            if (event.key === "Backspace" && !tagInput.value && tags.length) {
                tags.pop();
                renderTags();
            }
        });

        tagInput.addEventListener("blur", function () {
            window.setTimeout(closeTagSuggestions, 120);
        });

        tagList.addEventListener("click", function (event) {
            let button = event.target.closest("button[data-tag-index]");
            let index;

            if (!button) {
                return;
            }

            index = Number(button.getAttribute("data-tag-index"));

            if (Number.isNaN(index)) {
                return;
            }

            tags.splice(index, 1);
            renderTags();
            tagInput.focus();
        });
    }

    if (tagSuggestions) {
        tagSuggestions.addEventListener("mousedown", function (event) {
            let button = event.target.closest(".tag-suggestion-item");

            if (!button) {
                return;
            }

            event.preventDefault();
            applyTagSuggestion(button.getAttribute("data-tag-name"));
        });
    }

    createBtn.addEventListener("click", function () {
        setTitleError(setCount(titleBox, titleCount, 150).length === 0);
        syncCreateBtn();
        if (!createBtn.disabled) {
            submitGallery();
        }
    });

    if (isEditMode) {
        titleBox.textContent = initialGalleryData.title || "";
        descBox.textContent = initialGalleryData.description || "";

        getArtworkCheckboxes().forEach(function (checkbox) {
            checkbox.checked = Array.isArray(initialGalleryData.workIds) && initialGalleryData.workIds.indexOf(Number(checkbox.value)) > -1;
        });

        committedArtworkLinks = getSelectedArtworkData();

        if (initialGalleryData.coverImage && thumbPreviewImage) {
            thumbPreviewImage.src = initialGalleryData.coverImage;
            thumbOk = true;
            if (thumbShell) {
                thumbShell.classList.add("is-filled");
            }
            if (thumbLinkMeta) {
                thumbLinkMeta.hidden = false;
            }
            syncGalleryLink(initialGalleryData.coverImage);
        }
    }

    setCount(titleBox, titleCount, 150);
    setCount(descBox, descCount, 500);
    if (!isEditMode) {
        resetThumb();
    }
    renderTags();
    renderSelectedArtworks();
    renderSelectedArtworkLinks();
    if (!isEditMode) {
        syncGalleryLink("");
    }
    setTitleError(false);
    syncCreateBtn();
}

if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initializeGalleryRegister);
} else {
    initializeGalleryRegister();
}

window.initializeGalleryRegister = initializeGalleryRegister;
