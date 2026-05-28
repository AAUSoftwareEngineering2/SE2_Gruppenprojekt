# Auction Bluffing Mechanism (COMPLETED)

Implemented the ability for players to place bids exceeding their total money ("bluffing"), with a penalty system if they win the auction and cannot pay.

## Changes Made
- **Backend**: Removed strict money check during bidding. Added bluff detection in `resolveAuction`.
- **Shared**: Updated models to support player exclusion and event notification.
- **Frontend**: Enabled bluff bidding in UI and handled detection events.
- **Refactoring**: Cleaned up `GameScreen.kt` by extracting phase-specific logic into `GameScreenPhases.kt`.

## User Review Required

- **Revealing the Bluff**: Should the bluff be revealed immediately when the auction closes, or only when the auctioneer chooses to "sell" to the winner? *Initial plan: Reveal during `resolveAuction` (when auctioneer makes a choice).*
- **Restarting Auction**: When a bluff is detected, the bid is reset and the bluffer is excluded. The timer will restart.

## Proposed Changes

### Shared Module

#### [AuctionState.kt](file:///Users/oleneckritz/StudioProjects/SE2_Gruppenprojekt/shared/src/main/kotlin/at/aau/kuhhandel/shared/model/AuctionState.kt)
- Add `excludedPlayerIds: Set<String>` to track players who bluffed and are barred from the current auction.

#### [GameErrorReason.kt](file:///Users/oleneckritz/StudioProjects/SE2_Gruppenprojekt/shared/src/main/kotlin/at/aau/kuhhandel/shared/enums/GameErrorReason.kt)
- Add `PLAYER_EXCLUDED_FROM_AUCTION`.

#### [GameEvent.kt](file:///Users/oleneckritz/StudioProjects/SE2_Gruppenprojekt/shared/src/main/kotlin/at/aau/kuhhandel/shared/model/GameEvent.kt)
- Add `BluffDetected(playerId: String, playerName: String)`.

---

### Server Module

#### [GameSession.kt](file:///Users/oleneckritz/StudioProjects/SE2_Gruppenprojekt/server/src/main/kotlin/at/aau/kuhhandel/server/model/GameSession.kt)
- **`placeBid`**: Remove `ensureHasEnoughMoney`. Add check for `excludedPlayerIds`.
- **`resolveAuction`**: Add validation logic. If `highestBidder` cannot pay:
    - Add to `excludedPlayerIds`.
    - Reset auction state (bid = 0, bidder = null).
    - Stay in `AUCTION_BIDDING` phase.
    - Emit `BluffDetected` event.

---

### App Module

#### [AuctionControls.kt](file:///Users/oleneckritz/StudioProjects/SE2_Gruppenprojekt/app/src/main/kotlin/at/aau/kuhhandel/app/ui/components/AuctionControls.kt)
- Allow clicking bid buttons even if `nextBid > myTotalMoney`.
- Add visual feedback for "Bluff" bids.

#### [GameScreen.kt](file:///Users/oleneckritz/StudioProjects/SE2_Gruppenprojekt/app/src/main/kotlin/at/aau/kuhhandel/app/ui/game/GameScreen.kt)
- Show snackbar or animation when `BluffDetected` event is received.

## Verification Plan

### Automated Tests
- `GameSessionTest.kt`: Add test for placing a bid higher than total money.
- `GameSessionTest.kt`: Add test for resolving auction where winner bluffed (check exclusion and reset).

### Manual Verification
1. Start a game with 3 players.
2. Have Player 2 bid 100€ even if they only have 60€.
3. Close auction.
4. Auctioneer chooses "Let Winner Buy".
5. Verify Player 2 is excluded, bid is reset, and auction continues.
