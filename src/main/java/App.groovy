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
import util.TravisFinder;

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
			MergeResult result = computeStatistics(m)

			if(result.sucessfullmerge){
				//if((result.ssmergeConf == 0 && result.jdimeConf>0){//travis only necessary when tools differ (result.ssmergeConf > 0 && result.jdimeConf==0)
					//push to fork, build fork
					String new_sha = e.git_push()
					result.travisStatus = TravisFinder.findStatus(new_sha, result.commit.projectName)
					//TODO testar
				//}
			}

			//log statistics related to each scenario
			logStatistics(result)
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

	def private static MergeResult computeStatistics(MergeCommit m){
		//wrapper
		MergeResult result = new MergeResult()
		result.commit = m;

		//retrieving statistics
		String logpath  = System.getProperty("user.home")+ File.separator + ".jfstmerge" + File.separator;
		File statistics_partial = new File(logpath+ "numbers-current-file.csv");
		File statistics_files 	= new File(logpath+ "numbers-files.csv");
		if(!statistics_files.exists()){(
			new File(logpath)).mkdirs();statistics_files.createNewFile();
			statistics_files.append('project;mergecommit;files;consecutiveLinesonly;spacingonly;samePositiononly;sameStmtonly;otheronly;consecutiveLinesAndSamePosition;consecutiveLinesAndsameStmt;otherAndsamePosition;otherAndsameStmt;spacingAndSamePosition;spacingAndSameStmt;ssmergeConf;textualConf;jdimeConfs;smergeTime;textualTime;jdimeTime;sucessfullMerge;isSsEqualsToUn;isStEqualsToUn\n')
		} //ensuring it exists
		/*
		 * numbers-current-file contains numbers for each merged file. To compute numbers for each scenario,
		 * we read the numbers-current-file, and then we delete this file. Thus, every time a merge scenario is merged
		 * the numbers-current-file is empty. To have overall number for all files, regardless merge scenarios, we append
		 * numbers-current-file content in the numbers-files before deleting numbers-current-file.
		 */

		List<String> lines = new ArrayList<String>();
		if(statistics_partial.exists()){lines = statistics_partial.readLines();}
		for(int y = 1/*ignoring header*/; y <lines.size(); y++){
			String[] columns = lines.get(y).split(";");

			statistics_files.append(m.projectURL +';'+m.sha+';'+lines.get(y)+'\n')

			result.consecutiveLinesonly += Integer.valueOf(columns[1]);
			result.spacingonly 	 += Integer.valueOf(columns[2]);
			result.samePositiononly += Integer.valueOf(columns[3]);
			result.sameStmtonly 	 += Integer.valueOf(columns[4]);
			result.otheronly 		 += Integer.valueOf(columns[5]);
			result.consecutiveLinesAndSamePosition += Integer.valueOf(columns[6]);
			result.consecutiveLinesAndsameStmt 	+= Integer.valueOf(columns[7]);
			result.otherAndsamePosition 	+= Integer.valueOf(columns[8]);
			result.otherAndsameStmt 		+= Integer.valueOf(columns[9]);
			result.spacingAndSamePosition 	+= Integer.valueOf(columns[10]);
			result.spacingAndSameStmt += Integer.valueOf(columns[11]);
			result.ssmergeConf += Integer.valueOf(columns[12]);
			result.jdimeConf   += Integer.valueOf(columns[13]);
			result.textualConf += Integer.valueOf(columns[14]);
			result.ssmergetime += Long.parseLong(columns[15]);
			result.unmergetime += Long.parseLong(columns[16]);
			result.jdimetime   += Long.parseLong(columns[17]);
			result.sucessfullmerge= result.sucessfullmerge&& Boolean.parseBoolean(columns[18]);
			result.isSsEqualsToUn = result.isSsEqualsToUn && Boolean.parseBoolean(columns[19]);
			result.isStEqualsToUn = result.isStEqualsToUn && Boolean.parseBoolean(columns[20]);
		}
		(new AntBuilder()).delete(file:statistics_partial.getAbsolutePath(),failonerror:false)
		return result
	}

	private static logStatistics(MergeResult result) {
		String logpath = System.getProperty("user.home")+ File.separator + ".jfstmerge" + File.separator;
		File statistics_scenarios = new File(logpath+ "numbers-scenarios.csv");
		if(!statistics_scenarios.exists()){
			def header = "project;mergecommit;consecutiveLinesonly;spacingonly;samePositiononly;sameStmtonly;otheronly;consecutiveLinesAndSamePosition;consecutiveLinesAndsameStmt;otherAndsamePosition;otherAndsameStmt;spacingAndSamePosition;spacingAndSameStmt;ssmergeConf;textualConf;jdimeConfs;smergeTime;textualTime;jdimeTime;sucessfullMerge;isSsEqualsToUn;isStEqualsToUn;travisStatus"
			statistics_scenarios.append(header+'\n')
			statistics_scenarios.createNewFile()
		} //ensuring it exists

		def loggermsg = result.commit.projectName + ';' + result.commit.sha + ';'+ result.consecutiveLinesonly + ';'+ result.spacingonly + ';'+ result.samePositiononly + ';'+ result.sameStmtonly + ';'+ result.otheronly + ';'+ result.consecutiveLinesAndSamePosition + ';'	+ result.consecutiveLinesAndsameStmt + ';'+ result.otherAndsamePosition + ';'+ result.otherAndsameStmt + ';'+ result.spacingAndSamePosition + ';'+ result.spacingAndSameStmt + ';'+ result.ssmergeConf + ';'+ result.textualConf + ';'+ result.jdimeConf + ';'+ result.ssmergetime + ';'+ result.unmergetime + ';'+ result.jdimetime + ';'+ result.sucessfullmerge+ ';'+ result.isSsEqualsToUn + ';'+ result.isStEqualsToUn + ';'+ result.travisStatus;
		statistics_scenarios.append(loggermsg+'\n')
	}

	public static void main (String[] args){
		run_gitmerges()
	}
}