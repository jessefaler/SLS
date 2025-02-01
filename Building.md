### Building the Project

This project uses Maven for building. To build the project, follow these steps:

1. **Add the Pterodactyl4J library**:\
   Before building, ensure the Pterodactyl4J library is added to the project. You can either add it as a dependency or include the source directly. For instructions on how to add Pterodactyl4J, visit the Pterodactyl4J GitHub repository.

Here's how you can add the link with the name "wings":

2. **Set up Pterodactyl**:\
   Install and configure the Pterodactyl panel and wings. The SLS plugin requires a custom version of Pterodactyl wings, which can be found [here](https://github.com/jessefaler/wings) Follow the setup instructions provided there. Once wings is configured, update the `APPLICATION_API_URL` and `CLIENT_API_URL` in the `Endpoint` enum inside the API package of SLS to match your panel's URL. Then, generate an `APPLICATION_API_KEY` and `CLIENT_API_KEY` and set them in the `Endpoint` enum accordingly.

3. **Run the Maven build**:\
   Use the Maven package command to create the build. Ensure you are using the shaded JAR for proper packaging of dependencies.

   ```bash
   mvn package
   ```

4. **Configure the output location**:\
   If needed, modify the output location for the shaded JAR in the `<configuration>` section of the Maven Shade Plugin in the `pom.xml`. Update the `outputFile` as required.

5. **Set up the Velocity proxy server**:\
   Once you have built the JAR file, set up a Velocity proxy server. Place the generated plugin JAR into the `plugins` folder of your proxy server.

6. **Build the SLS Docker image**:\
   The Dockerfile is located in the `DockerImage` package in the root folder of the repository. Build this image on the machine running Wings.

7. **Add the SLS egg to Pterodactyl**:\
   The SLS egg can be found in the `pteroEgg` package in the root folder of the repository. Download the egg and add it through the Pterodactyl panel.

If you have any issues or questions, feel free to ask in the discussions section.

