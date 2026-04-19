## 7. k6 부하 테스트 시나리오

### 7.1 시나리오 구성

```
k6/
├── scripts/
│   ├── smoke-test.js          # 소량 테스트 (정상 동작 확인)
│   ├── load-test.js           # 일반 부하 (초당 1,000건, 5분)
│   ├── stress-test.js         # 한계 테스트 (점진적 증가 → 초당 50,000건)
│   ├── spike-test.js          # 스파이크 (평소 100 → 순간 50,000)
│   └── search-test.js         # ES 검색 API 부하
└── config/
    └── thresholds.json        # 성능 기준
```

### 7.2 성능 목표

| 항목 | 기준 |
|------|------|
| 거래 수집 API (POST) | p99 < 100ms |
| 거래 검색 API (GET, ES) | p99 < 200ms |
| 처리량 (Throughput) | > 10,000 TPS |
| 에러율 | < 0.1% |
| Kafka Consumer Lag | < 1,000 |

### 7.3 k6 스크립트 예시

```javascript
// k6/scripts/load-test.js
import http from 'k6/http';
import { check } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 500 },   // ramp up
    { duration: '5m',  target: 1000 },  // 유지
    { duration: '30s', target: 0 },     // ramp down
  ],
  thresholds: {
    http_req_duration: ['p(99)<100'],
    http_req_failed: ['rate<0.001'],
  },
};

const BASE_URL = 'http://localhost:8080';

export default function () {
  const payload = JSON.stringify({
    userId: `USER_${String(Math.floor(Math.random() * 500) + 1).padStart(5, '0')}`,
    cardNumber: `4${Math.random().toString().slice(2, 14)}`,
    amount: Math.floor(Math.random() * 500000) + 1000,
    currency: 'KRW',
    merchantName: '테스트가맹점',
    merchantCategory: 'ONLINE',
    country: 'KR',
    city: '서울',
    latitude: 37.5665 + (Math.random() - 0.5) * 0.1,
    longitude: 126.978 + (Math.random() - 0.5) * 0.1,
  });

  const res = http.post(`${BASE_URL}/api/v1/transactions`, payload, {
    headers: { 'Content-Type': 'application/json' },
  });

  check(res, {
    'status is 201': (r) => r.status === 201,
    'has transactionId': (r) => JSON.parse(r.body).id !== undefined,
  });
}
```

---

