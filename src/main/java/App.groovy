import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream;
import java.io.PrintWriter
import java.util.ArrayList;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.regex.Matcher

import br.ufpe.cin.app.JFSTMerge;
import util.LoggingOutputStream;
import util.LoggerPrintStream;
import util.StdOutErrLevel;


class App {

	def static run(){
		Read r = new Read("projects.csv",true)
		def projects = r.getProjects()
		println('Reader Finished!')

		projects.each {
			GremlinQuery gq = new GremlinQuery(it.graph)

			Printer p = new Printer()
			p.writeCSV(gq.getMergeCommitsList())
			println('Printer Finished!')
			println("----------------------")

			it.setMergeCommits(gq.getMergeCommitsList())

			Extractor e = new Extractor(it,true)
			e.extractCommits(Strategy.ALL)
			println('Extractor Finished!\n')
			gq.graph.shutdown();
		}
	}

	def static runNoGitminer(){
		new File("execution.log").createNewFile()

		//read csv and clone github repositories
		Read r = new Read("projects.csv",false)
		def projects = r.getProjects()
		restoreGitRepositories(projects)

		//fill merge scenarios info (base,left,right)
		projects.each {
			Extractor e = new Extractor(it,false)
			e.fillAncestors()
			println('Project ' + it.name + " read")
		}

		//download scenarios and execute analysis
		LinkedList<MergeCommit> horizontalExecutionMergeCommits = fillMergeCommitsListForHorizontalExecution(projects)
		for(int i=0; i<horizontalExecutionMergeCommits.size();i++){
			MergeCommit m = horizontalExecutionMergeCommits.get(i);
			println ('Analysing ' + ((i+1)+'/'+horizontalExecutionMergeCommits.size()) + ': ' +  m.sha)

			Extractor ext = new Extractor(m)
			ext.downloadMergeScenario(m)

			JFSTMerge merger = new JFSTMerge()
			if(m.revisionFile != null){
				fillExecutionLog(m) //allows execution restart from where it stopped

				merger.mergeRevisions(m.revisionFile)

				// deleted merged revisions
				String revisionFolderDir = (new File(m.revisionFile)).getParent()
				(new AntBuilder()).delete(dir:revisionFolderDir,failonerror:false)

				System.gc();
			}
		}
		println 'Analysis Finished!'
	}

	def static runWithCommitCsv(){
		// running directly with the commit list in a CSV file
		Read r = new Read("projects.csv",true)
		def projects = r.getProjects()
		println('Reader Finished!')

		projects.each {
			r.setCsvCommitsFile("commits.csv")
			r.readCommitsCSV()
			def ls = r.getMergeCommitsList()

			it.setMergeCommits(ls)

			Extractor e = new Extractor(it,true)
			e.extractCommits(Strategy.ALL)
			println('Extractor Finished!\n')
		}
	}

	def static collectMergeCommits(){
		Read r = new Read("projects.csv",true)
		def projects = r.getProjects()
		println('Reader Finished!')

		projects.each {
			GremlinQuery gq = new GremlinQuery(it.graph)

			Printer p = new Printer()
			p.writeCSV(it.name,gq.getMergeCommitsList())
			p.writeNumberOfMergeCommits(it.name,gq.getMergeCommitsList());
			println('Printer Finished!')
			println("----------------------")

			it.setMergeCommits(gq.getMergeCommitsList())
			gq.graph.shutdown();
		}
	}

	def static ArrayList<Project> readProjects(){
		Read r = new Read("projects.csv",true)
		def projects = r.getProjects()

		projects.each {
			GremlinQuery gq = new GremlinQuery(it.graph)
			Printer p = new Printer()
			p.writeCSV(gq.getMergeCommitsList())
			it.setMergeCommits(gq.getMergeCommitsList())

			Extractor e = new Extractor(it,true)
			e.fillAncestors()
			println('Project ' + it.name + " read")

			gq.graph.shutdown();
		}

		return projects
	}

	def static ArrayList<Project> readProjectsNoGitMiner(){
		Read r = new Read("projects.csv",false)
		def projects = r.getProjects()
		restoreGitRepositories(projects)

		projects.each {
			Extractor e = new Extractor(it,false)
			e.fillAncestors()
			println('Project ' + it.name + " read")
		}

		return projects
	}

	def private static restoreGitRepositories(ArrayList<Project> projects){
		projects.each {
			Extractor e = new Extractor(it,true)
			e.restoreWorkingFolder()
		}
		println('Restore finished!\n')
	}

	def private static LinkedList<MergeCommit> fillMergeCommitsListForHorizontalExecution(ArrayList<Project> projects){
		ArrayList<String> alreadyExecutedSHAs = restoreExecutionLog();
		LinkedList<MergeCommit> horizontalExecutionMergeCommits = new LinkedList<MergeCommit>()
		int aux = projects.size()
		int i 	= 0;
		while(i < projects.size()) {
			Project p = projects.get(i)
			if(!p.listMergeCommit.isEmpty()){
				MergeCommit mergeCommit = p.listMergeCommit.poll()
				if(!alreadyExecutedSHAs.contains(mergeCommit.projectName+','+mergeCommit.sha)){
					//if(alreadyExecutedSHAs.contains(mergeCommit.projectName+','+mergeCommit.sha)){
					horizontalExecutionMergeCommits.add(mergeCommit)
				}
			}
			if(p.listMergeCommit.isEmpty()){
				projects.remove(i)
			}
			aux 	= projects.size()
			if(aux == 0){
				break
			}
			if(i >= (projects.size() - 1)){
				i = 0;
			} else {
				i++;
			}
		}
		return horizontalExecutionMergeCommits
	}

	def private static fillExecutionLog(MergeCommit lastMergeCommit){
		def out = new File('execution.log')
		out.append (lastMergeCommit.projectName+','+lastMergeCommit.sha)
		out.append '\n'
	}

	def private static ArrayList<String> restoreExecutionLog(){
		ArrayList<String> alreadyExecutedSHAs = new ArrayList<String>()
		try {
			BufferedReader br = new BufferedReader(new FileReader("execution.log"))
			String line  = ""
			while ((line = br.readLine()) != null)
				alreadyExecutedSHAs.add(line)
		} catch (FileNotFoundException e) {}
		return alreadyExecutedSHAs
	}

	public static void main (String[] args){
		runNoGitminer()
	}
}