package org.ovirt.engine.core.dal.dbbroker;

import java.io.IOException;
import java.util.Properties;

import javax.annotation.Resource;
import javax.inject.Singleton;
import javax.sql.DataSource;

import org.ovirt.engine.core.utils.EngineLocalConfig;
import org.ovirt.engine.core.utils.ResourceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.SQLErrorCodeSQLExceptionTranslator;

/**
 * A locator singleton for looking up (and initializing) DbFacade instance
 */

@Singleton
public class DbFacadeLocator {
    private static final Logger log = LoggerFactory.getLogger(DbFacadeLocator.class);

    // Default values for the configuration (these will be replaced with the
    // values from the configuration file):
    private static final int DEFAULT_CHECK_INTERVAL = 5000;
    private static final int DEFAULT_CONNECTION_TIMEOUT = 30000;

    // Time to wait between checks of the database connection and maximum time
    // to wait for a connection:
    private static int checkInterval = DEFAULT_CHECK_INTERVAL;
    private static int connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;

    @Resource(mappedName = "java:/ENGINEDataSource")
    private DataSource ds;

    private DbFacadeLocator() {
    }

    /**
     * configure the dbFacade.
     *
     * @return the reference to the dbFacade if it was successfully created or
     *   <code>null</code> if something failed
     */
    protected void configure(DbFacade dbFacade) {
        // Load the configuration:
        loadDbFacadeConfig();

        // Load the dialect:
        DbEngineDialect dialect = loadDbEngineDialect();

        // configure the dbFacade:
        dbFacade.setOnStartConnectionTimeout(connectionTimeout);
        dbFacade.setConnectionCheckInterval(checkInterval);
        dbFacade.setDbEngineDialect(dialect);
        JdbcTemplate template = dialect.createJdbcTemplate(ds);
        SQLErrorCodeSQLExceptionTranslator tr = new CustomSQLErrorCodeSQLExceptionTranslator(ds);
        template.setExceptionTranslator(tr);
        dbFacade.setTemplate(template);
    }

    /**
     * Generate and sets the database engine dialect object according to configuration.
     *
     * @throws Exception
     */
    public static DbEngineDialect loadDbEngineDialect() {
        final String ENGINE_DB_ENGINE_PROPERTIES = "engine-db-engine.properties";
        final String DIALECT = "DbEngineDialect";
        Properties props = null;
        try {
            props = ResourceUtils.loadProperties(DbFacadeLocator.class, ENGINE_DB_ENGINE_PROPERTIES);
        }
        catch (IOException exception) {
            throw new IllegalStateException(
                "Can't load properties from resource \"" +
                ENGINE_DB_ENGINE_PROPERTIES + "\".", exception
            );
        }
        String dialect = props.getProperty(DIALECT);
        if (dialect == null) {
            throw new IllegalStateException(
                "Can't load property \"" + DIALECT + "\" from resource \"" +
                 ENGINE_DB_ENGINE_PROPERTIES + "\"."
            );
        }
        try {
            return (DbEngineDialect) Class.forName(dialect).newInstance();
        }
        catch (Exception exception) {
            throw new IllegalStateException(
                "Can't create instance of dialect class \"" + dialect + "\".",
                exception
            );
        }
    }

    public static void loadDbFacadeConfig() {
        EngineLocalConfig config = EngineLocalConfig.getInstance();
        try {
            connectionTimeout = config.getInteger("ENGINE_DB_CONNECTION_TIMEOUT");
            checkInterval = config.getInteger("ENGINE_DB_CHECK_INTERVAL");
        }
        catch (Exception exception) {
            log.warn("Can't load connection checking parameters of DB facade, "
                            + "will continue using the default values. Error: {}",
                exception.getMessage());
            log.debug("Exception", exception);
        }
    }
}
