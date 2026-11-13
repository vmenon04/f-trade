package com.fintechfive.ftrade.repository;

import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UuidTypeHandlerTest {

    private final UuidTypeHandler handler = new UuidTypeHandler();
    private final UUID id = UUID.fromString("cfe3fcd1-7520-45a5-9a50-d870d47e3b60");

    @Test
    void getResult_uuidColumn_returnsTheUuid() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("external_client_id")).thenReturn(id);

        assertEquals(id, handler.getResult(rs, "external_client_id"));
    }

    @Test
    void getResult_columnCastToText_parsesTheUuid() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject(1)).thenReturn(id.toString());

        assertEquals(id, handler.getResult(rs, 1));
    }

    @Test
    void getResult_nullColumn_returnsNull() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("external_trade_id")).thenReturn(null);

        assertNull(handler.getResult(rs, "external_trade_id"));
    }

    @Test
    void setParameter_passesTheUuidToTheDriver() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);

        handler.setParameter(ps, 1, id, JdbcType.OTHER);

        verify(ps).setObject(1, id);
    }
}
