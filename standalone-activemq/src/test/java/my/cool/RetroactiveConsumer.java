package my.cool;

import junit.framework.TestCase;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.region.policy.*;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.store.PersistenceAdapter;
import org.apache.activemq.store.kahadb.KahaDBPersistenceAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.*;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

public class RetroactiveConsumer extends TestCase {

    private static final Logger log = LoggerFactory.getLogger(RetroactiveConsumer.class);

    private BrokerService broker;
    private final static String BROKER_URI = "tcp://localhost:62626";
/*    private final static String URI = "vm://durable-broker";*/
    
    private final static String ACTIVEMQ_BROKER_URI = BROKER_URI;

    private final String ACTIVEMQ_BROKER_BIND = BROKER_URI;

    Connection connection;

    @Override
    protected void setUp() throws Exception {
        createBroker();
        connection = getConnection();
    }

    @Override
    protected void tearDown() throws Exception {
        broker.stop();
    }

    protected ActiveMQConnectionFactory createConnectionFactory() throws Exception {
        return new ActiveMQConnectionFactory(BROKER_URI);
    }
    
/*    protected void restartBroker() throws Exception {
        if (connection != null) {
            connection.close();
        }
        if (broker != null) {
            broker.stop();
            broker.waitUntilStopped();
        }
        createRestartedBroker();
    }*/

    private void createBroker() throws Exception {
        PolicyEntry policy = new PolicyEntry();
        policy.setDispatchPolicy(new SimpleDispatchPolicy());
        policy.setSubscriptionRecoveryPolicy(new NoSubscriptionRecoveryPolicy());
        /* new FixedCountSubscriptionRecoveryPolicy() */
        PolicyMap pMap = new PolicyMap();
        pMap.setDefaultEntry(policy);
        
        broker = new BrokerService();
        broker.setBrokerName("durable-broker");
        broker.setDeleteAllMessagesOnStartup(true);
        broker.setPersistenceAdapter(createPersistenceAdapter());
        broker.setPersistent(true);

        broker.addConnector(ACTIVEMQ_BROKER_BIND);
        broker.start();
        broker.waitUntilStarted();
    }

/*    private void createRestartedBroker() throws Exception {
        PolicyEntry policy = new PolicyEntry();
        policy.setDispatchPolicy(new SimpleDispatchPolicy());
        policy.setSubscriptionRecoveryPolicy(new FixedCountSubscriptionRecoveryPolicy());
        PolicyMap pMap = new PolicyMap();
        pMap.setDefaultEntry(policy);
        
        broker = new BrokerService();
        broker.setBrokerName("durable-broker");
        broker.setDeleteAllMessagesOnStartup(false);
        broker.setPersistenceAdapter(createPersistenceAdapter());
        broker.setPersistent(true);
        broker.start();
        broker.waitUntilStarted();
    }*/

    protected PersistenceAdapter createPersistenceAdapter() throws Exception {
        KahaDBPersistenceAdapter adapter = new KahaDBPersistenceAdapter();
        adapter.setConcurrentStoreAndDispatchQueues(false);
        adapter.setConcurrentStoreAndDispatchTopics(false);
        adapter.deleteAllMessages();
        return adapter;
    }
    
    private Connection getConnection() throws Exception {
        Connection connection = createConnectionFactory().createConnection();
        connection.setClientID("cliId1");
        return connection;
    }

    public void testFixedRecoveryPolicy() throws Exception {
        
        connection.start();

        // Create the durable sub.
        Session session = connection.createSession(false, javax.jms.Session.AUTO_ACKNOWLEDGE);

        // Ensure that consumer will receive messages sent before it was created
        Topic topic = session.createTopic("TestTopic?consumer.retroactive=true");
        TopicSubscriber sub1 = session.createDurableSubscriber(topic, "sub1");

        // Produce a message
        MessageProducer producer = session.createProducer(topic);
        producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

        // Make sure it works when the durable sub is active.
        producer.send(session.createTextMessage("Msg:1"));

        // Close session/connection
        session.close();
        connection.close();
        
        connection = getConnection();
        connection.start();
        session = connection.createSession(false, javax.jms.Session.AUTO_ACKNOWLEDGE);
        producer = session.createProducer(topic);
        producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
        producer.send(session.createTextMessage("Msg:2"));

        // Recreate the subscriber to check if it will be able to recover the messages
        sub1 = session.createDurableSubscriber(topic, "sub1");

        // Try to get the message.
        assertTextMessageEquals("Msg:1", sub1.receive(5000));
        assertTextMessageEquals("Msg:2", sub1.receive(5000));
    }

    private void assertTextMessageEquals(String string, Message message) throws JMSException {
        assertNotNull("Message was null", message);
        assertTrue("Message is not a TextMessage", message instanceof TextMessage);
        assertEquals(string, ((TextMessage) message).getText());
    }
}