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
		Read r = new Read("projects.csv",false)
		def projects = r.getProjects()
		restoreGitRepositories(projects)

		projects.each {
			Extractor e = new Extractor(it,false)
			e.fillAncestors()
			
			e.extractCommits(Strategy.ALL)
			
			println('Project ' + it.name + " read")
		}
		println('Mining Finished!\n')
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

	public static void main (String[] args){
		runNoGitminer()
	}
}