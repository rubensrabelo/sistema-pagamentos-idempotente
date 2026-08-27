import http from "k6/http";
import { check, sleep } from "k6";

export const options = {
    stages: [
        { duration: "10s", target: 100 },
        { duration: "20s", target: 1000 },
        { duration: "20s", target: 10000 },
        { duration: "10s", target: 0 },
    ],
};

const poolDeChaves = [
    "chave-alfa", "chave-beta", "chave-gama", "chave-delta",
    "chave-omega", "chave-sigma", "chave-zeta", "chave-iota"
];

export default function () {
    const url = "http://localhost:8080/api/v1/payments";
    
    const chaveBase = poolDeChaves[Math.floor(Math.random() * poolDeChaves.length)];

    const payload = JSON.stringify({
        amount: 150.00
    });

    const params = {
        headers: {
            "Content-Type": "application/json",
            "X-Idempotency-Key": `${chaveBase}-${__VU}`, 
        },
    };

    const res = http.post(url, payload, params);

    check(res, {
        "status is valid (201, 409 or 425)": (r) => r.status === 201 || r.status === 425 || r.status === 409,
    });

    sleep(0.05);
}
