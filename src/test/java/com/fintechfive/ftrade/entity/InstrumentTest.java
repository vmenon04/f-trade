package com.fintechfive.ftrade.entity;

import com.fintechfive.ftrade.enums.InstrumentType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InstrumentTest {

    @Test
    void noArgsConstructorLeavesFieldsAtDefaults() {
        Instrument instrument = new Instrument();

        assertAll(
                () -> assertNull(instrument.getInstrumentId()),
                () -> assertNull(instrument.getName()),
                () -> assertNull(instrument.getTicker()),
                () -> assertNull(instrument.getInstrumentType())
        );
    }

    @Test
    void parameterizedConstructorSetsProvidedValues() {
        Instrument equity = new Instrument("Microsoft", "MSFT", InstrumentType.STOCK);

        assertAll(
                () -> assertNull(equity.getInstrumentId()),
                () -> assertEquals("Microsoft", equity.getName()),
                () -> assertEquals("MSFT", equity.getTicker()),
                () -> assertEquals(InstrumentType.STOCK, equity.getInstrumentType())
        );
    }

    @Test
    void settersUpdateAllMutableFields() {
        Instrument instrument = new Instrument();

        instrument.setInstrumentId(9L);
        instrument.setName("Bitcoin");
        instrument.setTicker("BTC");
        instrument.setInstrumentType(InstrumentType.CRYPTO);

        assertAll(
                () -> assertEquals(9L, instrument.getInstrumentId()),
                () -> assertEquals("Bitcoin", instrument.getName()),
                () -> assertEquals("BTC", instrument.getTicker()),
                () -> assertEquals(InstrumentType.CRYPTO, instrument.getInstrumentType())
        );
    }
}