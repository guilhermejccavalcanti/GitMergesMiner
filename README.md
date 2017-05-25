###Compilation

This project uses maven to manage all dependencies.
Using eclipse, you need groovy compiler 2.0 and groovy-maven [installed](https://github.com/groovy/groovy-eclipse/wiki).

###Usage

1. Update the file `config.properties´ with the directory where the mining will be made
2. Update the file `projects.csv´ with the projects to be mined, each line contains the name and URL of the project, optionally dates of start and end of mining (there is an example for you to guide yourselves)
3. Run the class `App.groovy´
4. The mining result will be in the `project/projectName/revision´ folder

ps.: execution can take long depending on the speed of the connection and the size of the project.