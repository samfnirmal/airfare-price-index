# Real-time Airfare Price Index for India (SIH26056)
**Organization:** Ministry of Statistics and Programme Implementation (MoSPI)  
**Category:** Software / Economic Data Engineering  
**Team Name:** `whoisjson`  
**Repository:** [https://github.com/samfnirmal/airfare-price-index](https://github.com/samfnirmal/airfare-price-index)

---

## 📌 Problem Overview
Modern airline pricing is dynamic, non-linear, and opaque. Building an official, high-scale statistical pipeline to calculate the **Airfare Price Index (API)** requires harvesting dynamic ticket prices across all major domestic corridors (e.g., DEL–BOM, BLR–MAA, DEL–BLR) across advance booking windows ($D-0, D-1, D-7, D-15, D-30$), filtering noise, and computing official economic inflation indices (Jevons geometric mean and Laspeyres price indices).

---

## 🏗️ Architecture & Technology Stack

```
[Playwright Scraper + Tor/Privoxy Proxy (:8118)]
                    │
                    ▼
       [Apache Kafka (flight-fares-raw)]
                    │
          ┌─────────┴─────────┐
          ▼                   ▼
[TimescaleDB Hypertables]  [Statistical Stream Engine]
(Raw Fares & Index Hist)    (Jevons & Laspeyres Indices)
                              │
                              ▼
                       [Redis Cache] ──▶ [Spring Boot REST API] ──▶ [MoSPI Dashboard]
```

- **Scraping & Anti-Bot:** Playwright Java (headless Chromium) + Tor/Privoxy (`localhost:8118` rotating HTTP proxy with automatic IP rotation).
- **Streaming & Ingestion:** Apache Kafka (KRaft mode on `localhost:9092`).
- **Persistence:** TimescaleDB (PostgreSQL 16 on `localhost:5432` with time-partitioned hypertables for raw prices and index metrics).
- **Cache:** Redis 7 (`localhost:6379`) for sub-millisecond real-time index retrieval.
- **Backend Core:** Java 21, Spring Boot 3 with Virtual Threads, jOOQ.

---

## 🚀 Quick Start

### 1. Start Infrastructure Services
```bash
docker compose up -d
```
This boots up:
- `whoisjson-tor-proxy` on port `8118`
- `whoisjson-timescaledb` on port `5432`
- `whoisjson-kafka` on port `9092`
- `whoisjson-redis` on port `6379`

### 2. Apply TimescaleDB Schema
```bash
docker compose exec -i timescaledb psql -U whoisjson_user -d airfare_db < src/main/resources/schema.sql
```

### 3. Run the Spring Boot Application
```bash
./mvnw spring-boot:run
```
