package com.example.flyway_9_spring_2_7_2.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.migration.JavaMigration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.boot.autoconfigure.flyway.FlywayDataSource;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.autoconfigure.flyway.FlywayProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.jdbc.DatabaseDriver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.jdbc.support.MetaDataAccessException;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.DatabaseMetaData;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Because everything interesting is private in FlywayAutoConfiguration the only way around is copy-paste.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(JdbcUtils.class)
@ConditionalOnMissingBean(Flyway.class)
@EnableConfigurationProperties(FlywayProperties.class)
public class Flyway9Config {

    @Bean
    public Flyway flyway(FlywayProperties properties, ResourceLoader resourceLoader,
                         ObjectProvider<DataSource> dataSource, @FlywayDataSource ObjectProvider<DataSource> flywayDataSource,
                         ObjectProvider<FlywayConfigurationCustomizer> fluentConfigurationCustomizers,
                         ObjectProvider<JavaMigration> javaMigrations, ObjectProvider<Callback> callbacks) {
        FluentConfiguration configuration = new FluentConfiguration(resourceLoader.getClassLoader());
        configureDataSource(configuration, properties, flywayDataSource.getIfAvailable(), dataSource.getIfUnique());
        configureProperties(configuration, properties);
        List<Callback> orderedCallbacks = callbacks.orderedStream().collect(Collectors.toList());
        configureCallbacks(configuration, orderedCallbacks);
        fluentConfigurationCustomizers.orderedStream().forEach((customizer) -> customizer.customize(configuration));
        configureFlywayCallbacks(configuration, orderedCallbacks);
        List<JavaMigration> migrations = javaMigrations.stream().collect(Collectors.toList());
        configureJavaMigrations(configuration, migrations);
        return configuration.load();
    }

    private void configureDataSource(FluentConfiguration configuration, FlywayProperties properties,
                                     DataSource flywayDataSource, DataSource dataSource) {
        DataSource migrationDataSource = getMigrationDataSource(properties, flywayDataSource, dataSource);
        configuration.dataSource(migrationDataSource);
    }

    private DataSource getMigrationDataSource(FlywayProperties properties, DataSource flywayDataSource,
                                              DataSource dataSource) {
        if (flywayDataSource != null) {
            return flywayDataSource;
        }
        if (properties.getUrl() != null) {
            DataSourceBuilder<?> builder = DataSourceBuilder.create().type(SimpleDriverDataSource.class);
            builder.url(properties.getUrl());
            applyCommonBuilderProperties(properties, builder);
            return builder.build();
        }
        if (properties.getUser() != null && dataSource != null) {
            DataSourceBuilder<?> builder = DataSourceBuilder.derivedFrom(dataSource)
                    .type(SimpleDriverDataSource.class);
            applyCommonBuilderProperties(properties, builder);
            return builder.build();
        }
        Assert.state(dataSource != null, "Flyway migration DataSource missing");
        return dataSource;
    }

    private void applyCommonBuilderProperties(FlywayProperties properties, DataSourceBuilder<?> builder) {
        builder.username(properties.getUser());
        builder.password(properties.getPassword());
        if (StringUtils.hasText(properties.getDriverClassName())) {
            builder.driverClassName(properties.getDriverClassName());
        }
    }

