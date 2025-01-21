
---

### Building the Project

This project uses Maven for building. To build the project, follow these steps:

1. **Add the Pterodactyl4J library**:
    Before building, ensure the Pterodactyl4J library is added to the project. You can either add it as a dependency or include the source directly. For instructions on how to add Pterodactyl4J, visit the Pterodactyl4J GitHub repository.

2. **Run the Maven build**:  
   Use the Maven `package` command to create the build. Ensure you are using the shaded JAR for proper packaging of dependencies.

   ```bash
   mvn package
   ```

3. **Configure the output location**:  
   If needed, modify the output location for the shaded JAR in the `<configuration>` section of the Maven Shade Plugin in the `pom.xml`. Update the `outputFile` as required.

---