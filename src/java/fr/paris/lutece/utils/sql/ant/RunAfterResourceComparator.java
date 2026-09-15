package fr.paris.lutece.utils.sql.ant;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.Resource;
import org.apache.tools.ant.types.resources.FileProvider;
import org.apache.tools.ant.types.resources.comparators.ResourceComparator;

import fr.paris.lutece.utils.sql.RunAfterOrdering;

/**
 * Ant resource comparator ordering lutece SQL scripts with the <code>runAfter</code> directive, for use in
 * a <code>&lt;sort&gt;</code> resource collection of the database initialization build file :
 *
 * <pre>
 * &lt;typedef name="runafter" classname="fr.paris.lutece.utils.sql.ant.RunAfterResourceComparator"
 *          onerror="report" classpathref="runafter.classpath"/&gt;
 * ...
 * &lt;sort&gt;
 *     &lt;fileset dir="${basedir}" includes="plugins/**&#47;plugin/*.sql"/&gt;
 *     &lt;runafter dir="${basedir}"/&gt;
 * &lt;/sort&gt;
 * </pre>
 *
 * Applies exactly the rules of {@link RunAfterOrdering}, hence the same order as the liquibase startup of
 * plugin-liquibase. A comparator only sees pairs of resources, while the directives of every plugin must be
 * known to place any of them : on first use, the whole <code>dir</code> tree (the <code>WEB-INF/sql</code>
 * directory, by default the project basedir) is scanned once for <code>*.sql</code> files. Resource names
 * are taken relative to <code>dir</code>, normalized to <code>/</code> separators and prefixed with
 * <code>sql/</code> to match the classpath form the shared rules expect.
 *
 * Every plugin owning a SQL script is an acceptable target : without the webapp's plugin descriptors, "the
 * target has scripts" is the only existence test available to Ant.
 */
public class RunAfterResourceComparator extends ResourceComparator
{
    private static final String SQL_EXTENSION = ".sql";
    private static final String CLASSPATH_PREFIX = "sql/";

    private File _dir;
    private RunAfterOrdering _ordering;

    /**
     * The <code>WEB-INF/sql</code> directory the resources live in. Defaults to the project basedir.
     *
     * @param dir
     *            the directory
     */
    public void setDir( File dir )
    {
        _dir = dir;
    }

    @Override
    protected int resourceCompare( Resource left, Resource right )
    {
        RunAfterOrdering ordering = ordering( );
        return ordering.keyOf( classpathName( left ) ).compareTo( ordering.keyOf( classpathName( right ) ) );
    }

    /**
     * The classpath form of a resource name : relative to <code>dir</code> when the resource is a file
     * under it, the resource name otherwise ; separators normalized, <code>sql/</code> prefixed.
     */
    String classpathName( Resource resource )
    {
        String name = null;
        FileProvider provider = resource.as( FileProvider.class );
        if ( provider != null )
        {
            Path file = provider.getFile( ).toPath( ).toAbsolutePath( ).normalize( );
            Path base = baseDir( ).toPath( ).toAbsolutePath( ).normalize( );
            if ( file.startsWith( base ) )
            {
                name = base.relativize( file ).toString( );
            }
        }
        if ( name == null )
        {
            name = resource.getName( );
        }
        return toClasspathName( name );
    }

    /**
     * @param relativeName
     *            a name relative to <code>WEB-INF/sql</code>, with any separator
     * @return the <code>sql/...</code> form with <code>/</code> separators
     */
    static String toClasspathName( String relativeName )
    {
        String normalized = relativeName.replace( '\\', '/' );
        while ( normalized.startsWith( "/" ) )
        {
            normalized = normalized.substring( 1 );
        }
        return normalized.startsWith( CLASSPATH_PREFIX ) ? normalized : CLASSPATH_PREFIX + normalized;
    }

    private File baseDir( )
    {
        if ( _dir != null )
        {
            return _dir;
        }
        Project project = getProject( );
        File baseDir = project == null ? null : project.getBaseDir( );
        return baseDir == null ? new File( "." ) : baseDir;
    }

    private RunAfterOrdering ordering( )
    {
        if ( _ordering == null )
        {
            _ordering = RunAfterOrdering.build( scanScripts( ), name -> true, new AntListener( ) );
        }
        return _ordering;
    }

    private List<RunAfterOrdering.Script> scanScripts( )
    {
        Path base = baseDir( ).toPath( ).toAbsolutePath( ).normalize( );
        List<RunAfterOrdering.Script> scripts = new ArrayList<>( );
        if ( !Files.isDirectory( base ) )
        {
            log( "runAfter ordering : directory " + base + " does not exist, alphabetical order kept", Project.MSG_WARN );
            return scripts;
        }
        try ( Stream<Path> walk = Files.walk( base ) )
        {
            walk.filter( Files::isRegularFile ).filter( p -> p.getFileName( ).toString( ).endsWith( SQL_EXTENSION ) ).sorted( )
                    .forEach( p -> scripts.add( new FileScript( toClasspathName( base.relativize( p ).toString( ) ), p ) ) );
        }
        catch( IOException e )
        {
            throw new BuildException( "Could not scan " + base + " for SQL scripts", e );
        }
        return scripts;
    }

    /** A script backed by a file on disk. */
    private static final class FileScript implements RunAfterOrdering.Script
    {
        private final String _path;
        private final Path _file;

        FileScript( String path, Path file )
        {
            _path = path;
            _file = file;
        }

        @Override
        public String getPath( )
        {
            return _path;
        }

        @Override
        public InputStream open( ) throws IOException
        {
            return Files.newInputStream( _file );
        }
    }

    /** Routes ordering decisions to the Ant log. */
    private final class AntListener implements RunAfterOrdering.Listener
    {
        @Override
        public void info( String message )
        {
            log( "runAfter ordering : " + message, Project.MSG_INFO );
        }

        @Override
        public void error( String message )
        {
            log( "runAfter ordering : " + message, Project.MSG_ERR );
        }
    }
}
