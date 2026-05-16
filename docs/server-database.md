# Server-Side Database Design

This document defines the planned server-side persistence model for the
Kuhhandel prototype: which game state is persisted, the relational schema, and
the lifetime of each table.

The mirror document for the Android client lives in
[`client-database.md`](./client-database.md) (planned).

## Scope: transient vs. persisted

Not all game state is written to the database — some is intentionally kept in
memory because losing it on a restart is acceptable for the prototype.

**Transient (in-memory only, lost on server restart):**

- the live `GameState` while a match is running, including the shuffled
  `AnimalDeck` and `MoneyDeck`
- the WebSocket session ↔ player mapping in `ConnectionRegistry`

**Persisted (writes go to PostgreSQL):**

- Room / Match — `gameId`, `status`, host, optimistic-locking version
- Players in a match — id, name, seat order
- Animal deck draws — so the match can be reconstructed after a crash
- Per-player money cards and animals
- Active auction or trade state, while the match is in those phases

## ER diagram

```mermaid
erDiagram
    USERS ||--o{ GAMES : "active_player"
    USERS ||--o{ GAME_PLAYERS : "joins as"
    GAMES ||--o{ GAME_PLAYERS : "contains"
    GAMES ||--o{ DECK_CARDS : "has deck"
    GAMES ||--o| AUCTION_STATE : "when AUCTION"
    GAMES ||--o| TRADE_STATE : "when TRADE"
    GAME_PLAYERS ||--o{ PLAYER_MONEY : "owns"
    GAME_PLAYERS ||--o{ PLAYER_ANIMALS : "owns"
    GAME_PLAYERS ||--o{ AUCTION_STATE : "highest_bidder"
    GAME_PLAYERS ||--o{ TRADE_STATE : "challenger or defender"

    USERS {
        BIGINT id PK
        VARCHAR username UK
        VARCHAR password_hash
    }
    GAMES {
        BIGINT id PK
        ENUM status "LOBBY, AUCTION, TRADE, FINISHED"
        BIGINT active_player_id FK
        INT version "optimistic lock"
    }
    GAME_PLAYERS {
        BIGINT id PK
        BIGINT game_id FK
        BIGINT user_id FK
        INT seat_order
    }
    DECK_CARDS {
        BIGINT id PK
        BIGINT game_id FK
        ENUM animal_type
        INT draw_order
    }
    PLAYER_MONEY {
        BIGINT id PK
        BIGINT player_id FK
        INT card_value
        INT amount
    }
    PLAYER_ANIMALS {
        BIGINT id PK
        BIGINT player_id FK
        ENUM animal_type
        INT amount
    }
    AUCTION_STATE {
        BIGINT game_id PK
        BIGINT game_id_fk FK
        ENUM current_animal
        INT highest_bid
        BIGINT highest_bidder_id FK
        JSON passed_players
    }
    TRADE_STATE {
        BIGINT game_id PK
        BIGINT game_id_fk FK
        BIGINT challenger_id FK
        BIGINT defender_id FK
        ENUM animal_type
        JSON challenger_offer_json
        JSON defender_offer_json
    }
```

> In the actual DDL, `auction_state.game_id` and `trade_state.game_id` are each
> a single column carrying both the primary key and a foreign key to
> `games.id`. Mermaid cannot render the dual constraint on one column, so the
> diagram splits them visually.

## Table summary

| Table | Purpose | Lifetime |
|---|---|---|
| `users` | Player accounts (username + password hash) | long-lived |
| `games` | One row per match. `status` drives which transient state table is populated. | per match |
| `game_players` | Join row between a user and a game, with the user's seat order | per match |
| `deck_cards` | Persisted draw pile per game so the match can be rebuilt after a crash | per match |
| `player_money` | Aggregated by `card_value` per player (e.g. 3× 10-bills → one row with `amount = 3`) | per match |
| `player_animals` | Same aggregation, one row per animal type a player owns | per match |
| `auction_state` | One row per game while the match is in `AUCTION` | transient sub-state |
| `trade_state` | One row per game while the match is in `TRADE` | transient sub-state |

## Design notes

- **JSON columns** (`passed_players`, `challenger_offer_json`,
  `defender_offer_json`) are chosen over fully normalised sub-tables because the
  prototype neither queries across them nor aggregates them — fewer joins,
  faster schema evolution.
- **`games.version`** maps to JPA's `@Version` annotation for optimistic
  locking. Multiple WebSocket handlers can update the same match concurrently
  (a bid coming in while another player accepts a trade), so optimistic locking
  prevents lost-update conflicts.
- **`game_players.seat_order`** is the persistent counterpart of
  `currentPlayerIndex` in the in-memory `GameState`. Storing it explicitly
  lets the round flow be reconstructed without depending on insertion order.
- **`auction_state` and `trade_state` are keyed by `game_id`**, so a match can
  hold at most one active auction and one active trade at a time. When the
  phase ends the row is deleted.

## Known limitations

Authentication is not yet part of the runtime. The `users` table is included in
the schema for completeness, but the server currently issues anonymous,
server-generated player IDs at lobby join time. Adding real auth is tracked
separately and is out of scope for the prototype.

## References

- [#152 — Design a Minimal Server-Side Persistence Model](https://github.com/AAUSoftwareEngineering2/SE2_Gruppenprojekt/issues/152)
- [#157 — Plan Server-Side Database Design and Persistence Diagram](https://github.com/AAUSoftwareEngineering2/SE2_Gruppenprojekt/issues/157)
- [#158 — Implement Server-Side Persistence](https://github.com/AAUSoftwareEngineering2/SE2_Gruppenprojekt/issues/158)
