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

		//download scenarios
		LinkedList<MergeCommit> horizontalExecutionMergeCommits = fillMergeCommitsListForHorizontalExecution(projects)
		//horizontalExecutionMergeCommits = filter(horizontalExecutionMergeCommits)
		for(int i=0; i<horizontalExecutionMergeCommits.size();i++){
			MergeCommit m = horizontalExecutionMergeCommits.get(i);
			println ('Downloading ' + ((i+1)+'/'+horizontalExecutionMergeCommits.size()) + ': ' +  m.sha)
			Extractor ext = new Extractor(m)
			ext.download_merge_scenario(m)
		}
		println 'Downloads Finished!'
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

		String a;
		a.equalsIgnoreCase(a)
	}

	def private static filter(List<MergeCommit> mergeCommits){
		//TODO IMPLEMENT BELOW YOUR CRITERIA FOR FILTERING MERGE COMMITS
		List<MergeCommit> filtered = new ArrayList<>()
		File f = new File('filter.csv')
		if(!f.exists() ) {
			println "Filter file does not exist!"
		} else {
			for(MergeCommit m : mergeCommits){
				f.eachLine { line ->
					String[] a = line.split(';')
					String pname = a[0]
					String right = a[1]
					String left  = a[2]
					if(m.projectName.equalsIgnoreCase(pname) && m.parent2.startsWith(right) && m.parent1.startsWith(left)){
						filtered.add(m)
					}
				}
			}
		}
		return filtered
	}

	public static void main (String[] args){
		run()
	}
}