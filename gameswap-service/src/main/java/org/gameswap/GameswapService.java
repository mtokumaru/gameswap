package org.gameswap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.dropwizard.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.auth.basic.BasicCredentialAuthFilter;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.hibernate.HibernateBundle;
import io.dropwizard.jetty.MutableServletContextHandler;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.eclipse.jetty.servlet.FilterHolder;
import org.gameswap.auth.AuthFilter;
import org.gameswap.config.GameswapConfiguration;
import org.gameswap.daos.UserDAO;
import org.gameswap.models.User;
import org.gameswap.models.UserPrincipal;
import org.gameswap.resources.AuthResource;
import org.gameswap.resources.TestResource;
import org.gameswap.resources.UserResource;
import org.gameswap.security.SimpleAuthenticator;

import java.io.IOException;
import java.util.EnumSet;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.client.Client;

public class GameswapService extends Application<GameswapConfiguration> {

    public static void main(String[] args) throws Exception {
        new GameswapService().run(args);
    }

    private final HibernateBundle<GameswapConfiguration> hibernateBundle = new HibernateBundle<GameswapConfiguration>(User.class, Void.class) {

        @Override
        public DataSourceFactory getDataSourceFactory(GameswapConfiguration configuration) {
            return configuration.getDataSourceFactory();
        }
    };


    @Override
    public String getName() {
        return "gameswap";
    }


    @Override
    public void initialize(Bootstrap<GameswapConfiguration> bootstrap) {
        enableEnvironmentConfiguration(bootstrap);
        bootstrap.addBundle(new AssetsBundle("/assets/app/", "/", "index.html"));
        bootstrap.addBundle(hibernateBundle);
        ObjectMapper mapper = bootstrap.getObjectMapper();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }


    @Override
    public void run(GameswapConfiguration configuration, Environment environment) throws Exception {
        if (configuration.isRedirectAllToHttps()) {
            addHttpsForward(environment.getApplicationContext());
        }
        addAuthFilter(environment);
        environment.jersey().setUrlPattern("/gameswap/*");
        final Client client = new JerseyClientBuilder(environment).using(configuration.getJerseyClient()).build(getName());
        UserDAO dao = new UserDAO(hibernateBundle.getSessionFactory());

        registerResources(configuration, environment, client, dao);
        environment.jersey().register(new BasicCredentialAuthFilter.Builder<UserPrincipal>().setAuthenticator(new SimpleAuthenticator(dao)));
    }


    private void registerResources(GameswapConfiguration configuration, Environment environment, Client client, UserDAO dao) {
        environment.jersey().register(new UserResource(dao));
        environment.jersey().register(new TestResource());
        environment.jersey().register(new AuthResource(client, dao, configuration));
    }


    private void enableEnvironmentConfiguration(Bootstrap<GameswapConfiguration> bootstrap) {
        bootstrap.setConfigurationSourceProvider(
            new SubstitutingSourceProvider(bootstrap.getConfigurationSourceProvider(), new EnvironmentVariableSubstitutor(false)));
    }


    private void addAuthFilter(Environment environment) {
        environment.servlets().addFilter("AuthFilter", new AuthFilter()).addMappingForUrlPatterns(null, true, "/gameswap/v1/*");

    }


    private void addHttpsForward(MutableServletContextHandler handler) {
        handler.addFilter(new FilterHolder(new Filter() {

            public void init(FilterConfig filterConfig) throws ServletException {
            }


            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
                StringBuffer uri = ((HttpServletRequest) request).getRequestURL();
                if (uri.toString().startsWith("http://")) {
                    String location = "https://" + uri.substring("http://".length());
                    ((HttpServletResponse) response).sendRedirect(location);
                }
                else {
                    chain.doFilter(request, response);
                }
            }


            public void destroy() {
            }
        }), "/*", EnumSet.allOf(DispatcherType.class));
    }
}
