/* [모델 분류] AI 큐레이션 — 메인페이지 가로 캐러셀.
   /api/prediction/curation?k=10 호출 결과를 렌더한다. */
(function () {
  let section  = document.getElementById('aiCurationSection');
  let track    = document.getElementById('aiCurationTrack');
  let skeleton = document.getElementById('aiCurationSkeleton');
  let subEl    = document.getElementById('aiCurationSub');
  if (!section || !track) return;

  function escapeHtml(value) {
    if (value == null) return '';
    return String(value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function formatPrice(v) {
    if (v == null) return '--';
    return Number(v).toLocaleString('ko-KR');
  }

  function buildCard(item) {
    let pct      = Math.round((item.score || 0) * 100);
    let won      = !!item.is_won_predicted;
    let workId   = item.work_id;
    let title    = escapeHtml(item.work_title || '제목 없음');
    let category = escapeHtml(item.work_category || '');
    let price    = formatPrice(item.starting_price);
    let img      = 'https://picsum.photos/seed/bideo-w' + workId + '/480/300';

    return (
      '<a class="ai-curation-card" href="/work/detail/' + workId + '">' +
        '<div class="ai-curation-card__thumb">' +
          '<img src="' + img + '" alt="' + title + '" loading="lazy" ' +
            'onerror="this.onerror=null;this.src=\'https://picsum.photos/seed/bideo-fallback/480/300\';" />' +
          '<span class="ai-curation-card__score' + (won ? ' ai-curation-card__score--won' : '') + '">' +
            '<span class="ai-curation-card__score-num">' + pct + '</span>' +
            '<span class="ai-curation-card__score-unit">%</span>' +
          '</span>' +
        '</div>' +
        '<div class="ai-curation-card__meta">' +
          '<div class="ai-curation-card__title">' + title + '</div>' +
          '<div class="ai-curation-card__row">' +
            '<span>' + (category || '카테고리 없음') + '</span>' +
            '<span class="ai-curation-card__price">' + price + '원</span>' +
          '</div>' +
        '</div>' +
      '</a>'
    );
  }

  function render(data) {
    if (skeleton) skeleton.remove();
    let items = (data && data.items) || [];
    if (!items.length) {
      section.classList.add('ai-curation--hidden');
      return;
    }
    track.innerHTML = items.map(buildCard).join('');
    if (subEl) {
      subEl.textContent = '진행 중 ' + (data.total_active || 0) + '건 중 Top ' + items.length;
    }
  }

  function handleError(err) {
    console.warn('[ai-curation] 호출 실패:', err);
    section.classList.add('ai-curation--hidden');
  }

  fetch('/api/prediction/curation?k=10', {
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
})();
