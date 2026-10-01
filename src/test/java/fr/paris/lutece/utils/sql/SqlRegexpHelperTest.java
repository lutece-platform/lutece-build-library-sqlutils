package fr.paris.lutece.utils.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * Tests the SQL regexps applied by SqlRegexpHelper.
 */
public class SqlRegexpHelperTest
{
    private static final String PROPERTIES = "regexp.postgresql.list=1,2\n"
            + "regexp.postgresql.1=[^ ]+ auto_increment\nreplace.postgresql.1=serial\n"
            + "regexp.postgresql.2=LONG VARCHAR\nreplace.postgresql.2=TEXT\n";

    /**
     * The regexps match whatever the case of the SQL, like the Ant replaceregexp flags="gi" of build-config did.
     *
     * @throws Exception
     *             if the helper cannot be built
     */
    @Test
    public void testFilterIgnoresCase( ) throws Exception
    {
        SqlRegexpHelper helper = new SqlRegexpHelper( ( ) -> new ByteArrayInputStream( PROPERTIES.getBytes( StandardCharsets.UTF_8 ) ), "postgresql" );
        assertEquals( "id serial NOT NULL,", helper.filter( "id int AUTO_INCREMENT NOT NULL," ) );
        assertEquals( "id serial,", helper.filter( "id int auto_increment," ) );
        assertEquals( "value TEXT NULL,", helper.filter( "value long varchar NULL," ) );
    }
}
