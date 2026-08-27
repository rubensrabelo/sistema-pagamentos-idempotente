import http from "k6/http";
import { check, sleep } from "k6";

export const options = {
    stages: [
        { duration: "10s", target: 100 },   // Rampa 1: Acelera até 100 usuários
        { duration: "20s", target: 1000 },  // Rampa 2: Estressa subindo até 1.000 usuários
        { duration: "20s", target: 10000 }, // Rampa 3: Pico extremo com 10.000 conexões simultâneas
        { duration: "10s", target: 0 },     // Desaceleração: Reduz e limpa as conexões até zerar
    ],
};

const poolDeChaves = [
    "chave-alfa", "chave-beta", "chave-gama", "chave-delta",
    "chave-omega", "chave-sigma", "chave-zeta", "chave-iota"
];

export default function () {
    const url = "http://localhost:8080/api/v1/payments";
    
    // Prefixos fixos colidem de propósito nas threads para testar as travas do banco
    const chaveBase = poolDeChaves[Math.floor(Math.random() * poolDeChaves.length)];

    const payload = JSON.stringify({
        amount: 150.00
    });

    const params = {
        headers: {
            "Content-Type": "application/json",
            // O sufixo por usuário virtual (__VU) garante pulverização massiva sob concorrência controlada
            "X-Idempotency-Key": `${chaveBase}-${__VU}`, 
        },
    };

    const res = http.post(url, payload, params);

    // Aceita status 201 (Sucesso) ou 425/409 (Bloqueios previstos pela máquina de estados)
    check(res, {
        "status is valid (201, 409 or 425)": (r) => r.status === 201 || r.status === 425 || r.status === 409,
    });

    sleep(0.05); // Pausa de 50ms força vazão extrema contra a API
}
