[version]: https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Frepo1.maven.org%2Fmaven2%2Fio%2Fgithub%2Fprotoxon%2FS4J%2Fmaven-metadata.xml&color=informational&label=Maven%20Central
[github]: https://github.com/jessefaler/SLS/tree/main/S4J
[github-shield]: https://img.shields.io/badge/GitHub-S4J-181717?logo=github
[javadocs]: https://javadoc.io/doc/io.github.protoxon/S4J
[javadoc-shield]: https://javadoc.io/badge2/io.github.protoxon/S4J/javadoc.svg
[download]: #download
[ ![version][] ][download]
[ ![github-shield][] ][github]
[ ![javadoc-shield][] ][javadocs]

# S4J

S4J is a java wrapper for the SLS Protocube REST API

## Creating the SLSClient Object

Creating the SLSClient object is done via the SLSBuilder class. Make sure to set your application URL and token as necessary.

**Example**:
```java
SLSClient api = SLSBuilder.createClient("https://127.0.0.1", "xyz321");
```

### Examples:

**Creating a server**:
```java
public class ServerCreator
{
    public static void main(String[] args)
    {
        SLSClient api = SLSBuilder.createClient("https://127.0.0.1", "xyz321");
        
        SLSAction<ClientServer> action = api.createServer()
                .setBlueprintId("blueprint_id");
        ClientServer server = action.execute();

    }
}
```

**Getting blueprints Asynchronously**:
```java
public class BlueprintRetriever
{
    public static void main(String[] args)
    {
        SLSClient api = SLSBuilder.createClient("https://127.0.0.1", "xyz321");
        
        PaginationAction<Blueprint> action = api.getBlueprints();
        action.executeAsync(
            (blueprints) -> {
                // Success callback
                for (Blueprint blueprint : blueprints) {
                    System.out.println("Blueprint: " + blueprint.getName() + " (ID: " + blueprint.getId() + ")");
                }
            },
            (error) -> {
                // Failure callback
                System.err.println("Failed to retrieve blueprints: " + error.getMessage());
                error.printStackTrace();
            }
        );
    }
}
```

**Using WebSocket Event Stream**:
```java
public class EventStreamListener
{
    public static void main(String[] args)
    {
        SLSClient api = SLSBuilder.createClient("https://127.0.0.1", "xyz321");
        
        WebSocketEventStream eventStream = api.getEventStream();
        
        // Handle parsed server events (StatusUpdateEvent, ServerCrashEvent, etc.)
        eventStream.onServerEvent((event) -> {
            String serverId = event.getServerId();
            
            if (event instanceof StatusUpdateEvent) {
                StatusUpdateEvent statusEvent = (StatusUpdateEvent) event;
                System.out.println("Server " + serverId + " status changed to: " + statusEvent.getStatus());
            } else if (event instanceof ServerCrashEvent) {
                ServerCrashEvent crashEvent = (ServerCrashEvent) event;
                System.out.println("Server " + serverId + " crashed: " + crashEvent.getReason() + " (exit code: " + crashEvent.getExitCode() + ")");
            }
        });
        
        // Handle raw WebSocket events
        eventStream.onEvent((event) -> {
            System.out.println("Received event on topic: " + event.topic);
            System.out.println("Event data: " + event.dataAsJson().toString());
        });
        
        // Handle errors
        eventStream.onError((error) -> {
            System.err.println("WebSocket error: " + error.getMessage());
            error.printStackTrace();
        });
        
        // Start the event stream
        eventStream.start();
        
        // Keep the program running to receive events
        // In a real application, you might want to add shutdown hooks
        try {
            Thread.sleep(60000); // Listen for 60 seconds
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // Stop the event stream when done
        eventStream.stop();
    }
}
```

### SLSAction

SLSAction is designed 
to make request handling simple.

It provides lazy request handling by offering asynchronous callbacks
and synchronous execution.

This gives the user with a variety of patterns to use when making a request. The recommended approach is to use the asynchronous methods whenever possible.

The interface also supports several operators to improve quality of life:

- map
   Allows you to convert the result of a SLSAction to a different value
- flatmap
   Allows you to chain another SLSAction on the result of the previous one
- delay
   Delays execution of the previous SLSAction
  
**Example**:
```java
public void startServer(String identifier) {
    System.out.println("Starting server in 5 seconds...");
    client.retrieveServerByIdentifier(identifier) // retrieve the client server
        .delay(5, TimeUnit.SECONDS) // wait 5 seconds
        .flatMap(ClientServer::start) // start the server
        .executeAsync(__ -> System.out.println("Starting server " + identifier + " now"));
}
```

### Rate limiting

S4J handles rate limiting from SLS by keeping track of requests, pausing
execution when the limit is hit, and finishing the remaining requests when the limit is lifted.

S4J can do this by keeping a queue of requests that are waiting to be executed.
When S4J is not being rate limited, it will continue to poll requests and execute.

When queuing requests with `execute()`, there can only be one request in the queue concurrently.
Each of the following requests will be handled in order.

However, requests queued with `executeAsync()` will be handled unordered due to the asychronous nature.
With this approach, there can be an "unlimited" number of pending requests waiting
to be handled when it is convenient for S4J.

Queuing requests asynchronously is generally faster than a synchronous approach, which is why the former is preferred to the latter.

## Download
Latest version: [ ![version][] ][download]

Artifacts are on **Maven Central** (`https://repo1.maven.org/maven2/io/github/protoxon/S4J/`). Use the version from the badge above, or substitute **`1.0.2`** with a newer release when available.

**Maven**
```xml
<dependency>
    <groupId>io.github.protoxon</groupId>
    <artifactId>S4J</artifactId>
    <version>1.0.2</version>
</dependency>
```

**Gradle (Kotlin DSL)**
```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.protoxon:S4J:1.0.2")
}
```

**Gradle (Groovy)**
```gradle
repositories {
    mavenCentral()
}

dependencies {
    implementation 'io.github.protoxon:S4J:1.0.2'
}
```

**Javadoc:** [javadoc.io](https://javadoc.io/doc/io.github.protoxon/S4J)