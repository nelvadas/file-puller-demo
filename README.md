# file-puller-demo: Demonstrates clustered file puller in Fuse 6.3.X with infinispan idempotent repository


Level: Intermediate  
Technologies: Apache Camel 2.17, Blueprint, AMQ 7 , JBoss Datagrid, Fuse 6.X
Summary: This tutorial demonstrates how to use Apache Camel, JBoss Datagrid to handle clustered file processing
in a Fuse 6.X Fabric.



# Table of contents
1. [Introduction](#introduction)
2. [System requirements and installations](#appsetup)
    1. [Red Hat Fuse 6.3.X Fabric cluster](#fabric)
    2. [Red Hat AMQ 7.x  Broker](#amq7)
    3. [Red Hat Datagrid 7.x  ](#datagrid)
       1. [Installation](#datagridinstall)
       2. [Setup Cache configuration   ](#datagridconfig1)
       3. [Setup Cache Instances   ](#datagridconfig2)
3. [Camel Routes](#coding)
4. [Deploying the bundle ](#deployment)
5. [Running the application ](#tests)



# Introduction  <a name="introduction"></a>
This tutorial simulated an environment where customer orders are handled.
Orders are received as csv file in a shared folder.
concurrent Apache Camel processes deployed in Fuse 6.X fabric will pull files as soon as their arrive in the share folder.
each file should be handled once.


![clusterfilepuller ](https://github.com/nelvadas/file-puller-demo/blob/master/filepullercluster.png "filepullerdemo ")


# System requirements and installations <a name="appsetup"></a>
Before building and running this quick start you need:

* Maven 3.x or higher
* JDK 1.8
* Red Hat Fuse 6.3.X Fabric Cluster
* Red Hat AMQ 7.2.0  Broker
* Red Hat Datagrid 7.2.

To run the following tutorial you will have to set up the following
## Red Hat Fuse 6.3.X Fabric cluster <a name="fabric"></a>

file puller component OSGI component will be deployed in the fabric cluster
Create a simple fabric with the following instructions.

```
$ unzip ~/Downloads/jboss-fuse-karaf-6.3.0.redhat-347.zip
$ cd jboss-fuse-6.3.0.redhat-347/
$ sed -i.bak "s/#admin/admin/g" etc/users.properties

```

Create a fabric cluster with two child containers

```
$ ./bin/fuse
...
JBossFuse:karaf@root> fabric:create --resolver manualip --manual-ip 127.0.0.1 --wait-for-provisioning --force

JBossFuse:karaf@root> fabric:info
Fabric Release:                1.2.0.redhat-630347
Web Console:                   http://127.0.0.1:8181/hawtio
Rest API:
Git URL:                       http://127.0.0.1:8181/git/fabric/
Jolokia URL:                   http://127.0.0.1:8181/jolokia
ZooKeeper URI:                 127.0.0.1:2181
Maven Download URI:            http://127.0.0.1:8181/maven/download/
Maven Upload URI:              http://127.0.0.1:8181/maven/upload/

JBossFuse:karaf@root> container-create-child root cnt-file-puller 2
The following containers have been created successfully:
	Container: cnt-file-puller2.
	Container: cnt-file-puller1.
JBossFuse:karaf@root>
```

## Red Hat AMQ 7.x  Broker <a name="amq7"></a>
AMQ7 will be use to store command data once read by file processor.
To scale the ordrer processing, as soon as the file puller component read the order files,
the file content is sent to an INPUT_QUEUE on the AMQ7 Broker.
Various consummer can then connect to this queue to complete the order processing scenario.
To avoid some order numbers to be handled during a certain period,
we will set up  on the IdempotentConsummer process with Infinispan to keep track on orders item already handled.


Install an AMQ7.x and start one instance

```
$ unzip ~/Downloads/amq-broker-7.2.0-bin.zip
$ cd amq-broker-7.2.0
$./bin/artemis create ../../brokers/amq7-node1 --name amq7-node1 --user admin --password admin --allow-anonymous --port-offset 0
$ cd ../../brokers/amq7-node1/bin
$ ./artemis run

...
2018-07-25 08:38:02,116 INFO  [org.apache.activemq.artemis.core.server] AMQ221020: Started NIO Acceptor at 127.0.0.1:61616 for protocols [CORE,MQTT,AMQP,STOMP,HORNETQ,OPENWIRE]
2018-07-25 08:38:02,118 INFO  [org...] AMQ221020: Started NIO Acceptor at 127.0.0.1:5445 for protocols [HORNETQ,STOMP]
2018-07-25 08:38:02,121 INFO  [org...] AMQ221020: Started NIO Acceptor at 127.0.0.1:5672 for protocols [AMQP]
2018-07-25 08:38:02,124 INFO  [org...] AMQ221020: Started NIO Acceptor at 127.0.0.1:1883 for protocols [MQTT]
2018-07-25 08:38:02,126 INFO  [org...] AMQ221020: Started NIO Acceptor at 127.0.0.1:61613 for protocols [STOMP]
..
2018-07-25 08:38:04,094 INFO  [org...] AMQ241004: Artemis Console available at http://localhost:8161/console

```


## Red Hat Datagrid 7.x <a name="datagrid"></a>
In a production environment, we will setup a Replicated Datagrid cluster with at least two nodes,
for the simplicity of this tutorial we will show the operation you have to complete for each node of your cluster.
repeat the process to have a second node up and running.


### Install <a name="datagridinstall"></a>
```
$ unzip ~/Downloads/jboss-datagrid-7.2.0-server.zip
cd  jboss-datagrid-7.2.0-server
```



Replicate the configuration file instance folder to create a Node.

```
$ mkdir -p ../instances/node1
$ cp -r standalone/ ../instances/node1/
```

Create a Management User and and Application user
```
$ ./bin/add-user.sh -u admin -p Admin01#   -sc  ../../instances/node1/configuration
$ ./bin/add-user.sh -a -u appli01 -p appli01 -sc  ../../instances/node1/configuration
```

Start the Datagrid instance
```
$ ./bin/standalone.sh -c=clustered.xml \
    -bmanagement=127.0.0.1 -b=127.0.0.1 \
    -Djboss.node.name=Node1 \
    -Djboss.server.base.dir=../instances/node1 \
    -Djboss.socket.binding.port-offset=100    

```

The Node1 will expose the following interfaces
* MemcachedServer listening on 127.0.0.1:11311
* HotRodServer listening on 127.0.0.1:11322
* Admin console listening on http://127.0.0.1:10090


### Setup Cache Configuration  <a name="datagridconfig1"></a>
Connect to Node1 Admin CLI interface,

```
$ ./bin/cli.sh --connect --controller=127.0.0.1:10090 -u=admin -p=Admin01#
[standalone@127.0.0.1:10090 /]
```

then create two replicated caches  CACHE_FP_01 and CACHE_FP_02

=> We will not handle the same file before 5 min
CACHE_FP_01 should expire items after 5 minutes (300000ms)

=> We will not process the same order number before 10 minutes
CACHE_FP_01 should expire items after 10 minutes(600000ms)

We will create two different cache configuration

```
[standalone@127.0.0.1:10090 /] /subsystem=datagrid-infinispan/cache-container=clustered/configurations=CONFIGURATIONS/replicated-cache-configuration=CONFIG_FP_01:add(template=false,start=EAGER,mode=SYNC)
{"outcome" => "success"}

[standalone@127.0.0.1:10090 /] /subsystem=datagrid-infinispan/cache-container=clustered/configurations=CONFIGURATIONS/replicated-cache-configuration=CONFIG_FP_02:add(template=false,start=EAGER,mode=SYNC)
{"outcome" => "success"}

```

Define expiration delay for each cache configurations

```

[standalone@127.0.0.1:10090 /] /subsystem=datagrid-infinispan/cache-container=clustered/configurations=CONFIGURATIONS/replicated-cache-configuration=CONFIG_FP_01/expiration==EXPIRATION:add(lifespan=300000)
{"outcome" => "success"}

[standalone@127.0.0.1:10090 /] /subsystem=datagrid-infinispan/cache-container=clustered/configurations=CONFIGURATIONS/replicated-cache-configuration=CONFIG_FP_02/expiration==EXPIRATION:add(lifespan=600000)
{"outcome" => "success"}

```

### Setup Cache Instances  <a name="datagridconfig2"></a>

Create caches instances with the following commands

```
[standalone@127.0.0.1:10090 /] /subsystem=datagrid-infinispan/cache-container=clustered/replicated-cache=CACHE_FP_01:add(configuration=CONFIG_FP_01)
{"outcome" => "success"}

[standalone@127.0.0.1:10090 /] /subsystem=datagrid-infinispan/cache-container=clustered/replicated-cache=CACHE_FP_02:add(configuration=CONFIG_FP_02)
{"outcome" => "success"}

```

Now we are ready to code and deploy our file puller application


## Camel Routes  <a name="coding"></a>


The camel route to read the files should rely on an idempotent repository
```
 <from iuri="file:{{order.files.path.source}}?readLock=idempotent&amp;\
readLockLoggingLevel=WARN&amp;\
idempotentRepository=#infinispanFileRepo"/>
```

Add  `src/main/fabric8/com.redhat.training.filepuller.cfg.properties` file
to load application properties.


```
context.name.environment=local
context.name.application=file-puller-demo

activemq.name.queue.input=QUEUE.ORDER.INPUT
activemq.name.queue.output=QUEUE.ORDER.OUTPUT

#brokerURL=tcp://localhost:61616
#brokerURL=failover:(amqp://localhost:61617,amqp://localhost:61618)
brokerURL=failover:(amqp://127.0.0.1:61616,amqp://127.0.0.1:61617,amqp://127.0.0.1:61618)
userName=admin
password=admin

#Infinispan cluster
infinispan.hotrod.cluster=127.0.0.1:11322;127.0.0.1:11422

infinispan.file.cacheName=CACHE_FP_01
infinispan.orders.cacheName=CACHE_FP_02


order.files.path.source=/opt/fuse1t/data/orders
```



## Deploying the Bundle   <a name="deployment"></a>


To deploy the bundle in Fuse Fabric:

1. Enter `mvn io.fabric8:fabric8-maven-plugin:deploy` command to deploy the bundle on your fabric cluster
```
$file-puller-demo enonowog$ mvn io.fabric8:fabric8-maven-plugin:deploy
..

O] Uploading file README.md to invoke mbean io.fabric8:type=Fabric on jolokia URL: http://localhost:8181/jolokia with user: admin
[INFO] Uploading file com.redhat.training.filepuller.cfg.properties to invoke mbean io.fabric8:type=Fabric on jolokia URL: http://localhost:8181/jolokia with user: admin
[INFO] Uploading file data/order1.csv to invoke mbean io.fabric8:type=Fabric on jolokia URL: http://localhost:8181/jolokia with user: admin
[INFO] Performing profile refresh on mbean: io.fabric8:type=Fabric version: 1.0 profile: com-redhat-training.filepuller-demo
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time: 14.745 s
[INFO] Finished at: 2018-07-25T10:41:42+02:00
[INFO] Final Memory: 52M/597M
[INFO] ------------------------------------------------------------------------
MacBook-Pro-de-elvadas:file-puller-demo enonowog$
```
The profile is deployed in the fabric

```
JBossFuse:karaf@root> profile-list | grep com
com-redhat-training.filepuller-demo                  feature-camel
JBossFuse:karaf@root>
```


2. Add the profile to a set of containers

```
JBossFuse:karaf@root> container-add-profile cnt-file-puller1 com-redhat-training.filepuller-demo
JBossFuse:karaf@root> container-add-profile cnt-file-puller2 com-redhat-training.filepuller-demo

JBossFuse:karaf@root> container-list
[id]                [version]  [type]  [connected]  [profiles]                           [provision status]
root*               1.0        karaf   yes          fabric                               success
                                                    fabric-ensemble-0000-1
                                                    jboss-fuse-full
  cnt-file-puller1  1.0        karaf   yes          default                              success
                                                    com-redhat-training.filepuller-demo
  cnt-file-puller2  1.0        karaf   yes          default                              success
                                                    com-redhat-training.filepuller-demo

```



## Running the application  <a name="tests"></a>

To use the application be sure to have deployed it in Fuse fabric as described above.

1. As soon as the Camel route has been started, it will start to consume files from  `/opt/fuse1t/data/orders`
you can change this folder to match your installtion by editing the profile  com-redhat-training.filepuller-demo

```
JBossFuse:karaf@root> profile-edit --pid com.redhat.training.filepuller.cfg/order.files.path.source="/tmp/orders" com-redhat-training.filepuller-demo
Setting value:/tmp/orders key:order.files.path.source on pid:com.redhat.training.filepuller.cfg and profile:com-redhat-training.filepuller-demo version:1.0
```

2. Copy the files you find in this quick start's `src/main/fabric8/data` directory to the input folder  `/opt/fuse1t/data/orders` directory.
```
$ cd  src/main/fabric8/data
$ cp  `order1.csv`  `/opt/fuse1t/data/orders/`
```


The file is received by only one container  the containers
```
0347 | INFO: File Received  - /opt/fuse1t/data/orders/order1.csv   last Modified time - 20180725-111031
2018-07-25 11:10:31,248 | INFO  | se1t/data/orders | SplitOrderFileRoute              | 159 - org.apache.camel.camel-core - 2.17.0.redhat-630347 | File received with content
 ORDER01,29/06/2018,80.00,100.00,CUST01,Walter,Smite,01/01/1975,3 rue roy Tanguy,Montreuil,93100,FRANCE
 ORDER02,30/06/2018,29.00,35.45,CUST01,Walter,Smite,01/01/1975,3 rue roy Tanguy,Montreuil,93100,FRANCE
```

Check the caches content

the CACHE_FP_01 contains 1 item ( 1 file processed with key=`order1.csv`)
the processors will not be able to handle a file with the same name within the define expiration period ( 5min)

```
[standalone@127.0.0.1:10090 /] /subsystem=datagrid-infinispan/cache-container=clustered/replicated-cache=CACHE_FP_01:read-attribute(name=number-of-entries)
{
    "outcome" => "success",
    "result" => 1
}

[standalone@127.0.0.1:10090 /] /subsystem=datagrid-infinispan/cache-container=clustered/replicated-cache=CACHE_FP_02:read-attribute(name=number-of-entries)
{
    "outcome" => "success",
    "result" => 2
}

```

At the other side the second cache has two entries (order1.csv-ORDER01, order1.csv-ORDER02 )
a processor is used to generate an idempotent key for each order line in the file.
** This step is not mandatory if you chose to store the whole file content in the queue insted of storing on message per order line **

```
018-07-25 11:10:31,254 | INFO  | se1t/data/orders | SplitOrderFileRoute              | 159 - org.apache.camel.camel-core - 2.17.0.redhat-630347 |
 Order Item before
= ORDER01,29/06/2018,80.00,100.00,CUST01,Walter,Smite,01/01/1975,3 rue roy Tanguy,Montreuil,93100,FRANCE  ... idempotentKey=
2018-07-25 11:10:31,254 | INFO  | se1t/data/orders | SplitOrderFileRoute              | 159 - org.apache.camel.camel-core - 2.17.0.redhat-630347 |
 Order Item after processor  ORDER01,29/06/2018,80.00,100.00,CUST01,Walter,Smite,01/01/1975,3 rue roy Tanguy,Montreuil,93100,FRANCE  ... idempotentKey=order1.csv-ORDER01
2018-07-25 11:10:31,310 | INFO  | se1t/data/orders | SplitOrderFileRoute              | 159 - org.apache.camel.camel-core - 2.17.0.redhat-630347 |

2018-07-25 11:10:31,311 | INFO  | se1t/data/orders | SplitOrderFileRoute              | 159 - org.apache.camel.camel-core - 2.17.0.redhat-630347 |



 Order Item before
= ORDER02,30/06/2018,29.00,35.45,CUST01,Walter,Smite,01/01/1975,3 rue roy Tanguy,Montreuil,93100,FRANCE  ... idempotentKey=
2018-07-25 11:10:31,311 | INFO  | se1t/data/orders | SplitOrderFileRoute              | 159 - org.apache.camel.camel-core - 2.17.0.redhat-630347 |
 Order Item after processor  ORDER02,30/06/2018,29.00,35.45,CUST01,Walter,Smite,01/01/1975,3 rue roy Tanguy,Montreuil,93100,FRANCE  ... idempotentKey=order1.csv-ORDER02
2018-07-25 11:10:31,316 | INFO  | se1t/data/orders | SplitOrderFileRoute              | 159 - org.apache.camel.camel-core - 2.17.0.redhat-630347 |
```


3. Concurrent file processing - Duplicates files

Try to send the order1.csv file twice

```
  $ cp  `order1.csv`  `/opt/fuse1t/data/orders/`
  $ cp  `order1.csv`  `/opt/fuse1t/data/orders/`
```

On each container, you will notice the duplicate file is not handled unless the entry with key=order1.csv expires in the first cache: CACHE_FP_01.
The process cannot acquire the read lock

```
JBossFuse:karaf@root> container-connect cnt-file-puller1
JBossFuse:admin@cnt-file-puller1> log:tail
0347 | Cannot acquire read lock. Will skip the file: GenericFile[/opt/fuse1t/data/orders/order1.csv]
2018-07-25 12:19:01,773 | WARN  | se1t/data/orders | potentRepositoryReadLockStrategy | 159 - org.apache.camel.camel-core - 2.17.0.redhat-630347 | Cannot acquire read lock. Will skip the file: GenericFile[/opt/fuse1t/data/orders/order1.csv]


JBossFuse:karaf@root> container-connect cnt-file-puller2
JBossFuse:admin@cnt-file-puller2> log:tail

2018-07-25 12:23:39,421 | WARN  | se1t/data/orders | potentRepositoryReadLockStrategy | 159 - org.apache.camel.camel-core - 2.17.0.redhat-630347 | Cannot acquire read lock. Will skip the file: GenericFile[/opt/fuse1t/data/orders/order1.csv]
2018-07-25 12:23:39,922 | WARN  | se1t/data/orders | potentRepositoryReadLockStrategy | 159 - org.apache.camel.camel-core - 2.17.0.redhat-630347 | Cannot acquire read lock. Will skip the file: GenericFile[/opt/fuse1t/data/orders/order1.csv]
```


4. Concurrent file processing - Duplicates commands in different files.


Try to send the order1.csv file twice

```
  $ cp  `order1.csv`  `/opt/fuse1t/data/orders/`
  $ cp  `order1.csv`  `/opt/fuse1t/data/orders/`
```

As idempotent key is fileName and orderId, you will have 2 files in the first cache and 4 items in the order caches

```
[standalone@127.0.0.1:10090 /] /subsystem=datagrid-infinispan/cache-container=clustered/replicated-cache=CACHE_FP_01:read-attribute(name=number-of-entries)
{
    "outcome" => "success",
    "result" => 2
}
[standalone@127.0.0.1:10090 /] /subsystem=datagrid-infinispan/cache-container=clustered/replicated-cache=CACHE_FP_02:read-attribute(name=number-of-entries)
{
    "outcome" => "success",
    "result" => 4
}
[standalone@127.0.0.1:10090 /]
```
A system to avoid such situation will be to use only the orderId as idempotentKey.
