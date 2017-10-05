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

	def public static run_gitmerges(){
		new File("execution.log").createNewFile()
		Read r = new Read("projects.csv",false)
		def projects = r.getProjects()
		restoreGitRepositories(projects)

		//fill merge scenarios info (base,left,right)
		projects.each {
			Extractor e = new Extractor(it,false)
			e.fillAncestors()
			println('Project ' + it.name + " read")

		}

		//reproduce the git merge command for each merge scenario
		LinkedList<MergeCommit> horizontalExecutionMergeCommits = fillMergeCommitsListForHorizontalExecution(projects)
		for(int i=0; i<horizontalExecutionMergeCommits.size();i++){
			MergeCommit m = horizontalExecutionMergeCommits.get(i);
			println ('Analysing ' + ((i+1)+'/'+horizontalExecutionMergeCommits.size()) + ': ' +  m.sha)
			fillExecutionLog(m)

			Extractor e = new Extractor(m)
			e.git_merge(m)

			//compute statistics related to each scenario
			computeStatistcs(m)
		}
		println 'Analysis Finished!'
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

	def private static computeStatistcs(MergeCommit m){
		//retrieving statistics
		String logpath  = System.getProperty("user.home")+ File.separator + ".jfstmerge" + File.separator;
		File statistics_partial = new File(logpath+ "jfstmerge.statistics");
		File statistics_files 	= new File(logpath+ "jfstmerge.statistics.files");
		if(!statistics_files.exists()){statistics_files.mkdirs();statistics_files.createNewFile();} //ensuring it exists
		/*
		 * jfstmerge.statistics contains numbers for each merged file. To compute numbers for each scenario,
		 * we read the jfstmerge.statistics file, and then we delete this file. Thus, every time a merge scenario is merged
		 * the jfstmerge.statistics file is empty. To have overall number for all files, regardless merge scenarios, we append
		 * jfstmerge.statistics content in the jfstmerge.statistics.files before deleting jfstmerge.statistics.
		 */

		if(statistics_partial.exists()){
			int ssmergeconfs = 0;
			int ssmergeloc 	 = 0;
			int ssmergerenamingconfs = 0;
			int ssmergedeletionconfs = 0;
			int ssmergetaeconfs   = 0;
			int ssmergenereoconfs = 0;
			int ssmergeinitlblocksconfs = 0;
			int unmergeconfs = 0;
			int unmergeloc 	 = 0;
			long unmergetime = 0;
			long ssmergetime = 0;
			int unmergeorderingconfs = 0;
			int unmergeduplicateddeclarationerrors = 0;
			int equalconfs 	 = 0;

			List<String> lines = statistics_partial.readLines();
			for(int y = 1/*ignoring header*/; y <lines.size(); y++){
				String[] columns = lines.get(y).split(",");

				statistics_files.append(lines.get(y)+'\n')

				ssmergeconfs += Integer.valueOf(columns[2]);
				ssmergeloc += Integer.valueOf(columns[3]);
				ssmergerenamingconfs += Integer.valueOf(columns[4]);
				ssmergedeletionconfs += Integer.valueOf(columns[5]);
				ssmergetaeconfs += Integer.valueOf(columns[6]);
				ssmergenereoconfs += Integer.valueOf(columns[7]);
				ssmergeinitlblocksconfs += Integer.valueOf(columns[8]);
				unmergeconfs += Integer.valueOf(columns[9]);
				unmergeloc += Integer.valueOf(columns[10]);
				unmergetime += Long.parseLong(columns[11]);
				ssmergetime += Long.parseLong((columns[12]));
				unmergeduplicateddeclarationerrors += Integer.valueOf(columns[13]);
				unmergeorderingconfs += Integer.valueOf(columns[14]);
				equalconfs += Integer.valueOf(columns[15]);
			}
			//unmergeorderingconfs = (unmergeconfs - ssmergeconfs) + unmergeduplicateddeclarationerrors - (ssmergetaeconfs + ssmergenereoconfs + ssmergeinitlblocksconfs);unmergeorderingconfs=(unmergeorderingconfs>0)?unmergeorderingconfs:0;
			(new AntBuilder()).delete(file:statistics_partial.getAbsolutePath(),failonerror:false)

			logpath  = System.getProperty("user.home")+ File.separator + ".jfstmerge" + File.separator;
			statistics_partial = new File(logpath+ "jfstmerge.statistics.scenarios");
			if(!statistics_partial.exists())statistics_partial.createNewFile() //ensuring it exists

			def loggermsg = m.projectName + ";" + m.sha + ";" + m.parent1 + ";" + m.ancestor + ";" + m.parent2 + ";" + ssmergeconfs + ";" + ssmergeloc + ";" + ssmergerenamingconfs + ";" + ssmergedeletionconfs + ";" + ssmergetaeconfs + ";" + ssmergenereoconfs + ";" + ssmergeinitlblocksconfs + ";" + unmergeconfs + ";" + unmergeloc + ";" + unmergeorderingconfs + ";" + unmergeduplicateddeclarationerrors + ";" + equalconfs + ";" + ssmergetime + ";" + unmergetime+'\n';
			statistics_partial.append(loggermsg)
		}
	}

	public static void main (String[] args){
		run_gitmerges()
	}
}