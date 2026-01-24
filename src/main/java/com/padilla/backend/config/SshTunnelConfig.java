package com.padilla.backend.config;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "ssh.tunnel.enabled", havingValue = "true")
public class SshTunnelConfig {

    private static final Logger logger = LoggerFactory.getLogger(SshTunnelConfig.class);

    @Value("${ssh.tunnel.host}")
    private String sshHost;

    @Value("${ssh.tunnel.port}")
    private int sshPort;

    @Value("${ssh.tunnel.user}")
    private String sshUser;

    @Value("${ssh.tunnel.password}")
    private String sshPassword;

    @Value("${ssh.tunnel.local-port}")
    private int localPort;

    @Value("${ssh.tunnel.remote-host}")
    private String remoteHost;

    @Value("${ssh.tunnel.remote-port}")
    private int remotePort;

    private Session session;

    @Bean(name = "sshTunnelInitializer")
    public Object sshTunnelInitializer() {
        try {
            logger.info("Iniciando tunel SSH hacia {}:{}", sshHost, sshPort);

            JSch jsch = new JSch();
            session = jsch.getSession(sshUser, sshHost, sshPort);
            session.setPassword(sshPassword);

            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);

            session.connect(30000);

            int assignedPort = session.setPortForwardingL(localPort, remoteHost, remotePort);

            logger.info("Tunel SSH establecido: localhost:{} -> {}:{}", assignedPort, remoteHost, remotePort);

            return new Object(); // Dummy object for bean dependency

        } catch (Exception e) {
            logger.error("Error al establecer tunel SSH: {}", e.getMessage());
            throw new RuntimeException("No se pudo establecer el tunel SSH", e);
        }
    }

    @PreDestroy
    public void destroy() {
        if (session != null && session.isConnected()) {
            logger.info("Cerrando tunel SSH");
            session.disconnect();
        }
    }
}
