package fr.paris.lutece.utils.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Validates the ordering produced by {@link RunAfterOrdering} from the runAfter directives declared in the
 * headers of the SQL fixtures under src/test/resources/sql/plugins. The first four fixtures are those of
 * plugin-liquibase's LuteceRunAfterComparatorTest, so that both consumers are checked against the same
 * expectations :
 *
 * <ul>
 * <li>aaa declares runAfter:bbb (in its create script only), bbb declares runAfter:ccc, ccc declares nothing
 * : the expected order is ccc, bbb, aaa — the exact opposite of the alphabetical order</li>
 * <li>mmm declares nothing and keeps its natural position</li>
 * <li>qqq declares runAfter:doesnotexist : unknown target, natural position kept</li>
 * <li>xxx and yyy declare runAfter on each other : cycle, broken deterministically without failing</li>
 * <li>ddd declares two different targets in two scripts : conflict, natural position kept</li>
 * <li>eee declares runAfter:eee, fff declares runAfter:core : both ignored</li>
 * <li>ggg declares runAfter:hhh, a plugin declared but without any SQL script : ignored</li>
 * <li>lll declares the directive after its first SQL statement : not a header directive, ignored</li>
 * <li>rrr-sub, a module, declares runAfter:ccc : relocated like a plugin</li>
 * <li>ppp ships prerun_db_* scripts, never read for directives</li>
 * </ul>
 */
public class RunAfterOrderingTest
{
    static final String AAA_CREATE = "sql/plugins/aaa/plugin/create_db_aaa.sql";
    static final String AAA_UPDATE = "sql/plugins/aaa/upgrade/update_db_aaa-1.0.0-1.1.0.sql";
    static final String BBB_CREATE = "sql/plugins/bbb/plugin/create_db_bbb.sql";
    static final String CCC_CREATE = "sql/plugins/ccc/plugin/create_db_ccc.sql";
    static final String CCC_UPDATE = "sql/plugins/ccc/upgrade/update_db_ccc-1.0.0-1.1.0.sql";
    static final String DDD_CREATE = "sql/plugins/ddd/plugin/create_db_ddd.sql";
    static final String EEE_CREATE = "sql/plugins/eee/plugin/create_db_eee.sql";
    static final String FFF_CREATE = "sql/plugins/fff/plugin/create_db_fff.sql";
    static final String GGG_CREATE = "sql/plugins/ggg/plugin/create_db_ggg.sql";
    static final String LLL_CREATE = "sql/plugins/lll/plugin/create_db_lll.sql";
    static final String MMM_CREATE = "sql/plugins/mmm/plugin/create_db_mmm.sql";
    static final String PPP_CREATE = "sql/plugins/ppp/plugin/create_db_ppp.sql";
    static final String QQQ_CREATE = "sql/plugins/qqq/plugin/create_db_qqq.sql";
    static final String RRR_SUB_CREATE = "sql/plugins/rrr/modules/sub/plugin/create_db_rrr_sub.sql";
    static final String XXX_CREATE = "sql/plugins/xxx/plugin/create_db_xxx.sql";
    static final String YYY_CREATE = "sql/plugins/yyy/plugin/create_db_yyy.sql";

    /** the fixtures root, as laid out by the build under target/test-classes */
    public static final Path SQL_ROOT = Paths.get( "target", "test-classes", "sql" );

    /** plugins "declared" by the webapp : every fixture plugin, plus hhh which has no script */
    static final Set<String> DECLARED = new TreeSet<>(
            Arrays.asList( "aaa", "bbb", "ccc", "ddd", "eee", "fff", "ggg", "hhh", "lll", "mmm", "ppp", "ppp-mm", "qqq", "rrr-sub", "xxx", "yyy" ) );

    private static RunAfterOrdering ordering;
    private static RecordingListener listener;

    /** Collects the messages for assertions. */
    static final class RecordingListener implements RunAfterOrdering.Listener
    {
        final List<String> infos = new ArrayList<>( );
        final List<String> errors = new ArrayList<>( );

        @Override
        public void info( String message )
        {
            infos.add( message );
        }

        @Override
        public void error( String message )
        {
            errors.add( message );
        }

        boolean hasError( String fragment )
        {
            return errors.stream( ).anyMatch( m -> m.contains( fragment ) );
        }
    }

