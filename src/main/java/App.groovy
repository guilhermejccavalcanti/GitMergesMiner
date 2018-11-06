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
import util.Tuple;



import java.text.DecimalFormat;
import java.text.SimpleDateFormat;


class App {

	static boolean restore_repositories = true;
	static boolean is_building_jdime  = false
	static boolean run_travis = true;
	static boolean filter_known_builds = true;

	public static void main (String[] args){
		//execution configuration parameters
		def cli = new CliBuilder()
		cli.with {
			r longOpt: 'restore', 'do not restore git repositories'
			s longOpt: 'jdime', 'attempt to build jdime code'
			t longOpt: 'travis', 'do not execute Travis on merged scenario'
			b longOpt: 'builds', 'do not filter scenarios by known builds (builds.csv)'
		}
		def options = cli.parse(args)
		if (options.r) {
			restore_repositories = false
		}
		if (options.s) {
			is_building_jdime = true
		}
		if(options.t){
			run_travis = false
		}
		if(options.b){
			filter_known_builds = false
		}
		boolean alreadyExecuted1stRun = new File("conflictingS3M").exists()
		boolean changeGitConfig = !alreadyExecuted1stRun
		if(alreadyExecuted1stRun)is_building_jdime = true;

		//managing execution log
		File f = new File(System.getProperty("user.home") + File.separator + ".jfstmerge" + File.separator + 'execution.log')
		if(!f.exists()){
			f.getParentFile().mkdirs()
			f.createNewFile()
		}

		//configuring git merge driver
		File gitconfig = new File(System.getProperty("user.home") + File.separator + ".gitconfig")
		if(changeGitConfig){
			if(!gitconfig.exists()) {
				println 'ERROR: .gitconfig not found on ' + gitconfig.getParent() + '. S3M tool not installed?'
				System.exit(-1)
			}
			String gitconfigContents =  gitconfig.text
			if(is_building_jdime){
				gitconfig.text = gitconfigContents.replaceAll("-g", "-g -s")
			} else {
				gitconfig.text = gitconfigContents.replaceAll("-s", "")
			}
		}

		//1st run (attempts to build non-conflicting code of the first tool)
		run_gitmerges()

		//storing 1st results
		def resultsFolder = "conflictingS3M";
		if(is_building_jdime){
			resultsFolder = "conflictingJDIME"
		}
		boolean success = f.getParentFile().renameTo(new File(resultsFolder))
		boolean alreadyExecuted2ndRun = new File("conflictingJDIME").exists()

/*		if(success && !alreadyExecuted2ndRun){
			//configuration for 2nd run
			is_building_jdime = !is_building_jdime

			//re-managing execution log
			if(f.getParentFile().exists()) f.getParentFile().deleteDir()
			f.getParentFile().mkdirs()
			f.createNewFile()

			//re-configuring git merge driver
			if(!gitconfig.exists()) {
				println 'ERROR: .gitconfig not found on ' + gitconfig.getParent() + '. S3M tool not installed?'
				System.exit(-1)
			}
			def gitconfigContents =  gitconfig.text
			if(is_building_jdime){
				gitconfig.text = gitconfigContents.replaceAll("-g", "-g -s")
			} else {
				gitconfig.text = gitconfigContents.replaceAll("-s", "")
			}

			//2nd run (attempts to build non-conflicting code of the second tool)
			run_gitmerges()

			//storing 2nd results
			resultsFolder = "conflictingS3M";
			if(is_building_jdime){
				resultsFolder = "conflictingJDIME"
			}
			success = f.getParentFile().renameTo(new File(resultsFolder))
			if(!success){
				println 'ERROR: unable to store 2nd run results'
				System.exit(-1)
			}
		} else {
			if(!success){
				println 'ERROR: unable to store 1st run results'
				System.exit(-1)
			}
		}*/
	}

