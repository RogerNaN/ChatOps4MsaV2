import http from 'k6/http';
import {sleep} from 'k6';

export const options = {
  // Key configurations for Stress in this section
  stages: [
    { duration: '5s', target: 200 }, // traffic ramp-up from 1 to a higher 150 users over 5s.
    { duration: '20s', target: 200 }, // stay at higher 150 users for 20s
    { duration: '5s', target: 0 }, // ramp-down to 0 users
  ],
      thresholds: {
        // 期望在整個測試執行過程中，錯誤率必須低於 5%
        http_req_failed: ["rate<0.05"],
        // 平均請求必須在 300ms 內完成，90% 的請求必須在 200ms 內完成
        http_req_duration: ["avg < 300", "p(90) < 100"],
      }

};

export default () => {
  // const testUrl = "https://test-api.k6.io";
  const testUrl = __ENV.TEST_URL;
  http.get(testUrl);
  //check(res, { 'status was 200': (r) => r.status == 200 });
  sleep(1);
};
