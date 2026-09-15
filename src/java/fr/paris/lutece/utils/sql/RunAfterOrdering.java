package fr.paris.lutece.utils.sql;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Execution order of lutece SQL scripts honouring the <code>runAfter</code> header directive.
 *
 * Scripts are ordered as liquibase and Ant do by default (alphabetical path order), except for plugins
 * declaring an explicit ordering directive in the leading comment block of any of their scripts :
 *
 * <pre>
 * --liquibase formatted sql
 * --lutece runAfter:genericattributes
 * --changeset author:id
 * </pre>
 *
 * The directive is plugin-scoped : ALL the scripts of the declaring plugin are then ordered as if the
 * plugin's directory lived under the target plugin's directory, AFTER all the target's own scripts, while
 * physically staying in place. Directives chain to any depth : if A declares runAfter:B and B declares
 * runAfter:C, the resulting order is C, then B, then A. Invalid directives are ignored with an error
 * reported to the {@link Listener} and the plugin keeps its natural position : conflicting targets inside
 * one plugin, unknown or script-less target, self reference, involvement of core, and dependency cycles
 * (broken deterministically, plugins being resolved in name order).
 *
 * The whole ordering is captured by {@link #keyOf(String)} : two consumers sorting the same set of paths
 * by that key produce the same sequence. This is the parity contract between the liquibase startup of
 * plugin-liquibase and the Ant initialization of build-config, which both delegate to this class.
 *
 * Paths are expected in the classpath form <code>sql/plugins/&lt;plugin&gt;/.../file.sql</code> with
 * <code>/</code> separators, as accepted by {@link SqlPathInfo#parse(String)}. Callers normalize their own
 * forms (a <code>WEB-INF/classes/</code> prefix, platform separators, a directory-relative name) before
 * building the ordering and before every call to {@link #keyOf(String)}.
 *
 * Instances are immutable once built and not thread-safe to build.
 */
public final class RunAfterOrdering
{
    /** A SQL script : its normalized path and a way to read its header. */
    public interface Script
    {
        /** @return the path, in the <code>sql/...</code> classpath form */
        String getPath( );

        /**
         * @return a fresh stream on the script content, closed by the caller
         * @throws IOException on read failure
         */
        InputStream open( ) throws IOException;
    }

    /** Receives the decisions taken while building the ordering. */
    public interface Listener
    {
        /** @param message a resolved directive, worth an INFO log */
        void info( String message );

        /** @param message an ignored directive or a read failure, worth an ERROR log */
        void error( String message );
    }

    /** A listener discarding every message. */
    public static final Listener SILENT = new Listener( )
    {
        @Override
        public void info( String message )
        {
        }

        @Override
        public void error( String message )
        {
        }
    };

    private static final Pattern RUN_AFTER_PATTERN = Pattern.compile( "^--\\s*lutece\\b.*\\brunAfter:([\\p{Alnum}\\-]+)" );
    private static final int MAX_HEADER_LINES = 20;
    private static final String CORE_PLUGIN_NAME = "core";
    private static final String SQL_EXTENSION = ".sql";
    private static final String PRERUN_FILE_PREFIX = "prerun_db_";
    // '~' sorts after any alphanumeric, so relocated scripts land after the target's own files.
    // FILE_MARKER sorts before AFTER_MARKER ("/" < "r"), so a relocated plugin's files always sort
    // before the files of plugins relocated after IT (chained directives).
    private static final String AFTER_MARKER = "/~runAfter/";
    private static final String FILE_MARKER = "/~/";

    /** normalized path -> sort key, for the files of relocated plugins only */
    private final Map<String, String> _relocatedKeys;
    /** component -> effective target, for the directives finally honoured */
    private final Map<String, String> _targets;

    private RunAfterOrdering( Map<String, String> relocatedKeys, Map<String, String> targets )
    {
        _relocatedKeys = Collections.unmodifiableMap( relocatedKeys );
        _targets = Collections.unmodifiableMap( targets );
    }

    /**
     * Builds the ordering of a set of scripts.
     *
     * @param scripts
     *            every SQL script of the webapp (all of them, not only the ones to sort : a target must be
     *            recognized by its scripts even when the caller only sorts a subset)
     * @param targetExists
     *            an extra existence test on the target plugin name, typically "is a declared plugin" ; use
     *            <code>name -> true</code> when the only available knowledge is the scripts themselves
     * @param listener
     *            receives resolved and ignored directives
     * @return the ordering
     */
    public static RunAfterOrdering build( Iterable<? extends Script> scripts, Predicate<String> targetExists, Listener listener )
    {
        // single pass over every SQL file : owning plugin, directory prefix, runAfter directive
        Map<String, String> basePrefixes = new HashMap<>( );
        Map<String, List<String>> componentFiles = new HashMap<>( );
        // TreeMap so that locations are resolved in name order : deterministic cycle breaking
        Map<String, String> targets = new TreeMap<>( );
        Set<String> conflicting = new HashSet<>( );
        Set<String> seen = new HashSet<>( );
        for ( Script script : scripts )
        {
            String path = script.getPath( );
            if ( !path.endsWith( SQL_EXTENSION ) || !seen.add( path ) )
            {
                continue;
            }
            SqlPathInfo info = SqlPathInfo.parse( path );
            // themes and unrecognized files keep their natural position
            if ( info == null || info.isTheme( ) )
            {
                continue;
            }
            String component = info.getFullPluginName( );
            basePrefixes.putIfAbsent( component, directoryPrefix( path ) );
            componentFiles.computeIfAbsent( component, k -> new ArrayList<>( ) ).add( path );
            // pre-execution scripts are liquibase-only mini changelogs : no directive is read from them
            if ( isPrerunScript( path ) )
            {
                continue;
            }
            String target = readRunAfterTarget( script, listener );
            if ( target == null )
            {
                continue;
            }
            String previous = targets.put( component, target );
            if ( previous != null && !previous.equals( target ) )
            {
                listener.error( "Plugin " + component + " declares conflicting runAfter targets (" + previous + " and " + target + ") : directives ignored" );
                conflicting.add( component );
            }
        }
        targets.keySet( ).removeAll( conflicting );

        for ( Iterator<Map.Entry<String, String>> iterator = targets.entrySet( ).iterator( ); iterator.hasNext( ); )
        {
            Map.Entry<String, String> entry = iterator.next( );
            String component = entry.getKey( );
            String target = entry.getValue( );
            String error = null;
            if ( CORE_PLUGIN_NAME.equals( component ) || CORE_PLUGIN_NAME.equals( target ) )
            {
                error = "core cannot take part in runAfter ordering";
            }
            else if ( component.equals( target ) )
            {
                error = "a plugin cannot run after itself";
            }
            else if ( !targetExists.test( target ) )
            {
                error = "no such plugin is declared";
            }
            else if ( !basePrefixes.containsKey( target ) )
            {
                error = "the target plugin has no SQL script";
            }
            if ( error != null )
            {
                listener.error( "runAfter:" + target + " declared by plugin " + component + " ignored : " + error );
                iterator.remove( );
            }
        }

        Map<String, String> locations = new HashMap<>( );
        for ( String component : new ArrayList<>( targets.keySet( ) ) )
        {
            resolveLocation( component, targets, basePrefixes, locations, new LinkedHashSet<>( ), listener );
        }

        Map<String, String> keys = new HashMap<>( );
        for ( Map.Entry<String, String> entry : targets.entrySet( ) )
        {
            String component = entry.getKey( );
            String location = locations.get( component );
            listener.info( "Scripts of plugin " + component + " will run after those of plugin " + entry.getValue( ) );
            for ( String path : componentFiles.get( component ) )
            {
                keys.put( path, location + FILE_MARKER + path );
            }
        }
        return new RunAfterOrdering( keys, targets );
    }

    /**
     * The sort key of a script : its own path for plugins without a directive, a virtual path under the
     * target's location otherwise. Distinct paths always yield distinct keys since the key embeds the whole
     * path, so sorting by key is a total order consistent with equality of paths.
     *
     * @param path
     *            the normalized path
     * @return the sort key
     */
    public String keyOf( String path )
    {
        return _relocatedKeys.getOrDefault( path, path );
    }

    /**
     * @return the directives finally honoured, component name to target plugin name, in component name order
     */
    public Map<String, String> getTargets( )
    {
        return _targets;
    }

    /**
     * Whether the file name (whatever its location) is a reserved pre-execution script
     * <code>prerun_db_&lt;component&gt;.sql</code>.
     *
     * @param path
     *            a path with <code>/</code> separators
     * @return true for a prerun script
     */
    public static boolean isPrerunScript( String path )
    {
        String fileName = path.substring( path.lastIndexOf( '/' ) + 1 );
        return fileName.startsWith( PRERUN_FILE_PREFIX ) && fileName.endsWith( SQL_EXTENSION );
    }

    /**
     * Resolves the effective location of a plugin : its own directory, or, when it declares a (valid)
     * runAfter directive, a virtual directory sorting right after its target's effective location. Cycles
     * are broken at the plugin closing the loop, whose directive is discarded.
     */
    private static String resolveLocation( String component, Map<String, String> targets, Map<String, String> basePrefixes, Map<String, String> locations,
            Set<String> visiting, Listener listener )
    {
        String location = locations.get( component );
        if ( location != null )
        {
            return location;
        }
        String target = targets.get( component );
        if ( target == null )
        {
            location = basePrefixes.get( component );
        }
        else if ( !visiting.add( component ) )
        {
            listener.error( "Cycle detected in runAfter directives at plugin " + component + " (chain : " + visiting + ") : directive ignored" );
            targets.remove( component );
            location = basePrefixes.get( component );
        }
        else
        {
            String parentLocation = resolveLocation( target, targets, basePrefixes, locations, visiting, listener );
            visiting.remove( component );
            String existing = locations.get( component );
            // the recursion may have broken a cycle at this very component : its location is already settled
            if ( existing != null )
            {
                return existing;
            }
            location = parentLocation + AFTER_MARKER + component;
        }
        locations.put( component, location );
        return location;
    }

    /**
     * Returns the plugin's SQL directory : the path minus the (core|plugin|upgrade)/file.sql trailing part.
     */
    private static String directoryPrefix( String path )
    {
        int last = path.lastIndexOf( '/' );
        int previous = last > 0 ? path.lastIndexOf( '/', last - 1 ) : -1;
        return previous > 0 ? path.substring( 0, previous ) : path.substring( 0, Math.max( last, 0 ) );
    }

    /**
     * Reads the runAfter target declared in the leading comment block of a script, if any.
     *
     * @param script
     *            the script
     * @param listener
     *            receives read failures
     * @return the target plugin name, or null when the script declares nothing
     */
    public static String readRunAfterTarget( Script script, Listener listener )
    {
        try ( InputStream stream = script.open( ) )
        {
            BufferedReader reader = new BufferedReader( new InputStreamReader( stream, StandardCharsets.UTF_8 ) );
            String line;
            int count = 0;
            while ( ( line = reader.readLine( ) ) != null && count++ < MAX_HEADER_LINES )
            {
                String trimmed = line.trim( );
                if ( !trimmed.isEmpty( ) && !trimmed.startsWith( "--" ) )
                {
                    // past the leading comment block : the directive must appear before any SQL
                    return null;
                }
                Matcher matcher = RUN_AFTER_PATTERN.matcher( trimmed );
                if ( matcher.find( ) )
                {
                    return matcher.group( 1 );
                }
            }
        }
        catch( IOException e )
        {
            listener.error( "Could not read header of " + script.getPath( ) + " : " + e );
        }
        return null;
    }
}
