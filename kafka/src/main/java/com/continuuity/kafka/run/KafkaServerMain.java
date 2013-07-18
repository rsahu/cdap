package com.continuuity.kafka.run;

import com.continuuity.common.conf.CConfiguration;
import com.continuuity.common.conf.Constants;
import com.continuuity.common.runtime.DaemonMain;
import com.continuuity.weave.internal.kafka.EmbeddedKafkaServer;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Properties;

/**
 * Runs embedded Kafka server.
 */
public class KafkaServerMain extends DaemonMain {
  private static final Logger LOG = LoggerFactory.getLogger(KafkaServerMain.class);

  private static final String KAFKA_PORT_CONFIG = "kafka.port";
  private static final String KAFKA_NUM_PARTITIONS_CONFIG = "kafka.num.partitions";
  private static final String KAFKA_LOG_DIR_CONFIG = "kafka.log.dir";

  private static final String ZOOKEEPER_NAMESPACE = "continuuity.kafka";

  private Properties kafkaProperties;
  private EmbeddedKafkaServer kafkaServer;

  public static void main(String [] args) throws Exception {
    new KafkaServerMain().doMain(args);
  }

  @Override
  public void init(String[] args) {
    LOG.info(String.format("Got args - %s", Arrays.toString(args)));

    if (args.length != 1) {
      String name = KafkaServerMain.class.getSimpleName();
      throw new IllegalArgumentException(String.format("Usage: %s <brokerId>", name));
    }

    int brokerId = Integer.parseInt(args[0]);

    CConfiguration cConf = CConfiguration.create();
    String zkConnectStr = String.format("%s/%s", cConf.get(Constants.CFG_ZOOKEEPER_ENSEMBLE), ZOOKEEPER_NAMESPACE);
    int port = cConf.getInt(KAFKA_PORT_CONFIG, -1);
    int numPartitions = cConf.getInt(KAFKA_NUM_PARTITIONS_CONFIG, 1);
    String logDir = cConf.get(KAFKA_LOG_DIR_CONFIG);

    kafkaProperties = generateKafkaConfig(brokerId, zkConnectStr, port, numPartitions, logDir);
  }

  @Override
  public void start() {
    LOG.info("Starting embedded kafka server...");

    kafkaServer = new EmbeddedKafkaServer(KafkaServerMain.class.getClassLoader(), kafkaProperties);
    kafkaServer.startAndWait();
    if (!kafkaServer.isRunning()) {
      throw new IllegalStateException("Cannot start Kafka Server");
    }

    LOG.info("Embedded kafka server started successfully.");
  }

  @Override
  public void stop() {
    LOG.info("Stopping embedded kafka server...");
    if (kafkaServer != null) {
      kafkaServer.stopAndWait();
    }
  }

  @Override
  public void destroy() {
    // Nothing to do
  }

  private Properties generateKafkaConfig(int brokerId, String zkConnectStr, int port, int numPartitions,
                                         String logDir) {
    Preconditions.checkState(port > 0, "Failed to get random port.");
    Preconditions.checkState(numPartitions > 0, "Num partitions should be greater than zero.");

    Properties prop = new Properties();
    prop.setProperty("broker.id", Integer.toString(brokerId));
    prop.setProperty("port", Integer.toString(port));
    prop.setProperty("socket.send.buffer.bytes", "1048576");
    prop.setProperty("socket.receive.buffer.bytes", "1048576");
    prop.setProperty("socket.request.max.bytes", "104857600");
    prop.setProperty("log.dir", logDir);
    prop.setProperty("num.partitions", Integer.toString(numPartitions));
    prop.setProperty("log.flush.interval.messages", "10000");
    prop.setProperty("log.flush.interval.ms", "1000");
    prop.setProperty("log.segment.bytes", "536870912");
    prop.setProperty("zookeeper.connect", zkConnectStr);
    prop.setProperty("zookeeper.connection.timeout.ms", "1000000");
    return prop;
  }
}