    /** Scans the fixture tree into scripts of the classpath form. */
    static List<RunAfterOrdering.Script> fixtureScripts( ) throws IOException
    {
        try ( Stream<Path> walk = Files.walk( SQL_ROOT ) )
        {
            return walk.filter( Files::isRegularFile ).sorted( ).map( file -> new RunAfterOrdering.Script( )
            {
                @Override
                public String getPath( )
                {
                    return "sql/" + SQL_ROOT.relativize( file ).toString( ).replace( '\\', '/' );
                }

                @Override
                public InputStream open( ) throws IOException
                {
                    return Files.newInputStream( file );
                }
            } ).collect( Collectors.toList( ) );
        }
    }

    @BeforeAll
    public static void buildOrdering( ) throws IOException
    {
        listener = new RecordingListener( );
        ordering = RunAfterOrdering.build( fixtureScripts( ), DECLARED::contains, listener );
    }

    /** Every plugin/ and upgrade/ fixture path, sorted by key ; deliberately fed out of order. */
    private static List<String> sorted( )
    {
        List<String> paths = new ArrayList<>( Arrays.asList( AAA_UPDATE, MMM_CREATE, CCC_UPDATE, YYY_CREATE, BBB_CREATE, RRR_SUB_CREATE, GGG_CREATE, AAA_CREATE,
                QQQ_CREATE, LLL_CREATE, XXX_CREATE, PPP_CREATE, EEE_CREATE, CCC_CREATE, DDD_CREATE, FFF_CREATE ) );
        paths.sort( Comparator.comparing( ordering::keyOf ) );
        return paths;
    }

    public static void assertBefore( List<String> sorted, String first, String second )
    {
        int firstIndex = sorted.indexOf( first );
        int secondIndex = sorted.indexOf( second );
        assertTrue( firstIndex >= 0, first + " missing from " + sorted );
        assertTrue( secondIndex >= 0, second + " missing from " + sorted );
        assertTrue( firstIndex < secondIndex, first + " should sort before " + second + " but order was " + sorted );
    }

    @Test
    public void chainedRunAfterReversesAlphabeticalOrder( )
    {
        List<String> sorted = sorted( );
        // chain aaa -> bbb -> ccc : execution order must be ccc, then bbb, then aaa
        assertBefore( sorted, CCC_CREATE, BBB_CREATE );
        assertBefore( sorted, CCC_UPDATE, BBB_CREATE );
        assertBefore( sorted, BBB_CREATE, AAA_CREATE );
        // ccc's own scripts keep their internal alphabetical order
        assertBefore( sorted, CCC_CREATE, CCC_UPDATE );
    }

    @Test
    public void directiveIsPluginScoped( )
    {
        List<String> sorted = sorted( );
        // aaa's update script declares no directive itself, but the whole plugin is relocated
        assertBefore( sorted, BBB_CREATE, AAA_UPDATE );
        assertBefore( sorted, AAA_CREATE, AAA_UPDATE );
    }

    @Test
    public void unrelatedPluginsKeepTheirNaturalPositions( )
    {
        List<String> sorted = sorted( );
        // mmm and qqq (unknown target, directive ignored) stay in plain alphabetical order,
        // after the whole ccc chain ('~' sorts after any alphanumeric) and before xxx/yyy
        assertBefore( sorted, AAA_UPDATE, MMM_CREATE );
        assertBefore( sorted, MMM_CREATE, QQQ_CREATE );
        assertBefore( sorted, QQQ_CREATE, XXX_CREATE );
        assertTrue( listener.hasError( "runAfter:doesnotexist declared by plugin qqq ignored : no such plugin is declared" ), listener.errors.toString( ) );
    }

    @Test
    public void cycleIsBrokenDeterministicallyWithoutFailing( )
    {
        List<String> sorted = sorted( );
        // xxx <-> yyy : plugins are resolved in name order, so the cycle is broken at xxx
        // (its directive is discarded, it keeps its natural position) and yyy lands after it
        assertBefore( sorted, XXX_CREATE, YYY_CREATE );
        assertEquals( 16, sorted.size( ) );
        assertTrue( listener.hasError( "Cycle detected in runAfter directives at plugin xxx" ), listener.errors.toString( ) );
        assertFalse( ordering.getTargets( ).containsKey( "xxx" ) );
        assertEquals( "xxx", ordering.getTargets( ).get( "yyy" ) );
    }

