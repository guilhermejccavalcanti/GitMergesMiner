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
import br.ufpe.cin.app.JFSTMerge;

class App {

	def static run(){
		//read csv and clone github repositories
		Read r = new Read("projects.csv",false)
		def projects = r.getProjects()

		//fill merge scenarios info (base,left,right)
		projects.each {
			Extractor e = new Extractor(it,false)
			e.fillAncestors()
			println('Project ' + it.name + " read")
		}

		//download scenarios
		LinkedList<MergeCommit> horizontalExecutionMergeCommits = fillMergeCommitsListForHorizontalExecution(projects)
		for(int i=0; i<horizontalExecutionMergeCommits.size();i++){
			MergeCommit m = horizontalExecutionMergeCommits.get(i);
			println ('Analysing ' + ((i+1)+'/'+horizontalExecutionMergeCommits.size()) + ': ' +  m.sha)
			Extractor ext = new Extractor(m)
			ext.download_merge_scenario(m)


			JFSTMerge merger = new JFSTMerge()
			if(m.revisionFile != null){
				fillExecutionLog(m) //allows execution restart from where it stopped
				merger.mergeRevisions(m.revisionFile)
			}
		}
		println 'Analysis Finished! Results in ' + System.getProperty("user.home") + File.separator + ".jfstmerge"
	}

	def private static restoreGitRepositories(ArrayList<Project> projects){
		projects.each {
			Extractor e = new Extractor(it,true)
			e.restoreWorkingFolder()
		}
		println('Restore finished!\n')
	}

	def private static LinkedList<MergeCommit> fillMergeCommitsListForHorizontalExecution(ArrayList<Project> projects){
		LinkedList<MergeCommit> horizontalExecutionMergeCommits = new LinkedList<MergeCommit>()
		int aux = projects.size()
		int i 	= 0;
		while(i < projects.size()) {
			Project p = projects.get(i)
			if(!p.listMergeCommit.isEmpty()){
				MergeCommit mergeCommit = p.listMergeCommit.poll()
				horizontalExecutionMergeCommits.add(mergeCommit)
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
		Collections.shuffle(horizontalExecutionMergeCommits);
		if(horizontalExecutionMergeCommits.size() > 10000){
			return horizontalExecutionMergeCommits.subList(0, 10000);
		} else {
			return horizontalExecutionMergeCommits;
		}
	}

	def private static fillExecutionLog(MergeCommit lastMergeCommit){
		def out = new File('execution.log')
		out.append (lastMergeCommit.projectName+','+lastMergeCommit.sha)
		out.append '\n'
	}

	public static void main (String[] args){
		run()
	}
}