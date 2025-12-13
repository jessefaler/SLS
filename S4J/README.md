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