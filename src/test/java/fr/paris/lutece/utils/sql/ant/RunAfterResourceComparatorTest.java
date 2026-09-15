package fr.paris.lutece.utils.sql.ant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Resource;
import org.apache.tools.ant.types.resources.Sort;
import org.junit.jupiter.api.Test;

import fr.paris.lutece.utils.sql.RunAfterOrderingTest;

/**
 * Runs {@link RunAfterResourceComparator} inside a real Ant {@link Sort} over a {@link FileSet}, the way
 * build.xml uses it, on the same fixtures as {@link RunAfterOrderingTest}.
 */
public class RunAfterResourceComparatorTest
{
    private static final File SQL_DIR = RunAfterOrderingTest.SQL_ROOT.toFile( ).getAbsoluteFile( );

    private static Project project( )
    {
        Project project = new Project( );
        project.init( );
        project.setBaseDir( SQL_DIR );
        return project;
    }

    /** the fileset of the Ant target "all", sorted, as relative names with / separators */
    private static List<String> sortedPluginScripts( File comparatorDir )
    {
        Project project = project( );
        FileSet fileSet = new FileSet( );
        fileSet.setProject( project );
        fileSet.setDir( SQL_DIR );
        fileSet.createInclude( ).setName( "plugins/**/plugin/*.sql" );
        fileSet.createExclude( ).setName( "plugins/**/plugin/prerun_db_*.sql" );

        RunAfterResourceComparator comparator = new RunAfterResourceComparator( );
        comparator.setProject( project );
        if ( comparatorDir != null )
        {
            comparator.setDir( comparatorDir );
        }

        Sort sort = new Sort( );
        sort.setProject( project );
        sort.add( fileSet );
        sort.add( comparator );

        List<String> names = new ArrayList<>( );
        for ( Resource resource : sort )
        {
            names.add( resource.getName( ).replace( '\\', '/' ) );
        }
        return names;
    }

    private static void assertBefore( List<String> sorted, String first, String second )
    {
        RunAfterOrderingTest.assertBefore( sorted, first, second );
    }

    @Test
    public void sortRelocatesDeclaringPluginsAfterTheirTargets( )
    {
        List<String> sorted = sortedPluginScripts( null );
        // the target "all" of build.xml : plugin/ scripts only, prerun excluded
        assertEquals( 15, sorted.size( ), sorted.toString( ) );
        assertBefore( sorted, "plugins/ccc/plugin/create_db_ccc.sql", "plugins/bbb/plugin/create_db_bbb.sql" );
        assertBefore( sorted, "plugins/bbb/plugin/create_db_bbb.sql", "plugins/aaa/plugin/create_db_aaa.sql" );
        assertBefore( sorted, "plugins/aaa/plugin/create_db_aaa.sql", "plugins/ddd/plugin/create_db_ddd.sql" );
        assertBefore( sorted, "plugins/ccc/plugin/create_db_ccc.sql", "plugins/rrr/modules/sub/plugin/create_db_rrr_sub.sql" );
        assertBefore( sorted, "plugins/rrr/modules/sub/plugin/create_db_rrr_sub.sql", "plugins/ddd/plugin/create_db_ddd.sql" );
        assertBefore( sorted, "plugins/xxx/plugin/create_db_xxx.sql", "plugins/yyy/plugin/create_db_yyy.sql" );
        // untouched plugins stay alphabetical
        assertBefore( sorted, "plugins/ddd/plugin/create_db_ddd.sql", "plugins/eee/plugin/create_db_eee.sql" );
        assertBefore( sorted, "plugins/mmm/plugin/create_db_mmm.sql", "plugins/ppp/plugin/create_db_ppp.sql" );
        assertBefore( sorted, "plugins/ppp/plugin/create_db_ppp.sql", "plugins/qqq/plugin/create_db_qqq.sql" );
        assertTrue( sorted.stream( ).noneMatch( n -> n.contains( "prerun_db_" ) ), sorted.toString( ) );
    }

    @Test
    public void explicitDirAttributeGivesTheSameOrder( )
    {
        assertEquals( sortedPluginScripts( null ), sortedPluginScripts( SQL_DIR ) );
    }

    @Test
    public void windowsSeparatorsAreNormalizedToTheClasspathForm( )
    {
        assertEquals( "sql/plugins/forms/plugin/init_db_forms.sql", RunAfterResourceComparator.toClasspathName( "plugins\\forms\\plugin\\init_db_forms.sql" ) );
        assertEquals( "sql/plugins/forms/plugin/init_db_forms.sql", RunAfterResourceComparator.toClasspathName( "/plugins/forms/plugin/init_db_forms.sql" ) );
        assertEquals( "sql/plugins/forms/plugin/init_db_forms.sql", RunAfterResourceComparator.toClasspathName( "sql/plugins/forms/plugin/init_db_forms.sql" ) );
        assertEquals( "sql/create_db_lutece_core.sql", RunAfterResourceComparator.toClasspathName( "create_db_lutece_core.sql" ) );
    }
}
