/* [모델 분류] 추천 (Recommendation) UI 호출 — 마이페이지 추천 작가 캐러셀.
   cf. js/profile/profile-growth.js (회귀), js/main/main-curation.js (분류). */
(function () {
  let section   = document.getElementById('recoStripSection');
  let track     = document.getElementById('recoStripTrack');
  let skeleton  = document.getElementById('recoStripSkeleton');
  let subEl     = document.getElementById('recoStripSub');
  let toggleBtn = document.getElementById('recoToggleBtn');
  if (!section || !track || !toggleBtn) return;

  function escapeHtml(value) {
    if (value == null) return '';
    return String(value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function fmt(n) {
    if (n == null) return '0';
    return Number(n).toLocaleString('ko-KR');
  }

  function reasonLabel(reason) {
    if (reason === 'CF')      return '비슷한 취향';
    if (reason === 'POPULAR') return '인기 작가';
    if (reason === 'SIMILAR') return '비슷한 작가';
    return reason || '';
  }

  function buildCard(item) {
    let nickname   = escapeHtml(item.nickname || '익명');
    let profileUrl = item.nickname
      ? '/profile/' + encodeURIComponent(item.nickname)
      : 'javascript:void(0)';
    let initial    = (item.nickname || '?').slice(0, 1);
    let img        = item.profile_image;
    let reason     = item.reason || 'CF';
    let followers  = fmt(item.follower_count);
    let verified   = item.creator_verified;

    let avatar = img
      ? '<img src="' + escapeHtml(img) + '" alt="' + nickname + '" loading="lazy" ' +
        'onerror="this.onerror=null;this.parentNode.innerHTML=\'<span class=\\\'recoCard__avatar-text\\\'>' +
        escapeHtml(initial) + '</span>\'" />'
      : '<span class="recoCard__avatar-text">' + escapeHtml(initial) + '</span>';

    let verifiedDot = verified
      ? '<span class="recoCard__verified" title="인증 작가">✓</span>'
      : '';

    return (
      '<a class="recoCard" href="' + profileUrl + '">' +
        '<div class="recoCard__avatar">' + avatar + verifiedDot + '</div>' +
        '<div class="recoCard__name">' + nickname + '</div>' +
        '<div class="recoCard__followers">팔로워 ' + followers + '명</div>' +
        '<span class="recoCard__reason recoCard__reason--' + reason + '">' +
          reasonLabel(reason) +
        '</span>' +
      '</a>'
    );
  }

  function render(data) {
    if (skeleton) skeleton.remove();
    let items = (data && data.items) || [];
    if (!items.length) {
      section.classList.add('recoStrip--hidden');
      return;
    }
    track.innerHTML = items.map(buildCard).join('');
    if (subEl) {
      subEl.textContent = data.cold_start
        ? '아직 팔로우가 없어 인기 작가를 보여드려요'
        : '당신과 취향이 비슷한 사용자들의 follow 기반';
    }
  }

  function handleError(err) {
    console.warn('[reco] 호출 실패:', err);
    section.classList.add('recoStrip--hidden');
  }

  // ── 토글 동작 — 첫 열림 때만 lazy fetch ──
  let loaded = false;

  function open() {
    section.classList.remove('recoStrip--collapsed');
    toggleBtn.setAttribute('aria-expanded', 'true');
    if (loaded) return;
    loaded = true;
    fetch('/api/recommend/creators/me?k=10', {
      method: 'GET',
      headers: { 'Accept': 'application/json' },
      credentials: 'same-origin'
    })
      .then(function (res) {
        if (!res.ok) throw new Error('HTTP ' + res.status);
        return res.json();
      })
      .then(render)
      .catch(handleError);
  }

  function close() {
    section.classList.add('recoStrip--collapsed');
    toggleBtn.setAttribute('aria-expanded', 'false');
  }

  toggleBtn.addEventListener('click', function () {
    if (section.classList.contains('recoStrip--collapsed')) open();
    else close();
  });
})();

recommendAuthor = document.getElementById('recoStripSection');

