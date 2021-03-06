package savetheenvironment.profiles;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseFactoryBean;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.sql.Driver;


public class Main {
    public static void main(String[] arrrImAPirate) throws Throwable {
        ApplicationContext annotationConfigApplicationContext = SpringApplication.run(ServiceConfiguration.class);
        DataSource dataSource = annotationConfigApplicationContext.getBean(DataSource.class);
        System.out.println("Retrieved " + dataSource.toString());
    }
}

@EnableTransactionManagement
@ComponentScan
@Configuration
@Import({ProductionDataSourceConfiguration.class, EmbeddedDataSourceConfiguration.class})
class ServiceConfiguration {

    public static final String PRODUCTION_PROFILE = "production";
    public static final String DEVELOPMENT_PROFILE = "test";

    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }


}

@Configuration
@PropertySource("/config.properties")
@Profile({ServiceConfiguration.PRODUCTION_PROFILE})
class ProductionDataSourceConfiguration {

    @Bean
    public DataSource dataSource(Environment env) {
        SimpleDriverDataSource dataSource = new SimpleDriverDataSource();
        dataSource.setDriverClass(env.getPropertyAsClass("db.driverClass", Driver.class));
        dataSource.setUrl(env.getProperty("db.url").trim());
        dataSource.setUsername(env.getProperty("db.user").trim());
        dataSource.setPassword(env.getProperty("db.password").trim());
        return dataSource;
    }
}

@Configuration
@Profile({ServiceConfiguration.DEVELOPMENT_PROFILE})
class EmbeddedDataSourceConfiguration {

    @Bean
    public DataSource dataSource() {

        ClassPathResource classPathResource = new ClassPathResource("/crm-schema-h2.sql");

        ResourceDatabasePopulator resourceDatabasePopulator = new ResourceDatabasePopulator();
        resourceDatabasePopulator.addScript(classPathResource);

        EmbeddedDatabaseFactoryBean embeddedDatabaseFactoryBean = new EmbeddedDatabaseFactoryBean();
        embeddedDatabaseFactoryBean.setDatabasePopulator(resourceDatabasePopulator);
        embeddedDatabaseFactoryBean.setDatabaseName("crm");
        embeddedDatabaseFactoryBean.setDatabaseType(EmbeddedDatabaseType.H2);
        embeddedDatabaseFactoryBean.afterPropertiesSet();
        return embeddedDatabaseFactoryBean.getObject();
    }

}
