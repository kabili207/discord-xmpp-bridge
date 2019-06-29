package com.zyrenth.xmpp.discordbridge;

import org.apache.commons.daemon.Daemon;
import org.apache.commons.daemon.DaemonContext;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.jivesoftware.whack.ExternalComponentManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.component.ComponentException;

import java.util.Properties;

/**
 * Created by kabili on 1/28/16.
 */
public class DaemonApp implements Daemon {

    private static final Logger logger = LoggerFactory.getLogger(DaemonApp.class);
    private static Properties prop = new Properties();
    private static boolean running = true;

    private ExternalComponentManager mgr;
    private BaseComponent mComponent;
    private String componentName;

    private DiscordApi discord;

    public static void main(String[] args) throws Exception {

        final DaemonApp app = new DaemonApp();


        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                running = false;
                try {
                    app.stop();
                } catch (Exception e) {
                    logger.error("Error running shutdown hook", e);
                }
            }
        });

        app.start();

        //Keep it alive
        while (running) {
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
                logger.error("main", e);
            }
        }
    }

    public void init(DaemonContext context) throws Exception {
        //logger.fine(String.format("Daemon initialized with arguments {}.", context.getArguments()));
        logger.debug("Daemon init");
    }

    public void start() throws Exception {
        logger.debug("Starting up daemon.");

        startComponent();
    }

    public void stop() throws Exception {
        logger.debug("Stopping daemon.");

        discord.disconnect();
        mComponent.shutdown();
        if (mgr != null)
            mgr.removeComponent(componentName);
        running = false;
    }

    public void destroy() {
        logger.debug("Destroying daemon.");
    }

    public void startComponent() throws Exception {
        prop.load(DaemonApp.class.getClassLoader().getResourceAsStream("application.properties"));

        final String serverName = prop.getProperty("serverName");
        componentName = prop.getProperty("componentName");
        final String componentSecret = prop.getProperty("componentSecret");
        final String token = prop.getProperty("discordToken");

        logger.info("Connecting to {} as {}", serverName, componentName);

        mgr = new ExternalComponentManager(serverName, 5347, false);

        mgr.setSecretKey(componentName, componentSecret);
        try {
            discord = new DiscordApiBuilder().setToken(token).login().join();
            mComponent = new BridgeComponent(discord);
            mgr.addComponent(componentName, mComponent);
            discord.addListener(mComponent);
            logger.info("Discord invite URL: " + discord.createBotInvite());
        } catch (ComponentException e) {
            logger.error("start", e);
            System.exit(-1);
        }
    }
}
