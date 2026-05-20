// 워터마크 검증 페이지 — 파일 선택/드래그 → /api/watermark/verify 호출 → 결과 렌더.

(function () {
    'use strict';

    const form = document.getElementById('watermarkForm');
    if (!form) return;

    const fileInput = document.getElementById('watermarkFile');
    const drop = form.querySelector('[data-watermark-drop]');
    const dropInner = form.querySelector('[data-watermark-drop-inner]');
    const preview = form.querySelector('[data-watermark-preview]');
    const previewImage = form.querySelector('[data-watermark-preview-image]');
    const previewVideo = form.querySelector('[data-watermark-preview-video]');
    const previewName = form.querySelector('[data-watermark-preview-name]');
    const submitBtn = form.querySelector('[data-watermark-submit]');

    const result = document.querySelector('[data-watermark-result]');
    const status = result.querySelector('[data-watermark-status]');
    const creator = result.querySelector('[data-watermark-creator]');
    const creatorLink = result.querySelector('[data-watermark-creator-link]');
    const creatorImage = result.querySelector('[data-watermark-creator-image]');
    const creatorName = result.querySelector('[data-watermark-creator-name]');
    const creatorId = result.querySelector('[data-watermark-creator-id]');
    const creatorVerified = result.querySelector('[data-watermark-creator-verified]');
    const messageBox = result.querySelector('[data-watermark-message]');

    function showPreview(file) {
        if (!file) {
            dropInner.hidden = false;
            preview.hidden = true;
            previewImage.hidden = true;
            previewVideo.hidden = true;
            previewImage.src = '';
            if (previewVideo.src) {
                URL.revokeObjectURL(previewVideo.src);
                previewVideo.removeAttribute('src');
                previewVideo.load();
            }
            return;
        }
        const url = URL.createObjectURL(file);
        const isVideo = (file.type || '').startsWith('video/');
        dropInner.hidden = true;
        preview.hidden = false;
        if (isVideo) {
            previewVideo.src = url;
            previewVideo.hidden = false;
            previewImage.hidden = true;
            previewImage.src = '';
        } else {
            previewImage.src = url;
            previewImage.hidden = false;
            previewVideo.hidden = true;
            if (previewVideo.src) previewVideo.removeAttribute('src');
        }
        previewName.textContent = file.name;
    }

    fileInput.addEventListener('change', () => {
        result.hidden = true;
        showPreview(fileInput.files[0] || null);
    });

    ['dragenter', 'dragover'].forEach(evt =>
        drop.addEventListener(evt, e => {
            e.preventDefault();
            drop.classList.add('is-dragover');
        })
    );
    ['dragleave', 'drop'].forEach(evt =>
        drop.addEventListener(evt, e => {
            e.preventDefault();
            drop.classList.remove('is-dragover');
        })
    );
    drop.addEventListener('drop', e => {
        const file = e.dataTransfer?.files?.[0];
        if (!file) return;
        const dt = new DataTransfer();
        dt.items.add(file);
        fileInput.files = dt.files;
        result.hidden = true;
        showPreview(file);
    });

    function renderStatus(kind, label) {
        status.className = 'Watermark-Status';
        status.classList.add(kind);
        status.textContent = label;
    }

    function renderCreator(creatorData) {
        if (!creatorData) {
            creator.hidden = true;
            return;
        }
        creator.hidden = false;
        creatorLink.href = creatorData.profileUrl || '#';
        creatorImage.src = creatorData.profileImage || '/images/default-profile.svg';
        creatorImage.onerror = function () {
            this.onerror = null;
            this.src = '/images/default-profile.svg';
        };
        creatorName.textContent = creatorData.nickname || '(닉네임 없음)';
        creatorId.textContent = '작가 ID: ' + creatorData.id;
        creatorVerified.hidden = !creatorData.creatorVerified;
    }

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        if (!fileInput.files || !fileInput.files[0]) return;

        submitBtn.disabled = true;
        submitBtn.classList.add('is-loading');
        const originalText = submitBtn.textContent;
        submitBtn.textContent = '검증 중...';
        result.hidden = true;
        creator.hidden = true;

        const fd = new FormData();
        fd.append('file', fileInput.files[0]);

        try {
            const res = await fetch('/api/watermark/verify', {
                method: 'POST',
                body: fd,
                credentials: 'same-origin',
            });

            const data = await res.json().catch(() => ({}));

            if (!res.ok) {
                renderStatus('is-error', '검증 실패');
                messageBox.textContent = data.error || data.message || ('서버 오류 (' + res.status + ')');
                creator.hidden = true;
                result.hidden = false;
                return;
            }

            if (data.valid) {
                renderStatus('is-valid', '워터마크 확인됨');
            } else {
                renderStatus('is-invalid', '워터마크 없음');
            }
            renderCreator(data.creator);
            messageBox.textContent = data.message || '';
            result.hidden = false;
        } catch (err) {
            renderStatus('is-error', '네트워크 오류');
            messageBox.textContent = '서버에 연결할 수 없습니다.';
            creator.hidden = true;
            result.hidden = false;
        } finally {
            submitBtn.disabled = false;
            submitBtn.classList.remove('is-loading');
            submitBtn.textContent = originalText;
        }
    });
})();
