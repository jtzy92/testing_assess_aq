# Implementation Summary

## Completed Work

This project now includes the core cryptocurrency trade execution flow for the assessment.

### Trade execution
- Implemented the trade request and response models
- Added the core buy/sell engine in the trade service
- Validated required inputs and trade quantities
- Resolved supported market pairs and latest available price data
- Applied the correct execution price logic:
  - BUY uses the latest ask price
  - SELL uses the latest bid price
- Recorded each trade in the trades table

### Wallet handling
- Added wallet lookup and creation logic for user balances
- Updated wallet balances after trade execution
- Ensured insufficient balance is rejected before persisting the trade
- Supported automatic wallet creation when a user does not yet hold the required asset

### Persistence updates
- Added MyBatis insert/update support for trade and wallet persistence
- Extended wallet mapper operations to support symbol-based wallet retrieval and updates

### Test coverage
- Added service-level tests for:
  - successful BUY execution
  - successful SELL execution
  - insufficient balance rejection

## Notes
- The implementation follows the existing project structure and current MyBatis/Spring Boot patterns.
- The trade API is designed to work with the current schema and seeded data for BTCUSDT and ETHUSDT.
- The requirement for a full Maven verification run depends on having the Java 21 toolchain and Maven wrapper or Maven CLI installed in the local environment.
