// 작품 등록 폼 — 가격/입찰시간 변경 시 실시간 AI 낙찰 예측 (디바운스 300ms)
(function () {
  let card           = document.getElementById('aiPredictCard');
  let priceInput     = document.getElementById('auctionBidPriceInput');
  let durationInput  = document.getElementById('auctionDeadlineHoursInput');
  let scoreEl        = document.getElementById('aiPredictCardScore');
  let barEl          = document.getElementById('aiPredictCardBarFill');
  let verdictEl      = document.getElementById('aiPredictCardVerdict');
  let registerState  = document.getElementById('work-register-state');

  if (!card || !priceInput || !durationInput) return;

  let debounceTimer = null;
  let lastReq       = null;

  function parsePrice(v) {
    if (v == null) return 0;
    return parseInt(String(v).replace(/[^0-9]/g, ''), 10) || 0;
  }

  function setIdle(msg) {
    card.classList.remove('ai-predict-card--loading', 'ai-predict-card--ready', 'ai-predict-card--won');
    card.classList.add('ai-predict-card--idle');
    scoreEl.textContent = '--';
    barEl.style.width = '0%';
    verdictEl.textContent = msg || '가격과 시간을 입력하면 예측이 갱신됩니다';
  }

  function setLoading() {
    card.classList.remove('ai-predict-card--idle', 'ai-predict-card--ready', 'ai-predict-card--won');
    card.classList.add('ai-predict-card--loading');
    verdictEl.textContent = '예측 중...';
  }

  function setResult(score, isWon) {
    let pct = Math.round((score || 0) * 100);
    card.classList.remove('ai-predict-card--idle', 'ai-predict-card--loading');
    card.classList.add('ai-predict-card--ready');
    card.classList.toggle('ai-predict-card--won', !!isWon);
    scoreEl.textContent = pct;
    barEl.style.width = pct + '%';
    verdictEl.textContent = isWon
      ? '낙찰 가능성 높음 — 좋은 조건입니다'
      : '추가 노출이 필요할 수 있어요';
  }

  function setError() {
    card.classList.remove('ai-predict-card--loading');
    card.classList.add('ai-predict-card--idle');
    verdictEl.textContent = '예측 서버에 연결할 수 없습니다';
  }

  function buildPayload() {
    let price    = parsePrice(priceInput.value);
    let hoursRaw = parseFloat(durationInput.value || '0');
    if (!price || price <= 0 || !hoursRaw || hoursRaw <= 0) return null;

    let now = new Date();
    let category = (registerState && registerState.getAttribute('data-category')) || 'unknown';

    return {
      starting_price:    price,
      duration_hours:    hoursRaw / 60, // BIDEO 는 '분' 기준 → 시간으로
      view_count:        0,
      like_count:        0,
      bookmark_count:    0,
      creator_followers: 30,    // 신규 작가 기본 가정
      creator_verified:  0,
      work_category:     category,
      started_hour:      now.getHours(),
      started_dow:       now.getDay()
    };
  }

  function trigger() {
    let payload = buildPayload();
    if (!payload) {
      setIdle();
      return;
    }
    setLoading();
    let reqKey = JSON.stringify(payload);
    lastReq = reqKey;

    fetch('/api/prediction/predict', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
      credentials: 'same-origin',
      body: reqKey
    })
      .then(function (res) {
        if (!res.ok) throw new Error('HTTP ' + res.status);
        return res.json();
      })
      .then(function (data) {
        if (lastReq !== reqKey) return; // 이미 새 요청 들어옴
        setResult(data.score, data.is_won_predicted);
      })
      .catch(function (err) {
        console.warn('[ai-predict] 호출 실패:', err);
        setError();
      });
  }

  function schedule() {
    clearTimeout(debounceTimer);
    debounceTimer = setTimeout(trigger, 350);
  }

  priceInput.addEventListener('input', schedule);
  durationInput.addEventListener('change', schedule);

  // 입찰시간 버튼 클릭 시 hidden input 의 change 가 직접 발생 안 함 → 명시적으로 hook
  document.querySelectorAll('.work-auction-config__deadline-btn').forEach(function (btn) {
    btn.addEventListener('click', function () { setTimeout(schedule, 50); });
  });

  // 초기 idle 메시지
  setIdle();
})();