    private void configureProperties(FluentConfiguration configuration, FlywayProperties properties) {
        PropertyMapper map = PropertyMapper.get().alwaysApplyingWhenNonNull();
        String[] locations = new LocationResolver(configuration.getDataSource())
                .resolveLocations(properties.getLocations()).toArray(new String[0]);
        configureFailOnMissingLocations(configuration, properties.isFailOnMissingLocations());
        map.from(locations).to(configuration::locations);
        map.from(properties.getEncoding()).to(configuration::encoding);
        map.from(properties.getConnectRetries()).to(configuration::connectRetries);
        // No method reference for compatibility with Flyway < 7.15
        map.from(properties.getConnectRetriesInterval())
                .to((interval) -> configuration.connectRetriesInterval((int) interval.getSeconds()));
        // No method reference for compatibility with Flyway 6.x
        map.from(properties.getLockRetryCount())
                .to((lockRetryCount) -> configuration.lockRetryCount(lockRetryCount));
        // No method reference for compatibility with Flyway 5.x
        map.from(properties.getDefaultSchema()).to((schema) -> configuration.defaultSchema(schema));
        map.from(properties.getSchemas()).as(StringUtils::toStringArray).to(configuration::schemas);
        configureCreateSchemas(configuration, properties.isCreateSchemas());
        map.from(properties.getTable()).to(configuration::table);
        // No method reference for compatibility with Flyway 5.x
        map.from(properties.getTablespace()).to((tablespace) -> configuration.tablespace(tablespace));
        map.from(properties.getBaselineDescription()).to(configuration::baselineDescription);
        map.from(properties.getBaselineVersion()).to(configuration::baselineVersion);
        map.from(properties.getInstalledBy()).to(configuration::installedBy);
        map.from(properties.getPlaceholders()).to(configuration::placeholders);
        map.from(properties.getPlaceholderPrefix()).to(configuration::placeholderPrefix);
        map.from(properties.getPlaceholderSuffix()).to(configuration::placeholderSuffix);
        // No method reference for compatibility with Flyway version < 8.0
        map.from(properties.getPlaceholderSeparator())
                .to((placeHolderSeparator) -> configuration.placeholderSeparator(placeHolderSeparator));
        map.from(properties.isPlaceholderReplacement()).to(configuration::placeholderReplacement);
        map.from(properties.getSqlMigrationPrefix()).to(configuration::sqlMigrationPrefix);
        map.from(properties.getSqlMigrationSuffixes()).as(StringUtils::toStringArray)
                .to(configuration::sqlMigrationSuffixes);
        map.from(properties.getSqlMigrationSeparator()).to(configuration::sqlMigrationSeparator);
        map.from(properties.getRepeatableSqlMigrationPrefix()).to(configuration::repeatableSqlMigrationPrefix);
        map.from(properties.getTarget()).to(configuration::target);
        map.from(properties.isBaselineOnMigrate()).to(configuration::baselineOnMigrate);
        map.from(properties.isCleanDisabled()).to(configuration::cleanDisabled);
        map.from(properties.isCleanOnValidationError()).to(configuration::cleanOnValidationError);
        map.from(properties.isGroup()).to(configuration::group);
        // REMOVED - no direct support in new Flyway configureIgnoredMigrations(configuration, properties, map);
        map.from(properties.isMixed()).to(configuration::mixed);
        map.from(properties.isOutOfOrder()).to(configuration::outOfOrder);
        map.from(properties.isSkipDefaultCallbacks()).to(configuration::skipDefaultCallbacks);
        map.from(properties.isSkipDefaultResolvers()).to(configuration::skipDefaultResolvers);
        configureValidateMigrationNaming(configuration, properties.isValidateMigrationNaming());
        map.from(properties.isValidateOnMigrate()).to(configuration::validateOnMigrate);
        map.from(properties.getInitSqls()).whenNot(CollectionUtils::isEmpty)
                .as((initSqls) -> StringUtils.collectionToDelimitedString(initSqls, "\n"))
                .to(configuration::initSql);
        map.from(properties.getScriptPlaceholderPrefix())
                .to((prefix) -> configuration.scriptPlaceholderPrefix(prefix));
        map.from(properties.getScriptPlaceholderSuffix())
                .to((suffix) -> configuration.scriptPlaceholderSuffix(suffix));
        // Pro properties
        map.from(properties.getBatch()).to(configuration::batch);
        map.from(properties.getDryRunOutput()).to(configuration::dryRunOutput);
        map.from(properties.getErrorOverrides()).to(configuration::errorOverrides);
        map.from(properties.getLicenseKey()).to(configuration::licenseKey);
        map.from(properties.getOracleSqlplus()).to(configuration::oracleSqlplus);
        // No method reference for compatibility with Flyway 5.x
        map.from(properties.getOracleSqlplusWarn())
                .to((oracleSqlplusWarn) -> configuration.oracleSqlplusWarn(oracleSqlplusWarn));
        map.from(properties.getStream()).to(configuration::stream);
        map.from(properties.getUndoSqlMigrationPrefix()).to(configuration::undoSqlMigrationPrefix);
        // No method reference for compatibility with Flyway 6.x
        map.from(properties.getCherryPick()).to((cherryPick) -> configuration.cherryPick(cherryPick));
        // No method reference for compatibility with Flyway 6.x
        map.from(properties.getJdbcProperties()).whenNot(Map::isEmpty)
                .to((jdbcProperties) -> configuration.jdbcProperties(jdbcProperties));
        // No method reference for compatibility with Flyway 6.x
        map.from(properties.getKerberosConfigFile())
                .to((configFile) -> configuration.kerberosConfigFile(configFile));
        // No method reference for compatibility with Flyway 6.x
        map.from(properties.getOracleKerberosCacheFile())
                .to((cacheFile) -> configuration.oracleKerberosCacheFile(cacheFile));
        // No method reference for compatibility with Flyway 6.x
        map.from(properties.getOutputQueryResults())
                .to((outputQueryResults) -> configuration.outputQueryResults(outputQueryResults));
        // Removed as incompatible - plugin access needs config
        // map.from(properties.getSqlServerKerberosLoginFile()).whenNonNull()
        //        .to(this::configureSqlServerKerberosLoginFile);
        // No method reference for compatibility with Flyway 6.x
        map.from(properties.getSkipExecutingMigrations())
                .to((skipExecutingMigrations) -> configuration.skipExecutingMigrations(skipExecutingMigrations));
        // No method reference for compatibility with Flyway < 7.8
        map.from(properties.getIgnoreMigrationPatterns()).whenNot(List::isEmpty)
                .to((ignoreMigrationPatterns) -> configuration
                        .ignoreMigrationPatterns(ignoreMigrationPatterns.toArray(new String[0])));
        // No method reference for compatibility with Flyway version < 7.9
        map.from(properties.getDetectEncoding())
                .to((detectEncoding) -> configuration.detectEncoding(detectEncoding));
        // No method reference for compatibility with Flyway version < 8.0
        // Not supported in 9.x
        //map.from(properties.getBaselineMigrationPrefix())
        //        .to((baselineMigrationPrefix) -> configuration.baselineMigrationPrefix(baselineMigrationPrefix));
    }

