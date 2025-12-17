package fr.paris.lutece.utils.sql;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Knows how to parse lutece SQL file paths. Holds useful information about the parsed file.
 */
public class SqlPathInfo
{
    /** true when create_XXX or init_XXX, false when update_XXX */
    private boolean create;
    /** plugin name ("core", if not a plugin) */
    private String plugin;
    /** theme name (example : "mytheme" for themes/mytheme ) */
    private String theme;
    private boolean isTheme;
    /** module name (example : "template" for forms-template) */
    private String module;
    /** starting version for an update script. Only available when create is false */
    private PluginVersion srcVersion;
    /** destination version for an update script. Only available when create is false */
    private PluginVersion dstVersion;


    public boolean isTheme()
    {
        return isTheme;
    }
    public void setTheme(boolean isTheme)
    {
        this.isTheme = isTheme;
    }

    public boolean isCreate()
    {
        return create;
    }

    public String getPlugin()
    {
        return plugin;
    }
    public String getTheme()
    {
        return theme;
    }

    public String getFullPluginName()
    {
        return (module == null || module.trim().isEmpty()) ? plugin : (plugin + "-" + module);
    }

    public PluginVersion getSrcVersion()
    {
        return srcVersion;
    }

    public PluginVersion getDstVersion()
    {
        return dstVersion;
    }

    @Override
    public String toString()
    {
        return "SqlPathInfo [create=" + create + ", theme=" + getTheme() + ", plugin=" + getFullPluginName() + ", module=" + module + ", srcVersion=" + srcVersion + ", dstVersion="
                + dstVersion + "]";
    }

    // having a pattern that does everything is not easy to read
    // so we declare several patterns
    // example : "sql/plugins/testpourliquibase/plugin/create_db_testpourliquibase.sql"
    private static final Pattern SQL_CREATE_PATTERN = Pattern
            .compile("sql/plugins/(?<plugin>[\\p{Alnum}\\-]+)(/modules/(?<module>[\\p{Alnum}]+))?/(core|plugin)/(init|create)[\\p{Alnum}_\\-]+\\.sql");
    // example : "sql/plugins/testpourliquibase/upgrade/update_db_testpourliquibase-0.0.9-1.0.0.sql"
    private static final Pattern SQL_UPDATE_PATTERN = Pattern.compile(
            "sql/plugins/(?<plugin>[\\p{Alnum}\\-]+)(/modules/(?<module>[\\p{Alnum}]+))?/upgrades?/(update|upgrade)[\\p{Alnum}\\-_]+[\\-_]?(?<srcVersion>([0-9]+(\\.[0-9]+)*))[\\-_](?<dstVersion>([0-9]+(\\.[0-9]+)*))\\.sql");

    // matches src/sql/create_db_lutece_core.sql and src/sql/init_db_lutece_core.sql
    private static final Pattern SQL_CORE_CREATE = Pattern.compile("sql/(init|create)[\\p{Alpha}_]+(?<plugin>core)\\.sql");
    private static final Pattern SQL_CORE_UPDATE = Pattern
            .compile("sql/upgrade/update_db_lutece_(?<plugin>core)-(?<srcVersion>([0-9]+(\\.[0-9]+)*))-(?<dstVersion>([0-9]+(\\.[0-9]+)*))\\.sql");
    // capturing group names (literally written in the patterns above for readability)
    private static final String DST_VERSION_GROUP = "dstVersion";
    private static final String SRC_VERSION_GROUP = "srcVersion";
    private static final String PLUGIN_GROUP = "plugin";
    private static final String MODULE_GROUP = "module";
    private static final String THEME_GROUP = "theme";

   //Themes
    private static final Pattern SQL_THEME_CREATE_PATTERN =Pattern.compile(
        "sql/themes/(?<theme>[\\p{Alnum}]+)/(init|create)[\\p{Alnum}_\\-]*\\.sql");

    private static final Pattern SQL_THEME_UPDATE_PATTERN = Pattern.compile(
            "sql/themes/(?<theme>[\\p{Alnum}]+)/upgrade/(update|upgrade)[\\p{Alnum}_\\-]+[\\-_]?(?<srcVersion>([0-9]+(\\.[0-9]+)*))[\\-_](?<dstVersion>([0-9]+(\\.[0-9]+)*))\\.sql");


    /**
     * Creates a SqlPathInfo instance from a file path
     * 
     * @param sqlFilePath path of the SQL file
     * @return a new instance, or null, if the given path matches cannot be recognized
     */
    public static SqlPathInfo parse(String sqlFilePath)
    {
        Matcher matcher = SQL_CREATE_PATTERN.matcher(sqlFilePath);
        if (matcher.matches())
        {
            return createInfo(matcher, true, false);
        }
        matcher = SQL_CORE_CREATE.matcher(sqlFilePath);
        if (matcher.matches())
        {
            return createInfo(matcher, false, false);
        }
        matcher = SQL_THEME_CREATE_PATTERN.matcher(sqlFilePath);
        if (matcher.matches())
        {

            return createInfo(matcher, false, true);
        }

        matcher = SQL_UPDATE_PATTERN.matcher(sqlFilePath);
        if (matcher.matches())
        {
            return updateInfo(matcher, true, false);
        }
        matcher = SQL_CORE_UPDATE.matcher(sqlFilePath);
        if (matcher.matches())
        {
            return updateInfo(matcher, false, false);
        }
        matcher = SQL_THEME_UPDATE_PATTERN .matcher(sqlFilePath);
        if (matcher.matches())
        {
            return updateInfo(matcher, false, true);
        }


        return null;
    }

    private static SqlPathInfo basicInfo(Matcher matcher, boolean lookForModule, boolean lookForTheme)
    {
        SqlPathInfo info = new SqlPathInfo();
        if(lookForTheme)
        {
            info.theme = matcher .group(THEME_GROUP);
            info.setTheme(true);
        }
        else
        {
            if (lookForModule)
                info.module = matcher.group(MODULE_GROUP);
            info.plugin = matcher.group(PLUGIN_GROUP);
        }
        
        return info;
    }

    private static SqlPathInfo createInfo(Matcher matcher, boolean lookForModule, boolean lookForTheme)
    {
        SqlPathInfo info = basicInfo(matcher, lookForModule,lookForTheme);
        info.create = true;
        return info;
    }

    private static SqlPathInfo updateInfo(Matcher matcher, boolean lookForModule, boolean lookForTheme) {
        SqlPathInfo info = basicInfo(matcher, lookForModule, lookForTheme);
        info.create = false;
        info.srcVersion = PluginVersion.of(matcher.group(SRC_VERSION_GROUP));
        info.dstVersion = PluginVersion.of(matcher.group(DST_VERSION_GROUP));
        return info;
    }

}