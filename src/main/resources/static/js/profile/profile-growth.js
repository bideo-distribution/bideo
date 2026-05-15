/* [모델 분류] 회귀 (Regression) UI 호출 — 마이페이지 팔로워 성장 예측.
   cf. js/work/work-register-prediction.js 는 분류 (낙찰 확률) 카드. */
(function () {
  let card = document.getElementById('growthCard');
  if (!card) return;

  let gradeEl     = document.getElementById('growthCardGrade');
  let predictedEl = document.getElementById('growthCardPredicted');
  let deltaEl     = document.getElementById('growthCardDelta');
  let ciEl        = document.getElementById('growthCardCi');
  let noteEl      = document.getElementById('growthCardNote');

  function fmt(n) {
    if (n == null) return '--';
    return Number(n).toLocaleString('ko-KR');
  }

  function setError(msg) {
    card.classList.remove('growthCard--loading');
    card.classList.add('growthCard--error');
    if (noteEl) noteEl.textContent = msg || '예측 서버 연결 실패';
  }

  function render(data) {
    if (!data) {
      setError('예측 데이터가 없습니다.');
      return;
    }
    card.classList.remove('growthCard--loading', 'growthCard--error');

    gradeEl.textContent = data.grade || '--';
    gradeEl.className = 'growthCard__grade growthCard__grade--' + (data.grade || 'NEWCOMER');

    predictedEl.textContent = fmt(data.predicted);

    let growth = data.growth || 0;
    let sign = growth > 0 ? '+' : '';
    deltaEl.textContent = sign + fmt(growth);
    deltaEl.classList.toggle('growthCard__delta--negative', growth < 0);

    ciEl.textContent = fmt(data.ci_low) + ' ~ ' + fmt(data.ci_high) + '명 (95% 구간)';

    noteEl.textContent = '합성 데이터 기반 추정치';
  }

  fetch('/api/growth/me', {
    method: 'GET',
    headers: { 'Accept': 'application/json' },
    credentials: 'same-origin'
  })
    .then(function (res) {
      if (!res.ok) throw new Error('HTTP ' + res.status);
      return res.json();
    })
    .then(render)
    .catch(function (err) {
      console.warn('[growth] 호출 실패:', err);
      setError();
    });
})();