    private void configureFailOnMissingLocations(FluentConfiguration configuration,
                                                 boolean failOnMissingLocations) {
        try {
            configuration.failOnMissingLocations(failOnMissingLocations);
        }
        catch (NoSuchMethodError ex) {
            // Flyway < 7.9
        }
    }

    private void configureCreateSchemas(FluentConfiguration configuration, boolean createSchemas) {
        try {
            configuration.createSchemas(createSchemas);
        }
        catch (NoSuchMethodError ex) {
            // Flyway < 6.5
        }
    }

    private void configureValidateMigrationNaming(FluentConfiguration configuration,
                                                  boolean validateMigrationNaming) {
        try {
            configuration.validateMigrationNaming(validateMigrationNaming);
        }
        catch (NoSuchMethodError ex) {
            // Flyway < 6.2
        }
    }

    private void configureCallbacks(FluentConfiguration configuration, List<Callback> callbacks) {
        if (!callbacks.isEmpty()) {
            configuration.callbacks(callbacks.toArray(new Callback[0]));
        }
    }

    private void configureFlywayCallbacks(FluentConfiguration flyway, List<Callback> callbacks) {
        if (!callbacks.isEmpty()) {
            flyway.callbacks(callbacks.toArray(new Callback[0]));
        }
    }

    private void configureJavaMigrations(FluentConfiguration flyway, List<JavaMigration> migrations) {
        if (!migrations.isEmpty()) {
            try {
                flyway.javaMigrations(migrations.toArray(new JavaMigration[0]));
            }
            catch (NoSuchMethodError ex) {
                // Flyway 5.x
            }
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public FlywayMigrationInitializer flywayInitializer(Flyway flyway,
                                                        ObjectProvider<FlywayMigrationStrategy> migrationStrategy) {
        return new FlywayMigrationInitializer(flyway, migrationStrategy.getIfAvailable());
    }

    private static class LocationResolver {

        private static final String VENDOR_PLACEHOLDER = "{vendor}";

        private final DataSource dataSource;

        LocationResolver(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        List<String> resolveLocations(List<String> locations) {
            if (usesVendorLocation(locations)) {
                DatabaseDriver databaseDriver = getDatabaseDriver();
                return replaceVendorLocations(locations, databaseDriver);
            }
            return locations;
        }

        private List<String> replaceVendorLocations(List<String> locations, DatabaseDriver databaseDriver) {
            if (databaseDriver == DatabaseDriver.UNKNOWN) {
                return locations;
            }
            String vendor = databaseDriver.getId();
            return locations.stream().map((location) -> location.replace(VENDOR_PLACEHOLDER, vendor))
                    .collect(Collectors.toList());
        }

        private DatabaseDriver getDatabaseDriver() {
            try {
                String url = JdbcUtils.extractDatabaseMetaData(this.dataSource, DatabaseMetaData::getURL);
                return DatabaseDriver.fromJdbcUrl(url);
            }
            catch (MetaDataAccessException ex) {
                throw new IllegalStateException(ex);
            }

        }

        private boolean usesVendorLocation(Collection<String> locations) {
            for (String location : locations) {
                if (location.contains(VENDOR_PLACEHOLDER)) {
                    return true;
                }
            }
            return false;
        }

    }

}