    @Test
    public void conflictingTargetsInsideOnePluginAreIgnored( )
    {
        List<String> sorted = sorted( );
        // ddd would land after ccc or after mmm : it stays where it is, before eee
        assertBefore( sorted, DDD_CREATE, EEE_CREATE );
        assertBefore( sorted, AAA_UPDATE, DDD_CREATE );
        assertTrue( listener.hasError( "Plugin ddd declares conflicting runAfter targets (ccc and mmm)" ), listener.errors.toString( ) );
        assertFalse( ordering.getTargets( ).containsKey( "ddd" ) );
    }

    @Test
    public void selfReferenceAndCoreAreIgnored( )
    {
        assertTrue( listener.hasError( "runAfter:eee declared by plugin eee ignored : a plugin cannot run after itself" ), listener.errors.toString( ) );
        assertTrue( listener.hasError( "runAfter:core declared by plugin fff ignored : core cannot take part in runAfter ordering" ), listener.errors.toString( ) );
        assertEquals( EEE_CREATE, ordering.keyOf( EEE_CREATE ) );
        assertEquals( FFF_CREATE, ordering.keyOf( FFF_CREATE ) );
    }

    @Test
    public void declaredTargetWithoutScriptIsIgnored( )
    {
        assertTrue( listener.hasError( "runAfter:hhh declared by plugin ggg ignored : the target plugin has no SQL script" ), listener.errors.toString( ) );
        assertEquals( GGG_CREATE, ordering.keyOf( GGG_CREATE ) );
    }

    @Test
    public void directiveAfterSqlIsNotAHeaderDirective( )
    {
        assertEquals( LLL_CREATE, ordering.keyOf( LLL_CREATE ) );
        assertFalse( ordering.getTargets( ).containsKey( "lll" ) );
    }

    @Test
    public void moduleIsRelocatedLikeAPlugin( )
    {
        List<String> sorted = sorted( );
        assertEquals( "ccc", ordering.getTargets( ).get( "rrr-sub" ) );
        assertBefore( sorted, CCC_UPDATE, RRR_SUB_CREATE );
        // bbb (resolved first, name order) and rrr-sub both hang under ccc : their relative order is by name
        assertBefore( sorted, AAA_UPDATE, RRR_SUB_CREATE );
        assertBefore( sorted, RRR_SUB_CREATE, MMM_CREATE );
    }

    @Test
    public void prerunScriptsAreNeverReadForDirectives( )
    {
        assertTrue( RunAfterOrdering.isPrerunScript( "sql/plugins/ppp/plugin/prerun_db_ppp.sql" ) );
        assertFalse( RunAfterOrdering.isPrerunScript( PPP_CREATE ) );
        assertFalse( ordering.getTargets( ).containsKey( "ppp" ) );
        assertFalse( ordering.getTargets( ).containsKey( "ppp-mm" ) );
    }

    @Test
    public void keysAreDistinctForDistinctPathsAndStableForTheSamePath( )
    {
        assertNotEquals( ordering.keyOf( AAA_CREATE ), ordering.keyOf( AAA_UPDATE ) );
        assertEquals( ordering.keyOf( AAA_CREATE ), ordering.keyOf( AAA_CREATE ) );
        // a relocated key embeds the target's directory and the whole original path
        String key = ordering.keyOf( AAA_CREATE );
        assertTrue( key.startsWith( "sql/plugins/ccc/~runAfter/bbb/~runAfter/aaa/~/" ), key );
        assertTrue( key.endsWith( AAA_CREATE ), key );
    }

    @Test
    public void honouredDirectivesAreReportedAsInfo( )
    {
        assertTrue( listener.infos.contains( "Scripts of plugin aaa will run after those of plugin bbb" ), listener.infos.toString( ) );
        assertTrue( listener.infos.contains( "Scripts of plugin bbb will run after those of plugin ccc" ), listener.infos.toString( ) );
        assertTrue( listener.infos.contains( "Scripts of plugin yyy will run after those of plugin xxx" ), listener.infos.toString( ) );
        assertEquals( 4, listener.infos.size( ), listener.infos.toString( ) );
    }
}
