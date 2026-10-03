package com.aquariux.technical.assessment.trade.service.impl;

import com.aquariux.technical.assessment.trade.dto.request.TradeRequest;
import com.aquariux.technical.assessment.trade.dto.response.TradeResponse;
import com.aquariux.technical.assessment.trade.entity.CryptoPrice;
import com.aquariux.technical.assessment.trade.entity.UserWallet;
import com.aquariux.technical.assessment.trade.enums.TradeType;
import com.aquariux.technical.assessment.trade.mapper.CryptoPairMapper;
import com.aquariux.technical.assessment.trade.mapper.CryptoPriceMapper;
import com.aquariux.technical.assessment.trade.mapper.TradeMapper;
import com.aquariux.technical.assessment.trade.mapper.UserWalletMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradeServiceImplTest {

    @Mock
    private TradeMapper tradeMapper;

    @Mock
    private CryptoPairMapper cryptoPairMapper;

    @Mock
    private UserWalletMapper userWalletMapper;

    @Mock
    private CryptoPriceMapper cryptoPriceMapper;

    @InjectMocks
    private TradeServiceImpl tradeService;

    private UserWallet usdtWallet;
    private UserWallet btcWallet;
    private CryptoPrice btcPrice;

    @BeforeEach
    void setUp() {
        usdtWallet = new UserWallet();
        usdtWallet.setId(10L);
        usdtWallet.setUserId(1L);
        usdtWallet.setSymbolId(3L);
        usdtWallet.setBalance(new BigDecimal("10000.00"));
        usdtWallet.setUpdatedAt(LocalDateTime.now());

        btcWallet = new UserWallet();
        btcWallet.setId(11L);
        btcWallet.setUserId(1L);
        btcWallet.setSymbolId(1L);
        btcWallet.setBalance(new BigDecimal("0.50"));
        btcWallet.setUpdatedAt(LocalDateTime.now());

        btcPrice = new CryptoPrice();
        btcPrice.setId(1L);
        btcPrice.setPairName("BTCUSDT");
        btcPrice.setAskPrice(new BigDecimal("50000.00"));
        btcPrice.setBidPrice(new BigDecimal("49500.00"));
        btcPrice.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void executeTrade_ShouldBuyCrypto_UsingLatestAskPriceAndUpdateWallets() {
        // Given
        when(cryptoPairMapper.findIdByPairName("BTCUSDT")).thenReturn(1L);
        when(cryptoPriceMapper.findLatestPrices()).thenReturn(List.of(btcPrice));
        when(userWalletMapper.findSymbolIdBySymbol("BTC")).thenReturn(1L);
        when(userWalletMapper.findSymbolIdBySymbol("USDT")).thenReturn(3L);
        when(userWalletMapper.findByUserIdAndSymbolId(1L, 1L)).thenReturn(null);
        when(userWalletMapper.findByUserIdAndSymbolId(1L, 3L)).thenReturn(usdtWallet);

        TradeRequest request = new TradeRequest();
        request.setUserId(1L);
        request.setTradeType(TradeType.BUY);
        request.setPairName("BTCUSDT");
        request.setQuantity(new BigDecimal("0.1"));

        // When
        TradeResponse result = tradeService.executeTrade(request);

        // Then
        assertThat(result.getPairName()).isEqualTo("BTCUSDT");
        assertThat(result.getTradeType()).isEqualTo(TradeType.BUY);
        assertThat(result.getQuantity()).isEqualTo(new BigDecimal("0.1"));
        assertThat(result.getPrice()).isEqualTo(new BigDecimal("50000.00"));
        assertThat(result.getTotalAmount()).isEqualTo(new BigDecimal("5000.00"));
        verify(tradeMapper).insertTrade(any());
    }

    @Test
    void executeTrade_ShouldSellCrypto_UsingLatestBidPriceAndUpdateWallets() {
        // Given
        when(cryptoPairMapper.findIdByPairName("BTCUSDT")).thenReturn(1L);
        when(cryptoPriceMapper.findLatestPrices()).thenReturn(List.of(btcPrice));
        when(userWalletMapper.findSymbolIdBySymbol("BTC")).thenReturn(1L);
        when(userWalletMapper.findSymbolIdBySymbol("USDT")).thenReturn(3L);
        when(userWalletMapper.findByUserIdAndSymbolId(1L, 1L)).thenReturn(btcWallet);
        when(userWalletMapper.findByUserIdAndSymbolId(1L, 3L)).thenReturn(usdtWallet);

        TradeRequest request = new TradeRequest();
        request.setUserId(1L);
        request.setTradeType(TradeType.SELL);
        request.setPairName("BTCUSDT");
        request.setQuantity(new BigDecimal("0.1"));

        // When
        TradeResponse result = tradeService.executeTrade(request);

        // Then
        assertThat(result.getTradeType()).isEqualTo(TradeType.SELL);
        assertThat(result.getPrice()).isEqualTo(new BigDecimal("49500.00"));
        assertThat(result.getTotalAmount()).isEqualTo(new BigDecimal("4950.00"));
        verify(tradeMapper).insertTrade(any());
    }

    @Test
    void executeTrade_ShouldRejectBuy_WhenUserHasInsufficientQuoteBalance() {
        // Given
        when(cryptoPairMapper.findIdByPairName("BTCUSDT")).thenReturn(1L);
        when(cryptoPriceMapper.findLatestPrices()).thenReturn(List.of(btcPrice));
        when(userWalletMapper.findSymbolIdBySymbol("BTC")).thenReturn(1L);
        when(userWalletMapper.findSymbolIdBySymbol("USDT")).thenReturn(3L);
        when(userWalletMapper.findByUserIdAndSymbolId(1L, 1L)).thenReturn(null);
        when(userWalletMapper.findByUserIdAndSymbolId(1L, 3L)).thenReturn(new UserWallet());

        TradeRequest request = new TradeRequest();
        request.setUserId(1L);
        request.setTradeType(TradeType.BUY);
        request.setPairName("BTCUSDT");
        request.setQuantity(new BigDecimal("0.2"));

        // Then
        assertThatThrownBy(() -> tradeService.executeTrade(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient quote balance");
    }
}
