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

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;


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
		if(!statistics_files.exists()){(new File(logpath)).mkdirs();statistics_files.createNewFile();} //ensuring it exists
		/*
		 * jfstmerge.statistics contains numbers for each merged file. To compute numbers for each scenario,
		 * we read the jfstmerge.statistics file, and then we delete this file. Thus, every time a merge scenario is merged
		 * the jfstmerge.statistics file is empty. To have overall number for all files, regardless merge scenarios, we append
		 * jfstmerge.statistics content in the jfstmerge.statistics.files before deleting jfstmerge.statistics.
		 */

		int ssmergeconfs = 0;
		int ssmergeloc 	 = 0;
		int ssmergerenamingconfs = 0;
		int ssmergedeletionconfs = 0;
		int ssmergeinnerdeletionconfs = 0;
		int ssmergetaeconfs   = 0;
		int ssmergenereoconfs = 0;
		int ssmergeinitlblocksconfs = 0;
		int ssmergeacidentalconfs = 0;
		int unmergeconfs = 0;
		int unmergeloc 	 = 0;
		long unmergetime = 0;
		long ssmergetime = 0;
		int unmergeorderingconfs = 0;
		int unmergeduplicateddeclarationerrors = 0;
		int equalconfs 	 = 0;

		List<String> lines = new ArrayList<String>();
		if(statistics_partial.exists()){lines = statistics_partial.readLines();}
		for(int y = 1/*ignoring header*/; y <lines.size(); y++){
			String[] columns = lines.get(y).split(",");

			statistics_files.append(lines.get(y)+'\n')

			ssmergeconfs += Integer.valueOf(columns[2]);
			ssmergeloc 	 += Integer.valueOf(columns[3]);
			ssmergerenamingconfs += Integer.valueOf(columns[4]);
			ssmergedeletionconfs += Integer.valueOf(columns[5]);
			ssmergeinnerdeletionconfs += Integer.valueOf(columns[6]);
			ssmergetaeconfs   += Integer.valueOf(columns[7]);
			ssmergenereoconfs += Integer.valueOf(columns[8]);
			ssmergeinitlblocksconfs += Integer.valueOf(columns[9]);
			ssmergeacidentalconfs 	+= Integer.valueOf(columns[10]);
			unmergeconfs += Integer.valueOf(columns[11]);
			unmergeloc 	 += Integer.valueOf(columns[12]);
			unmergetime  += Long.parseLong(columns[13]);
			ssmergetime  += Long.parseLong((columns[14]));
			unmergeduplicateddeclarationerrors += Integer.valueOf(columns[15]);
			unmergeorderingconfs += Integer.valueOf(columns[16]);
			equalconfs 	 += Integer.valueOf(columns[17]);
		}
		//unmergeorderingconfs = (unmergeconfs - ssmergeconfs) + unmergeduplicateddeclarationerrors - (ssmergetaeconfs + ssmergenereoconfs + ssmergeinitlblocksconfs);unmergeorderingconfs=(unmergeorderingconfs>0)?unmergeorderingconfs:0;
		(new AntBuilder()).delete(file:statistics_partial.getAbsolutePath(),failonerror:false)

		logpath  = System.getProperty("user.home")+ File.separator + ".jfstmerge" + File.separator;
		File statistics_scenarios = new File(logpath+ "jfstmerge.statistics.scenarios");
		if(!statistics_scenarios.exists())statistics_scenarios.createNewFile() //ensuring it exists

		def loggermsg = m.projectName + ";" + m.sha + ";" + m.parent1 + ";" + m.ancestor + ";" + m.parent2 + ";" + ssmergeconfs + ";" + ssmergeloc + ";" + ssmergerenamingconfs + ";" + ssmergedeletionconfs + ";" + ssmergeinnerdeletionconfs + ";" + ssmergetaeconfs + ";" + ssmergenereoconfs + ";" + ssmergeinitlblocksconfs + ";" + ssmergeacidentalconfs + ";" + unmergeconfs + ";" + unmergeloc + ";" + unmergeorderingconfs + ";" + unmergeduplicateddeclarationerrors + ";" + equalconfs + ";" + ssmergetime + ";" + unmergetime+'\n';
		statistics_scenarios.append(loggermsg)
		logSummary()
	}

	def private static void logSummary() throws IOException{
		//retrieving statistics
		String logpath   = System.getProperty("user.home")+ File.separator + ".jfstmerge" + File.separator;
		new File(logpath).mkdirs(); //ensuring that the directories exists
		File statistics = new File(logpath+ "jfstmerge.statistics.files");
		if(statistics.exists()){
			int ssmergeconfs = 0;
			int ssmergeloc = 0;
			int ssmergerenamingconfs = 0;
			int ssmergedeletionconfs = 0;
			int ssmergetaeconfs = 0;
			int ssmergenereoconfs = 0;
			int ssmergeinitlblocksconfs = 0;
			int ssmergeacidentalconfs = 0;
			int unmergeconfs = 0;
			int unmergeloc = 0;
			long unmergetime = 0;
			long ssmergetime = 0;
			int duplicateddeclarationerrors = 0;
			int unmergeorderingconfs = 0;
			int equalconfs = 0;

			List<String> lines = statistics.readLines();
			for(int i = 0; i <lines.size(); i++){
				String[] columns = lines.get(i).split(",");

				ssmergeconfs += Integer.valueOf(columns[2]);
				ssmergeloc 	 += Integer.valueOf(columns[3]);
				ssmergerenamingconfs += Integer.valueOf(columns[4]);
				ssmergedeletionconfs += Integer.valueOf(columns[5]);
				ssmergetaeconfs   += Integer.valueOf(columns[7]);
				ssmergenereoconfs += Integer.valueOf(columns[8]);
				ssmergeinitlblocksconfs += Integer.valueOf(columns[9]);
				ssmergeacidentalconfs 	+= Integer.valueOf(columns[10]);
				unmergeconfs += Integer.valueOf(columns[11]);
				unmergeloc 	 += Integer.valueOf(columns[12]);
				unmergetime  += Long.parseLong(columns[13]);
				ssmergetime  += Long.parseLong((columns[14]));
				duplicateddeclarationerrors += Integer.valueOf(columns[15]);
				unmergeorderingconfs += Integer.valueOf(columns[16]);
				equalconfs 	 += Integer.valueOf(columns[17]);
			}

			//summarizing retrieved statistics
			int JAVA_FILES = lines.size();
			//int FP_UN = (unmergeconfs - ssmergeconfs) + duplicateddeclarationerrors - (ssmergetaeconfs + ssmergenereoconfs + ssmergeinitlblocksconfs);FP_UN=(FP_UN>0)?FP_UN:0;
			int FP_UN = unmergeorderingconfs;
			int FN_UN = duplicateddeclarationerrors;
			int FP_SS = ssmergerenamingconfs;
			int FN_SS = (ssmergetaeconfs + ssmergenereoconfs + ssmergeinitlblocksconfs) + ssmergeacidentalconfs;
			double M = ((double)ssmergetime / 1000000000);
			double N = ((double)unmergetime / 1000000000);

			StringBuilder summary = fillSummaryMsg(ssmergeconfs, ssmergeloc,
					unmergeconfs, unmergeloc, equalconfs, JAVA_FILES, FP_UN,
					FN_UN, FP_SS, FN_SS, M, N);

			//print summary
			File fsummary = new File(logpath+ "jfstmerge.summary.files");
			if(!fsummary.exists()){
				fsummary.createNewFile();
			}

			fsummary.write(summary.toString())
		}
	}

	def private static StringBuilder fillSummaryMsg(int ssmergeconfs,
			int ssmergeloc, int unmergeconfs, int unmergeloc, int equalconfs,
			int JAVA_FILES, int FP_UN, int FN_UN, int FP_SS, int FN_SS,
			double M, double N) {
		StringBuilder summary = new StringBuilder();
		summary.append("s3m was invoked in " +JAVA_FILES+ " JAVA files so far.\n");

		if(FP_UN > 0 && FN_UN > 0){
			summary.append("In these files, you avoided at least " +FP_UN+ " false positive(s),");
			summary.append(" and at least "+FN_UN+" false negative(s) in relation to unstructured merge.\n");
		} else if(FP_UN == 0 && FN_UN == 0){
			summary.append("In these files, s3m did not find any occurrence of unstructured merge false positives and false negatives.\n");
		} else if(FP_UN > 0 && FN_UN == 0){
			summary.append("In these files, you avoided at least " +FP_UN+" false positive(s), and s3m did not find any occurrence of unstructured merge false negatives.\n");
		} else if(FP_UN == 0 && FN_UN > 0){
			summary.append("In these files, s3m did not find any occurrence of unstructured merge false positives, but you avoided at least "+FN_UN+" false negative(s) in relation to unstructured merge.\n");
		}

		summary.append("Conversely,");
		if(FP_SS > 0 && FN_SS > 0){
			summary.append(" you had at most " +FP_SS+ " extra false positive(s),");
			summary.append(" and at most " +FN_SS+ " potential extra false negative(s).");
		} else if(FP_SS == 0 && FN_SS == 0) {
			summary.append(" you had no extra false positives, nor potential extra false negatives.");
		} else if(FP_SS > 0 && FN_SS == 0) {
			summary.append(" you had at most " +FP_SS+ " extra false positive(s), but no potential extra false negatives.");
		} else if(FP_SS == 0 && FN_SS > 0) {
			summary.append(" you had no extra false positives, but you had at most "+FN_SS+" potential extra false negative(s).");
		}

		summary.append("\n\ns3m reported "+ssmergeconfs+" conflicts, totaling " +ssmergeloc+ " conflicting LOC,");
		summary.append(" compared to "+unmergeconfs+" conflicts and " +unmergeloc+ " conflicting LOC from unstructured merge.");
		/*		if(equalconfs >0){
		 summary.append("\nWith " +equalconfs+ " similar conflict(s) between the tools.");
		 }*/

		summary.append("\n\nAltogether, ");
		if(ssmergeconfs != unmergeconfs){
			if(ssmergeconfs < unmergeconfs){
				summary.append("these numbers represent a reduction of " + String.format("%.2f",((double)((unmergeconfs - ssmergeconfs)/(double)unmergeconfs))*100) +"% in the number of conflicts by s3m.\n");
			} else if(ssmergeconfs > unmergeconfs){
				summary.append("these numbers represent no reduction of conflicts by s3m.\n");
			}
		} else {
			summary.append("these numbers represent no difference in terms of number of reported conflicts.\n");
		}

		if(FP_UN != FP_SS){
			if(FP_UN > FP_SS) {
				summary.append("A reduction of " + String.format("%.2f",((double)((FP_UN - FP_SS)/(double)FP_UN))*100,2) +"% in the number of false positives.\n");
			} else if(FP_SS > FP_UN){
				summary.append("No reduction of false positives.\n");
			}
		} else {
			summary.append("No difference in terms of false positives.\n");
		}

		if(FN_UN != FN_SS){
			if(FN_UN > FN_SS) {
				summary.append("And a reduction of " + String.format("%.2f",((double)((FN_UN - FN_SS)/(double)FN_UN))*100,2) +"% in the number of false negatives.");
			} else if(FN_SS > FN_UN){
				summary.append("And no reduction of false negatives.");
			}
		}  else {
			summary.append("And no difference in terms of false negatives.");
		}


		summary.append("\n\nFinally, s3m took " + (new DecimalFormat("#.##").format(M))+" seconds, and unstructured merge " + (new DecimalFormat("#.##").format(N)) + " seconds to merge all these files.");

		summary.append("\n\n\n");
		summary.append("LAST TIME UPDATED: " + (new SimpleDateFormat("yyyy/MM/dd_HH:mm:ss").format(Calendar.getInstance().getTime())));
		return summary;
	}

	public static void main (String[] args){
		run_gitmerges()
	}
}