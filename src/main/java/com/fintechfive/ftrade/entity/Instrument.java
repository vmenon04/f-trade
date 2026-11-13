package com.fintechfive.ftrade.entity;

import com.fintechfive.ftrade.enums.InstrumentType;

public class Instrument {

    private Long instrumentId;
    private String name;
    private String ticker;
    private InstrumentType instrumentType;

    public Instrument() {
    }

    public Instrument(String name, String ticker, InstrumentType instrumentType) {
        instrumentId = null;
        this.name = name;
        this.ticker = ticker;
        this.instrumentType = instrumentType;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    public void  setInstrumentId(Long instrumentId) {
        this.instrumentId = instrumentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public InstrumentType getInstrumentType() {
        return instrumentType;
    }

    public void setInstrumentType(InstrumentType instrumentType) {
        this.instrumentType = instrumentType;
    }
}
