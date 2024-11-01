# Steps to run this MQTT streaming demo

### You should have Docker container services installed for this demo to work. The steps for installing ans working
 with Docker are beyond the scope of this demo, but are very easy for both windows and Macbook and can easily
 found on the internet.

## Steps to Run this demo.

1. Go to your Snowflake account, open a worksheet and paste this SQL.

```
CREATE DATABASE IF NOT EXISTS SNOWPIPE_STREAMING;
use schema SNOWPIPE_STREAMING.PUBLIC;

drop table SNOWPIPE_STREAMING.PUBLIC.STREAMING_MQTT_TO_SNOW;

create or replace TABLE SNOWPIPE_STREAMING.PUBLIC.STREAMING_MQTT_TO_SNOW (
	customer_name varchar(100),
    customer_id number(10),
    purchases variant
);

```

2. Go to the parent folder and open the properties file for connecting to Snowflake
```
<root folder>/subscriber/streaming-data-ingestion/snowflake_account_properties.json.example
```

update your credentials and account details, save this file in the same folder as 
```
<root folder>/subscriber/streaming-data-ingestion/snowflake_account_properties.json
```

for more information on the PEM key look at https://docs.snowflake.com/en/user-guide/key-pair-auth#configuring-key-pair-authentication

3. open a terminal and type this command 
    ```
    docker compose up
    ```

4. It will take a while for the first time and will start the containers required for the Snowpipe Streaming demo.

5. You can check the status of the streaming data by using these SQL commands in a Snowflake Worksheet

```
-- Note that the ENABLE_SCHEMA_EVOLUTION property can also be set at table creation with CREATE OR REPLACE TABLE
ALTER TABLE SNOWPIPE_STREAMING.PUBLIC.STREAMING_MQTT_TO_SNOW SET ENABLE_SCHEMA_EVOLUTION = TRUE;

select * from SNOWPIPE_STREAMING.PUBLIC.STREAMING_MQTT_TO_SNOW limit 10;

select count(*) from SNOWPIPE_STREAMING.PUBLIC.STREAMING_MQTT_TO_SNOW;

show channels;

-- Simple JSON flattening and transformation

select 
    customer_name,
    customer_id, 
    t2.value:"product_name"::varchar(1000) as product_name,
    t2.value:"purchase_amount"::number(20,2) as amount,
    t2.value:"purchase_date"::date as DateOf_Purchase
from 
    STREAMING_MQTT_TO_SNOW t1 ,
    lateral flatten(input => t1.purchases) t2
    
limit 10;

```