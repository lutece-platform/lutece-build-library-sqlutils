package fr.paris.lutece.utils.sql;

import java.util.ArrayList;
import java.util.List;

/**
 * Compares versions. Immutable.
 * 
 * A version is a string such as "1.0.0", i.e. a dot separated list of integers. non-integer components are not supported.
 *
 */
public class PluginVersion implements Comparable<PluginVersion>
{
    private static final String SNAPSHOT = "-SNAPSHOT";
    private static final List<String> UNSTABLE_SUFFIXE_VERSION = List.of("BETA", "ALPHA", "RC");
    private final List<Integer> components = new ArrayList<>();
    private boolean _isSnapshot = false;
    private boolean _isUnstable = false;

    
    public boolean isUnstable() {
        return _isUnstable;
    }

    public void setUnstable(boolean _isUnstable) {
        this._isUnstable = _isUnstable;
    }

    private PluginVersion(String version)
    {
        if (version != null)
        {
            if ( version.endsWith(SNAPSHOT))
            {
                this._isSnapshot = true;
                version = version.substring(0, version.length() - SNAPSHOT.length());
            }
            else
            {
                for (String suffixe : UNSTABLE_SUFFIXE_VERSION)
                {
                    if (version.toUpperCase().contains("-" + suffixe))
                    {
                        this._isUnstable = true;
                        version = version.split("-")[0];;
                        break;
                    }
                }
            }

            for (String element : version.split("\\."))
                components.add(Integer.parseInt(element));
        }
    }

    /**
     * Returns a copy of the components
     * 
     * @return an integer ordered list
     */
    public List<Integer> components()
    {
        return new ArrayList<>(components);
    }

    @Override
    public int compareTo(PluginVersion o)
    {
        if (o == null)
            return 1;
        for (int i = 0; i < Math.min(o.components.size(), components.size()); i++)
        {
            Integer i1 = components.get(i);
            Integer i2 = o.components.get(i);
            if (!i1.equals(i2))
                return i1.compareTo(i2);
        }
        return Integer.compare(components.size(), o.components.size());
    }

    /**
     * Parses and creates a PluginVersion from a version string.
     * 
     * Might throw a RuntimeException if parsing fails (bad format).
     * 
     * @param version the source string
     * @return a PluginVersion instance or null if the source is null
     */
    public static PluginVersion of(String version)
    {
        return version == null ? null : new PluginVersion(version);
    }

    @Override
    public String toString()
    {
        return components.toString();
    }

   /**
    * Indicates if this version is a snapshot
    * @return true if this version is a snapshot
    */ 
   public boolean isSnapshot()
    {
        return _isSnapshot;
    }

}
