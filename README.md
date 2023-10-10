![header](https://i.imgur.com/9tjhHQG.png)

**Redis Extended PubSub** (`RxPubSub` for short) is the ultimate tool for utilizing the [Redis Pub/Sub](https://redis.io/docs/interact/pubsub/) feature in Java applications. It offers elegant methods for publishing and receiving messages, implementing callbacks and more!

# Getting Started
**Maven**
```xml
<dependency>
  <groupId>io.github.feniksovich</groupId>
  <artifactId>rxpubsub</artifactId>
  <version>1.0.1</version>
</dependency>
```
**Gradle**
```gradle
dependencies {
    implementation 'io.github.feniksovich:rxpubsub:1.0.1'
}
```

# Quick Reference
## Pub/Sub Message
### Constructing Message
Pub/Sub messages are represented by its own Java classes now. Every Java class that represents your Pub/Sub message must extend `PubSubMessage` abstract class.
**For example:**

```java
public class MyPubSubMessage extends PubSubMessage {
    
    private String payload;

    public MyPubSubMessage(String payload) {
        this.payload = payload;
    }

    public String getPayload() {
        return payload;
    }

    public MyPubSubMessage setPayload(String payload) {
        this.payload = payload;
        return this;
    }
    
    // etc...
}
```

### Registering Message
To receive and publish a message class must be registered with `MessagingService#registerMessageClass(PubSubMessage)`.
It's required in order to generate internal classes identifiers to deserialize messages properly when received.

## Pub/Sub Channel
### Constructing Channel
Pub/Sub channels now consist of two identifiers called `namespace` & `name` and construct with `PubSubChannel#from(String, String)` method:

```java
public class MyApp {
    public static final PubSubChannel channel = PubSubChannel.from("app", "requests");
}
```
Internally in Redis these channels are represented as a string in the `namespace:name` format. 

### Registering Channel
There are two channels directions exist:
- **Incoming** – you will receive messages on this channel, registers with `MessagingService#registerIncomingChannel(PubSubChannel)`.
- **Outgoing** – you will publish messages on this channel, registers with `MessagingService#registerOutgoingChannel(PubSubChannel)`.

A channel that is receiving (incoming) and publishing (outgoing) messages is called a **duplex** channel and registers with `MessagingService#registerDuplexChannel(PubSubChannel)`.
This method is actually calls two methods described above.

## Messaging Service
`MessagingService` is a library entrypoint that provides all required methods to register components and publish messages. It uses own `MessagingServiceConfig` to construct that actually encapsulates authentication credentials and other settings such as queries execution timeout.

```java
import io.github.feniksovich.rxpubsub.MessagingService;
import io.github.feniksovich.rxpubsub.misc.MessagingServiceConfig;

public class MyApp {

    public static final PubSubChannel channel = PubSubChannel.from("app", "requests");

    public void runExample() {
        MessagingServiceConfig config = new MessagingServiceConfig(
                "localhost", 6379, "default", "password", 2000
        );
        MessagingService messagingService = new MessagingService(config);
    }
}
```

## Reception Listeners
There are two types of listeners exist.

### Simple Listener
Just listens for the pub/sub message reception and runs something actions.

```java
import io.github.feniksovich.rxpubsub.MessagingService;
import io.github.feniksovich.rxpubsub.misc.MessagingServiceConfig;

public class MyApp {

    public static final PubSubChannel channel = PubSubChannel.from("app", "notifications");

    public void runExample() {
        MessagingServiceConfig config = new MessagingServiceConfig(
                "localhost", 6379, "default", "password", 2000
        );

        MessagingService messagingService = new MessagingService(config)
                .registerIncomingChannel(channel)
                .registerMessageClass(MyPubSubMessage.class);

        messagingService.getEventBus().registerReceiptListener(MyPubSubMessage.class, this::onMessage);
    }

    private void onMessage(MyPubSubMessage message) {
        System.out.println("Received message: " + message);
    }
}
```

### Responding Listener
Like a simple one listens for pub/sub message reception, runs something actions and **returns a new `PubSubMessage` as a result and publishes it**.
It should be used when the received message describes some kind of request and is sent in a special way (see "Publish and wait for response" section below).

```java
import io.github.feniksovich.rxpubsub.MessagingService;
import io.github.feniksovich.rxpubsub.misc.MessagingServiceConfig;
import io.github.feniksovich.rxpubsub.model.PubSubMessage;

public class MyApp {

    public static final PubSubChannel channel = PubSubChannel.from("app", "math");

    public void runExample() {
        MessagingServiceConfig config = new MessagingServiceConfig(
                "localhost", 6379, "default", "password", 2000
        );

        MessagingService messagingService = new MessagingService(config)
                .registerDuplexChannel(channel) // register as duplex to respond
                .registerMessageClass(MyPubSubRequest.class)
                .registerMessageClass(MyPubSubCallback.class);

        messagingService.getEventBus().registerRespondingListener(MyPubSubMessage.class, channel, this::onRequest);
    }

    private PubSubMessage onRequest(MyPubSubRequest request) {
        int result = request.getA() + request.getB();
        return new MyPubSubCallback().setResult(result);
    }
}
```

## Publishing Messages

### Simple Publish
We can publish pub/sub message in a channel as usual.

```java
import io.github.feniksovich.rxpubsub.MessagingService;
import io.github.feniksovich.rxpubsub.misc.MessagingServiceConfig;

public class MyApp {

    public static final PubSubChannel channel = PubSubChannel.from("app", "messages");

    public void runExample() {
        MessagingServiceConfig config = new MessagingServiceConfig(
                "localhost", 6379, "default", "password", 2000
        );

        MessagingService messagingService = new MessagingService(config)
                .registerOutgoingChannel(channel)
                .registerMessageClass(MyPubSubMessage.class);

        MyPubSubMessage message = new MyPubSubMessage().setPayload("Hello!");
        messagingService.publishMessage(channel, message);
    }
}
```

### Publish & Wait for Response
Let's imagine we need to get information from another application via Pub/Sub messages. In this case we can publish a message and wait for a response to it!

```java
import io.github.feniksovich.rxpubsub.MessagingService;
import io.github.feniksovich.rxpubsub.model.CallbackHandler;
import io.github.feniksovich.rxpubsub.misc.MessagingServiceConfig;

import java.util.concurrent.TimeUnit;

public class MyApp {

    public static final PubSubChannel channel = PubSubChannel.from("app", "math");

    public void runExample() {
        MessagingServiceConfig config = new MessagingServiceConfig(
                "localhost", 6379, "default", "password", 2000, 2000
        );

        MessagingService messagingService = new MessagingService(config)
                .registerDuplexChannel(channel) // register as duplex to receive response
                .registerMessageClass(MyPubSubRequest.class)
                .registerMessageClass(MyPubSubCallback.class);

        MyPubSubMessage message = new MyPubSubRequest().setA(2).setB(2);
        messagingService.publishMessage(channel, message, new CallbackHandler<>(MyPubSubCallback.class)
                .handle(message -> System.out.println(message.getResult()))
                .handleError(throwable -> System.out.println("Publish error occurred: " + throwable.getMessage()))
                .setTimeout(2, TimeUnit.SECONDS)
                .handleTimeout(() -> System.out.println("Timeout!"))
        );
    }
}
```
> **Note**
> A message is identified as a response by the presence of the reserved field `@rxps_response_to` in the string representation of the message that contains source message ID.
> The best way to handle such requests and send response on it is use **Responding Listeners**. If it's not possible (for example, if you need asynchronous calculations to respond), you can manually specify the ID of the message to which you are responding using the `PubSubMessage#setResponseTo(String)` method.

# Advanced
## Message Signature
`RxPubSub` injects their own fields and values in the pub/sub JSON representation during message serialization or publishing. These fields always has `@rxps` prefix: 
- `@rxps_message_id` – includes during message object serialization and provides a unique ID (UUID v4) of the message that generates automatically on message object creation.
- `@rxps_class_id` – includes before message publishing and provides the class ID to use to deserialize JSON message.
- `@rxps_respond_to` *(optional)* – includes during message object serialization, if present, and provides source message ID that this message responds to. It assigns automatically if message publishes by **Responding Listener**.

```
{
  "@rxps_message_id": "60b14a18-b251-4ba5-9926-3fc7b50bd928",
  "@rxps_class_id": "MyPubSubMessage",
  "@rxps_respond_to": "5bfcbdb6-05ce-4242-9e50-65a63f5c74ad",
  // your custom fields
} 
```

## Class ID
Class ID (value of `@rxps_class_id` field) is the [simple name](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#getSimpleName()) of the class by default. It's also allowed override class ID by providing it with `@OverridePubSubClassId` annotation in the message class.

```java
import annotations.io.github.feniksovich.rxpubsub.OverridePubSubClassId;
import io.github.feniksovich.rxpubsub.model.PubSubMessage;

@OverridePubSubClassId("CustomClassIdOfMyPubSubMessage")
public class MyPubSubMessage extends PubSubMessage {
    // fields, getters & setters, etc...
}
```
Keep in mind that a class represents the same pub/sub message must have the same identifier across all your applications that use `RxPubSub`!

# Third-party Libraries
- [Lettuce](https://github.com/lettuce-io/lettuce-core) – Java Redis client implementation with stateful connections.
- [Gson](https://github.com/google/gson) – uses to serialize/deserialize message to operate with native Redis Pub/Sub.
- [Guava](https://github.com/google/guava) – preconditions is so neat!

# License
This package includes software licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).
<br>RxPubSub released under the [MIT License](LICENSE), enjoy!
