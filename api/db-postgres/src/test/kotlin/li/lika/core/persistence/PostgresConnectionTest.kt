package li.lika.core.persistence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PostgresConnectionTest {

    @Test
    fun `reads the connection URLs of Dokploy and Heroku-like hosts`() {
        assertEquals(PostgresConnection("jdbc:postgresql://lika-db-abc:5432/lika", "lika", "p@ss:word"),
            PostgresConnection.parse("postgresql://lika:p%40ss%3Aword@lika-db-abc:5432/lika"))
        assertEquals(PostgresConnection("jdbc:postgresql://db.example.com:5432/lika?sslmode=require", "u", "p"),
            PostgresConnection.parse("postgres://u:p@db.example.com/lika?sslmode=require"))
    }

    @Test
    fun `keeps JDBC URLs as they are`() {
        assertEquals(PostgresConnection("jdbc:postgresql://localhost:5432/lika", null, null), PostgresConnection.parse("jdbc:postgresql://localhost:5432/lika"))
    }

    @Test
    fun `refuses other databases`() {
        assertThrows<IllegalArgumentException> { PostgresConnection.parse("mysql://u:p@host/lika") }
    }
}
