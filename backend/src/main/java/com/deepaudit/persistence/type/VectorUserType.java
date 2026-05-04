package com.deepaudit.persistence.type;

import com.pgvector.PGvector;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;

/**
 * Hibernate UserType bridging PostgreSQL pgvector {@code VECTOR(N)} columns
 * and Java {@code float[]}.
 *
 * <p>Read path: JDBC driver returns the unknown {@code vector} column as a
 * {@code PGobject} whose {@code toString()} yields the wire format
 * {@code "[1,2,3]"}; we re-parse it via {@link PGvector#PGvector(String)}.
 *
 * <p>Write path: we wrap the {@code float[]} in a {@link PGvector} and
 * call {@code setObject}; PGvector extends PGobject so the JDBC driver
 * sends its string value, and Postgres casts to the column's vector type.
 *
 * <p>Per-connection {@code PGvector.addVectorType(...)} registration is NOT
 * required for this approach -- both directions go through PGobject's
 * generic string protocol.
 */
public class VectorUserType implements UserType<float[]> {

    @Override
    public int getSqlType() {
        return Types.OTHER;
    }

    @Override
    public Class<float[]> returnedClass() {
        return float[].class;
    }

    @Override
    public boolean equals(float[] x, float[] y) {
        return Arrays.equals(x, y);
    }

    @Override
    public int hashCode(float[] x) {
        return Arrays.hashCode(x);
    }

    @Override
    public float[] nullSafeGet(ResultSet rs, int position,
                               SharedSessionContractImplementor session,
                               Object owner) throws SQLException {
        Object raw = rs.getObject(position);
        if (raw == null) {
            return null;
        }
        return new PGvector(raw.toString()).toArray();
    }

    @Override
    public void nullSafeSet(PreparedStatement st, float[] value, int index,
                            SharedSessionContractImplementor session) throws SQLException {
        if (value == null) {
            st.setNull(index, Types.OTHER);
        } else {
            st.setObject(index, new PGvector(value));
        }
    }

    @Override
    public float[] deepCopy(float[] value) {
        return value == null ? null : Arrays.copyOf(value, value.length);
    }

    @Override
    public boolean isMutable() {
        return true;
    }

    @Override
    public Serializable disassemble(float[] value) {
        return deepCopy(value);
    }

    @Override
    public float[] assemble(Serializable cached, Object owner) {
        return deepCopy((float[]) cached);
    }

    @Override
    public float[] replace(float[] detached, float[] managed, Object owner) {
        return deepCopy(detached);
    }
}
