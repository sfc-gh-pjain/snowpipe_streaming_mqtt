import paho.mqtt.client as mqtt
import json
from faker import Faker
import random
import sys
import os
from datetime import datetime, timedelta


fake = Faker()


def show_usage():
    print("Usage for python kafka producer script:")
    print("stream-producer.py Int1 Int2")
    print("Arguments:")
    print("Int1: Number of JSON records to produce")
    print("arg2: Number of customers to produce records for")
    print("Example:")
    print("stream-producer.py 1000 100")
    exit(1)

if len(sys.argv) >=3:

    if int(sys.argv[1]) >= 1000:
        nrecords = int(sys.argv[1])
    else:
        print("Numbers of records default to minimum 1000")
        nrecords = 1000

    if int(sys.argv[2]) >= 100:
        ncust = int(sys.argv[2])
    else:
        print("Numbers of customers default to minimum 100")
        ncust = 100
else:
    show_usage()

# Generate a list of customers
customers = []
customer_id = 1001 # Starting customer ID


# We will produce a list of purchase for same customers for data sanity
for _ in range(ncust):  # Generate n customers, you can adjust this number as needed
    customer = {
        'cust_id': customer_id,
        'name': fake.name()
    }
    customers.append(customer)
    customer_id += 1 # Increment the customer ID


# Generate fake customer purchase data
def generate_customer_purchase():
    c_id = fake.random_int(min=0, max=(ncust-1))
    #print(c_id)
    customer_purchase = {
        'customer_id': customers[c_id]['cust_id'],
        'customer_name': customers[c_id]['name'],
        'purchases': []
    }
    num_purchases = fake.random_int(min=1, max=5)
    for _ in range(num_purchases):
        purchase = {
            'product_name': fake.name() + " " + fake.word(),
            'purchase_amount': round(random.uniform(10, 1000),2),
            'purchase_date': fake.date_between(start_date='-1y', end_date='today').strftime('%Y-%m-%d')
        }
        customer_purchase['purchases'].append(purchase)
    
    return customer_purchase

############ Producer Code #######
def on_connect(client, userdata, flags, rc):
    print("Connected with result code " + str(rc))


def run_producer(num_records):
    broker_address = "mqtt-broker"
    port = 1883
    topic = "topic2"

    print("Start Time of Process: " + str(datetime.now()))

    client = mqtt.Client()

    client.on_connect = on_connect
    client.connect(broker_address, port)

    for _ in range(num_records):
        data = generate_customer_purchase()
        record = json.dumps(data)
        client.publish(topic, record)

    client.disconnect()

       

try:
    run_producer(nrecords)

    print (f"Producer ran successfully and sent {nrecords} to MQ")
    print("End Time of Process: " + str(datetime.now()))
except Exception as e:
    # Exception handling for other exceptions
    print("An error occurred:", str(e))

