// Load test: many people browsing and searching at the same time (the most common traffic).
//
//   docker run --rm --add-host host.docker.internal:host-gateway \
//     -v "$PWD/load-tests:/scripts" grafana/k6:0.54.0 run /scripts/browse.js
//
// Options (pass with -e NAME=value):  BASE_URL (default http://host.docker.internal:8080),
// PEAK_VUS (default 30) = how many simultaneous virtual users at the busiest point,
// THINK=0 = no pause between requests (a stress test that finds the maximum throughput).

import http from 'k6/http'
import { check, sleep } from 'k6'

const BASE = __ENV.BASE_URL || 'http://host.docker.internal:8080'
const PEAK = Number(__ENV.PEAK_VUS || 30)
const THINK = __ENV.THINK !== '0'

export const options = {
  scenarios: {
    browsing: {
      executor: 'ramping-vus',
      stages: [
        { duration: '20s', target: PEAK }, // ramp up
        { duration: '60s', target: PEAK }, // hold at peak
        { duration: '10s', target: 0 }, // ramp down
      ],
    },
  },
  // The test FAILS if these aren't met.
  thresholds: {
    http_req_failed: ['rate<0.01'], // under 1% errors
    'http_req_duration{name:search}': ['p(95)<300'],
    'http_req_duration{name:list}': ['p(95)<300'],
    'http_req_duration{name:detail}': ['p(95)<200'],
    'http_req_duration{name:nearby}': ['p(95)<300'],
    'http_req_duration{name:matches}': ['p(95)<500'],
  },
}

const WORDS = ['iPhone', '皮夾', '錢包', '鑰匙', '學生證', '雨傘', '耳機', '後背包', '黑色', '圖書館', '餐廳', '捷運']
const PLACES = [
  [25.0173, 121.5397],
  [25.0158, 121.5385],
  [25.0215, 121.5352],
  [25.0147, 121.5343],
]
const pick = (list) => list[Math.floor(Math.random() * list.length)]

/** Runs once before the test: collect real item ids to open. */
export function setup() {
  const ids = []
  for (let page = 0; page < 5; page++) {
    const res = http.get(`${BASE}/api/items?size=50&page=${page}`)
    check(res, { 'setup list ok': (r) => r.status === 200 })
    for (const item of res.json('content')) ids.push(item.id)
  }
  if (ids.length === 0) throw new Error('No items found. Load test data first: see load-tests/seed.sql')
  return { ids }
}

export default function ({ ids }) {
  const roll = Math.random()
  let res

  if (roll < 0.30) {
    const type = pick(['', 'LOST', 'FOUND'])
    const category = pick(['', '1', '2', '3', '4', '5', '6'])
    res = http.get(`${BASE}/api/items?type=${type}&categoryId=${category}&page=${Math.floor(Math.random() * 3)}&size=12`,
      { tags: { name: 'list' } })
  } else if (roll < 0.55) {
    res = http.get(`${BASE}/api/items?q=${encodeURIComponent(pick(WORDS))}&size=12`, { tags: { name: 'search' } })
  } else if (roll < 0.65) {
    const [lat, lng] = pick(PLACES)
    res = http.get(`${BASE}/api/items?lat=${lat}&lng=${lng}&radiusKm=${pick([1, 3])}&size=12`, { tags: { name: 'nearby' } })
  } else if (roll < 0.90) {
    res = http.get(`${BASE}/api/items/${pick(ids)}`, { tags: { name: 'detail' } })
  } else {
    res = http.get(`${BASE}/api/items/${pick(ids)}/matches`, { tags: { name: 'matches' } })
  }

  check(res, { 'status is 200': (r) => r.status === 200 })
  if (THINK) sleep(0.2 + Math.random() * 0.6) // a real person pauses between clicks
}
