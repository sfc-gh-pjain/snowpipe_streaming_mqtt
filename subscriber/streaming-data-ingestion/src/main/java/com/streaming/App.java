package com.streaming;

import org.eclipse.paho.client.mqttv3.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import net.snowflake.ingest.streaming.InsertValidationResponse;
import net.snowflake.ingest.streaming.OpenChannelRequest;
import net.snowflake.ingest.streaming.SnowflakeStreamingIngestChannel;
import net.snowflake.ingest.streaming.SnowflakeStreamingIngestClient;
import net.snowflake.ingest.streaming.SnowflakeStreamingIngestClientFactory;
import java.sql.*;
import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class App {
    private static String PROFILE_PATH = "snowflake_account_properties.json";
    private static final ObjectMapper mapper = new ObjectMapper();
    public static int totalRowsInTable = 0;
    private static List<Map<String, Object>> messages = new ArrayList<Map<String, Object>>();
    private static ExecutorService executor = Executors.newSingleThreadExecutor();
    private static BlockingQueue<Map<String, Object>> messageQueue = new LinkedBlockingQueue<>();
    private static final int MAX_BATCH_SIZE = Integer.parseInt(System.getenv("max_rows"));
    private static final int FLUSH_SEC = Integer.parseInt(System.getenv("flush_tm"));

    public static void main(String[] args) throws Exception {

        Properties props = new Properties();
        Iterator<Map.Entry<String, JsonNode>> propIt =
            mapper.readTree(new String(Files.readAllBytes(Paths.get(PROFILE_PATH)))).fields();
        while (propIt.hasNext()) {
          Map.Entry<String, JsonNode> prop = propIt.next();
          props.put(prop.getKey(), prop.getValue().asText());
        }
        
        SnowflakeStreamingIngestClient snowclient = 
        SnowflakeStreamingIngestClientFactory
        .builder("CLIENT_FOR_MQLOAD").setProperties(props).build() ;

        // Create an open channel request on table MY_TABLE, note that the corresponding
        OpenChannelRequest request1 =
            OpenChannelRequest.builder("MQ_CHANNEL")
                .setDBName("SNOWPIPE_STREAMING")
                .setSchemaName("PUBLIC")
                .setTableName("STREAMING_MQTT_TO_SNOW_OAUTH")
                .setOnErrorOption(
                    OpenChannelRequest.OnErrorOption.CONTINUE) // Another ON_ERROR option is ABORT
                .build();

        // Open a streaming ingest channel from the given client
        SnowflakeStreamingIngestChannel channel1 = snowclient.openChannel(request1); 
        
        String offsetTokenFromSnowflake = channel1.getLatestCommittedOffsetToken();        
        try {
          totalRowsInTable = Integer.valueOf(offsetTokenFromSnowflake);
        } catch (NumberFormatException | NullPointerException e){
                  System.err.println("Error parsing the string to an integer: " + offsetTokenFromSnowflake);
        }        

        String broker = "tcp://mqtt-broker:1883";
        String topic = "topic2";
        
        Gson gson = new Gson();

        try {
            MqttClient client = new MqttClient(broker, MqttClient.generateClientId());
            client.setCallback(new MqttCallback() {
                public void connectionLost(Throwable throwable) {}
                public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {
                    String payload = new String(mqttMessage.getPayload());
                    //System.out.println("Received message: " + payload);

                    // Add the received message to the message queue                    
                    Map<String, Object> row = gson.fromJson(payload, Map.class);
                    messageQueue.add(row);                    
                    totalRowsInTable++;                      
                }
                public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
                  System.out.println("Received iMqttDeliveryToken: " + iMqttDeliveryToken);

                }
            });
            client.connect();
            client.subscribe(topic);

            // Schedule the task to process messages every 10 seconds
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.scheduleAtFixedRate(new MessageProcessor(channel1), FLUSH_SEC, FLUSH_SEC, TimeUnit.SECONDS);

            // Add a shutdown hook to stop the program gracefully

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {              
                System.out.println("Shutting down the program gracefully...");
                try {
                  client.disconnect();
                  channel1.close().get(); 
                } catch (MqttException | InterruptedException | ExecutionException e) {
                  e.printStackTrace();
                }              
              
            }));

          } catch (MqttException e) {
            e.printStackTrace();
        }
         
    }

    private static class MessageProcessor implements Runnable {
      SnowflakeStreamingIngestChannel channel1;

      public MessageProcessor(SnowflakeStreamingIngestChannel channel1) {
          this.channel1 = channel1;
      }

      public void run() {
          processMessages(channel1);
      }
    }

    private static void processMessages(SnowflakeStreamingIngestChannel channelN) {         
      try {
          
          messageQueue.drainTo(messages, MAX_BATCH_SIZE);            
          System.out.println("Total messages receieved in this run: " + messages.size());

          // Insert rows into the channel (Using insertRows API)
          // Insert the row with the current offset_token
          if(!messages.isEmpty()) {
              System.out.println("Processing messages:");
              InsertValidationResponse response = channelN.insertRows(messages, String.valueOf(totalRowsInTable));
              if (response.hasErrors()) {
              // Simply throw if there is an exception, or you can do whatever you want with the
              // erroneous row
              throw response.getInsertErrors().get(0).getException();
              }
              messages.clear();
                          
          }
      } catch (Exception e) {
              // Catch any other exceptions here
              System.out.println("Some other exception occurred: " + e.getMessage());
              e.printStackTrace();
              // Continue program execution or perform recovery actions
              System.out.println("Continuing the program after the exception...");
        }
    }
    
}