	def public static run_gitmerges(){
		Read r = new Read("projects.csv",false)
		def projects = r.getProjects()
		if(restore_repositories){
			restoreGitRepositories(projects)
		}

		//fill merge scenarios info (base,left,right)
		projects.each {
			Extractor e = new Extractor(it,false)
			e.fillAncestors()
			println('Project ' + it.name + " read")
		}

		//reproduce the 'git merge' command for each merge scenario
		LinkedList<MergeCommit> merge_commits = fillMergeCommitsListForHorizontalExecution(projects)
		if(filter_known_builds){
			filterByKnownBuilds(merge_commits)
		}
		for(int i=0; i<merge_commits.size();i++){
			MergeCommit m = merge_commits.get(i);
			println ('Analysing ' + ((i+1)+'/'+merge_commits.size()) + ': ' +  m.sha)
			fillExecutionLog(m)
			Extractor e = new Extractor(m)
			e.git_merge(m)

			//compute statistics related to each scenario
			MergeResult result = computeStatistics(m)

			if(result.sucessfullmerge && e.isTravis && run_travis){
				//travis only necessary when tools differ
				boolean shouldPush = false;
				if(is_building_jdime){
					if(result.ssmergeConf > 0 && result.jdimeConf == 0) {
						shouldPush = true;
					}
				} else {
					if(result.ssmergeConf == 0 && result.jdimeConf>0) {
						shouldPush = true;
					}
				}
				if(shouldPush){
					//push to fork, build fork
					def new_sha = e.git_push()
					if(new_sha != null){
						Tuple<String,String,Integer> travisResult = TravisFinder.findStatus(new_sha, result.commit.projectName)
						result.travisStatus = travisResult.x
						result.travisBuildURI = travisResult.y
						result.travisBuildTime = travisResult.z
						result.travisJobURI = travisResult.w
					}
				}
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
		/*
		 * adds a random factor on the executions's order
		 */
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
		def out = new File(System.getProperty("user.home") + File.separator + ".jfstmerge" + File.separator + 'execution.log')
		out.append (lastMergeCommit.projectName+','+lastMergeCommit.sha)
		out.append '\n'
	}

	def private static ArrayList<String> restoreExecutionLog(){
		ArrayList<String> alreadyExecutedSHAs = new ArrayList<String>()
		try {
			BufferedReader br = new BufferedReader(new FileReader(System.getProperty("user.home") + File.separator + ".jfstmerge" + File.separator + 'execution.log'))
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

			statistics_files.append(m.project.originalURL +';'+m.sha+';'+lines.get(y)+'\n')

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
			result.textualConf += Integer.valueOf(columns[13]);
			result.jdimeConf   += Integer.valueOf(columns[14]);
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
			def header = "project;mergecommit;consecutiveLinesonly;spacingonly;samePositiononly;sameStmtonly;otheronly;consecutiveLinesAndSamePosition;consecutiveLinesAndsameStmt;otherAndsamePosition;otherAndsameStmt;spacingAndSamePosition;spacingAndSameStmt;ssmergeConf;textualConf;jdimeConfs;smergeTime;textualTime;jdimeTime;sucessfullMerge;isSsEqualsToUn;isStEqualsToUn;travisStatus;travisBuildURI;travisJobURI;travisBuildTime"
			statistics_scenarios.append(header+'\n')
			statistics_scenarios.createNewFile()
		} //ensuring it exists

		def loggermsg = result.commit.projectName + ';' + result.commit.sha + ';'+ result.consecutiveLinesonly + ';'+ result.spacingonly + ';'+ result.samePositiononly + ';'+ result.sameStmtonly + ';'+ result.otheronly + ';'+ result.consecutiveLinesAndSamePosition + ';'	+ result.consecutiveLinesAndsameStmt + ';'+ result.otherAndsamePosition + ';'+ result.otherAndsameStmt + ';'+ result.spacingAndSamePosition + ';'+ result.spacingAndSameStmt + ';'+ result.ssmergeConf + ';'+ result.textualConf + ';'+ result.jdimeConf + ';'+ result.ssmergetime + ';'+ result.unmergetime + ';'+ result.jdimetime + ';'+ result.sucessfullmerge+ ';'+ result.isSsEqualsToUn + ';'+ result.isStEqualsToUn + ';'+ result.travisStatus + ';'+ result.travisBuildURI + ';'+ result.travisJobURI + ';'+ result.travisBuildTime;
		statistics_scenarios.append(loggermsg+'\n')
	}

	def private static filterByKnownBuilds(List<MergeCommit> mergeCommits){
		println 'Filtering known builds...'
		List<MergeCommit> filtered = new ArrayList<>()
		File f = new File('builds.csv')
		if(!f.exists() ) {
			println "Builds file does not exist!"
		} else {
			//1. group builds by project
			HashMap<String,List<String>> buildsByProject = new HashMap();
			f.eachLine {line ->
				try{
					String[] a = line.split(';')
					String pname = a[0]
					List<String> builds = buildsByProject.get(pname)
					if(builds == null) {
						builds = new ArrayList<>()
						builds.add(line)
						buildsByProject.put(pname, builds)
					}else {
						builds.add(line)
					}
				}catch(Exception e){}
			}
			//2. search builds
			for(MergeCommit m : mergeCommits){
				List<String> builds = buildsByProject.get(m.project.originalName)
				if(builds != null){
					builds.each {entry ->
						if(entry.contains(m.sha)){
							filtered.add(m)
						}
					}
				}
			}
		}
		mergeCommits.clear()
		mergeCommits.addAll(filtered)
	}
}