package com.aquariux.technical.assessment.trade.service.impl;

import com.aquariux.technical.assessment.trade.dto.request.TradeRequest;
import com.aquariux.technical.assessment.trade.dto.response.TradeResponse;
import com.aquariux.technical.assessment.trade.entity.CryptoPrice;
import com.aquariux.technical.assessment.trade.entity.Trade;
import com.aquariux.technical.assessment.trade.entity.UserWallet;
import com.aquariux.technical.assessment.trade.enums.TradeType;
import com.aquariux.technical.assessment.trade.mapper.CryptoPairMapper;
import com.aquariux.technical.assessment.trade.mapper.CryptoPriceMapper;
import com.aquariux.technical.assessment.trade.mapper.TradeMapper;
import com.aquariux.technical.assessment.trade.mapper.UserWalletMapper;
import com.aquariux.technical.assessment.trade.service.TradeServiceInterface;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TradeServiceImpl implements TradeServiceInterface {

    private final TradeMapper tradeMapper;
    private final CryptoPairMapper cryptoPairMapper;
    private final UserWalletMapper userWalletMapper;
    private final CryptoPriceMapper cryptoPriceMapper;

    @Override
    @Transactional
    public TradeResponse executeTrade(TradeRequest tradeRequest) {
        validateRequest(tradeRequest);

        String pairName = resolvePairName(tradeRequest);
        Long cryptoPairId = resolveCryptoPairId(pairName);
        if (cryptoPairId == null) {
            throw new IllegalArgumentException("Unsupported trading pair: " + pairName);
        }

        CryptoPrice latestPrice = getLatestPriceForPair(pairName);
        BigDecimal executionPrice = tradeRequest.getTradeType() == TradeType.BUY
                ? latestPrice.getAskPrice()
                : latestPrice.getBidPrice();
        BigDecimal quantity = tradeRequest.getQuantity();
        BigDecimal totalAmount = executionPrice.multiply(quantity);

        String baseSymbol = pairName.substring(0, pairName.length() - 4);
        String quoteSymbol = pairName.substring(pairName.length() - 4);
        Long baseSymbolId = resolveSymbolId(baseSymbol);
        Long quoteSymbolId = resolveSymbolId(quoteSymbol);

        UserWallet baseWallet = getOrCreateWallet(tradeRequest.getUserId(), baseSymbolId, tradeRequest.getTradeType() == TradeType.BUY);
        UserWallet quoteWallet = getOrCreateWallet(tradeRequest.getUserId(), quoteSymbolId, true);

        if (tradeRequest.getTradeType() == TradeType.BUY) {
            if (quoteWallet == null) {
                quoteWallet = createUserWallet(tradeRequest.getUserId(), quoteSymbolId, BigDecimal.ZERO);
            }
            if (quoteWallet.getBalance() == null) {
                quoteWallet.setBalance(BigDecimal.ZERO);
            }
            if (quoteWallet.getBalance().compareTo(totalAmount) < 0) {
                throw new IllegalArgumentException("Insufficient quote balance for BUY order");
            }
            quoteWallet.setBalance(quoteWallet.getBalance().subtract(totalAmount));
            if (baseWallet == null) {
                baseWallet = createUserWallet(tradeRequest.getUserId(), baseSymbolId, BigDecimal.ZERO);
            }
            if (baseWallet.getBalance() == null) {
                baseWallet.setBalance(BigDecimal.ZERO);
            }
            baseWallet.setBalance(baseWallet.getBalance().add(quantity));
        } else {
            if (baseWallet == null || baseWallet.getBalance() == null) {
                throw new IllegalArgumentException("Insufficient base balance for SELL order");
            }
            if (baseWallet.getBalance().compareTo(quantity) < 0) {
                throw new IllegalArgumentException("Insufficient base balance for SELL order");
            }
            baseWallet.setBalance(baseWallet.getBalance().subtract(quantity));
            if (quoteWallet == null) {
                quoteWallet = createUserWallet(tradeRequest.getUserId(), quoteSymbolId, BigDecimal.ZERO);
            }
            if (quoteWallet.getBalance() == null) {
                quoteWallet.setBalance(BigDecimal.ZERO);
            }
            quoteWallet.setBalance(quoteWallet.getBalance().add(totalAmount));
        }

        persistWallet(baseWallet);
        persistWallet(quoteWallet);

        Trade trade = new Trade();
        trade.setUserId(tradeRequest.getUserId());
        trade.setCryptoPairId(cryptoPairId);
        trade.setTradeType(tradeRequest.getTradeType().name());
        trade.setQuantity(quantity);
        trade.setPrice(executionPrice);
        trade.setTotalAmount(totalAmount);
        trade.setTradeTime(LocalDateTime.now());
        tradeMapper.insertTrade(trade);

        TradeResponse response = new TradeResponse();
        response.setTradeId(trade.getId());
        response.setUserId(trade.getUserId());
        response.setPairName(pairName);
        response.setTradeType(tradeRequest.getTradeType());
        response.setQuantity(quantity);
        response.setPrice(executionPrice);
        response.setTotalAmount(totalAmount);
        response.setTradeTime(trade.getTradeTime());
        return response;
    }

    private void validateRequest(TradeRequest tradeRequest) {
        if (tradeRequest == null) {
            throw new IllegalArgumentException("Trade request is required");
        }
        if (tradeRequest.getUserId() == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (tradeRequest.getTradeType() == null) {
            throw new IllegalArgumentException("tradeType is required");
        }
        if (tradeRequest.getQuantity() == null || tradeRequest.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("quantity must be greater than zero");
        }
    }

    private String resolvePairName(TradeRequest tradeRequest) {
        if (tradeRequest.getPairName() != null && !tradeRequest.getPairName().isBlank()) {
            return tradeRequest.getPairName().trim().toUpperCase();
        }
        if (tradeRequest.getCryptoPairId() != null) {
            throw new IllegalArgumentException("cryptoPairId is not supported without a pairName lookup");
        }
        throw new IllegalArgumentException("pairName is required");
    }

    private Long resolveCryptoPairId(String pairName) {
        return cryptoPairMapper.findIdByPairName(pairName);
    }

    private CryptoPrice getLatestPriceForPair(String pairName) {
        List<CryptoPrice> latestPrices = cryptoPriceMapper.findLatestPrices();
        return latestPrices.stream()
                .filter(price -> price.getPairName() != null && price.getPairName().equalsIgnoreCase(pairName))
                .max(Comparator.comparing(CryptoPrice::getCreatedAt))
                .orElseThrow(() -> new IllegalStateException("No latest market price available for pair: " + pairName));
    }

    private Long resolveSymbolId(String symbol) {
        Long symbolId = userWalletMapper.findSymbolIdBySymbol(symbol);
        if (symbolId == null) {
            throw new IllegalArgumentException("Unsupported symbol: " + symbol);
        }
        return symbolId;
    }

    private UserWallet getOrCreateWallet(Long userId, Long symbolId, boolean allowCreate) {
        UserWallet wallet = userWalletMapper.findByUserIdAndSymbolId(userId, symbolId);
        if (wallet == null && allowCreate) {
            return createUserWallet(userId, symbolId, BigDecimal.ZERO);
        }
        return wallet;
    }

    private UserWallet createUserWallet(Long userId, Long symbolId, BigDecimal balance) {
        UserWallet wallet = new UserWallet();
        wallet.setUserId(userId);
        wallet.setSymbolId(symbolId);
        wallet.setBalance(balance == null ? BigDecimal.ZERO : balance);
        wallet.setUpdatedAt(LocalDateTime.now());
        userWalletMapper.insertUserWallet(wallet);
        return wallet;
    }

    private void persistWallet(UserWallet wallet) {
        if (wallet == null) {
            return;
        }
        wallet.setUpdatedAt(LocalDateTime.now());
        if (wallet.getId() == null) {
            userWalletMapper.insertUserWallet(wallet);
        } else {
            userWalletMapper.updateUserWallet(wallet);
        }
    }
}