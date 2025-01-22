## Code Guidelines

1. **ProtoMessage Usage**  
   While it is not required, it is **recommended** to use `ProtoMessage` where applicable. Please refer to the [documentation](#) to learn more about `ProtoMessage` and how it can improve the handling of message-based communication in the project.

2. **Javadoc for Methods**  
   All methods should have Javadocs that clearly explain:
    - **What the method does.**
    - **Parameters**: A description of each parameter passed into the method.
    - **Return Values**: What the method returns, and any relevant details about the return type.

3. **Asynchronous Pterodactyl API Calls**  
   Any calls to the Pterodactyl API must be executed asynchronously to avoid blocking the main thread.
    - Methods within the API class should either handle asynchronous execution internally or return a `PteroAction`.
    - If a `PteroAction` is returned, it should be executed using `executeAsync` to ensure non-blocking behavior. You can find more information about using `PteroAction` in the [official documentation](https://ci.mattmalec.com/job/Pterodactyl4J/javadoc/com/mattmalec/pterodactyl4j/PteroAction.html).
    - **Do not use the `HttpClient` class**—it is deprecated and does not provide asynchronous execution capabilities.

---